/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.jackson;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class ReorderStreamWriteExceptionArguments extends Recipe {

    private static final String JSON_GENERATION_EXCEPTION = "com.fasterxml.jackson.core.JsonGenerationException";
    private static final String JSON_GENERATOR = "com.fasterxml.jackson.core.JsonGenerator";

    private static final MethodMatcher TWO_ARG_STRING = new MethodMatcher(
            JSON_GENERATION_EXCEPTION + " <constructor>(String, " + JSON_GENERATOR + ")");
    private static final MethodMatcher TWO_ARG_THROWABLE = new MethodMatcher(
            JSON_GENERATION_EXCEPTION + " <constructor>(Throwable, " + JSON_GENERATOR + ")");
    private static final MethodMatcher THREE_ARG = new MethodMatcher(
            JSON_GENERATION_EXCEPTION + " <constructor>(String, Throwable, " + JSON_GENERATOR + ")");

    final String displayName = "Reorder `StreamWriteException` constructor arguments";

    final String description = "In Jackson 3, `StreamWriteException` (formerly `JsonGenerationException`) constructors " +
            "take `JsonGenerator` as the first parameter instead of the last. " +
            "This recipe reorders constructor arguments to match the new signature.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JSON_GENERATION_EXCEPTION, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        List<Expression> args = nc.getArguments();

                        if ((TWO_ARG_STRING.matches(nc) || TWO_ARG_THROWABLE.matches(nc)) && args.size() == 2) {
                            if (TypeUtils.isAssignableTo(JSON_GENERATOR, args.get(0).getType())) {
                                return nc;
                            }
                            // [msg_or_cause, gen] -> [gen, msg_or_cause]
                            return nc.withArguments(swap(args.get(1), args.get(0)));
                        }
                        if (THREE_ARG.matches(nc) && args.size() == 3) {
                            if (TypeUtils.isAssignableTo(JSON_GENERATOR, args.get(0).getType())) {
                                return nc;
                            }
                            // [msg, cause, gen] -> [gen, msg, cause]
                            return nc.withArguments(moveLastToFirst(args));
                        }
                        return nc;
                    }
                }
        );
    }

    private static List<Expression> swap(Expression newFirst, Expression newSecond) {
        return Arrays.asList(
                newFirst.withPrefix(newSecond.getPrefix()),
                newSecond.withPrefix(newFirst.getPrefix())
        );
    }

    private static List<Expression> moveLastToFirst(List<Expression> args) {
        Space firstPrefix = args.get(0).getPrefix();
        Expression last = args.get(args.size() - 1);
        return Arrays.asList(
                last.withPrefix(firstPrefix),
                args.get(0).withPrefix(last.getPrefix()),
                args.get(1).withPrefix(args.get(1).getPrefix())
        );
    }
}
