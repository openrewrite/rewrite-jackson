/*
 * Copyright 2025 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class SimplifyJacksonExceptionCatch extends Recipe {

    private static final String RUNTIME_EXCEPTION = "java.lang.RuntimeException";
    private static final String JACKSON_RUNTIME_EXCEPTION = "tools.jackson.core.JacksonException";

    @Override
    public String getDisplayName() {
        return "Simplify catch clauses for Jackson exceptions";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `JacksonException` and its subtypes extend `RuntimeException`. " +
                "This recipe simplifies multi-catch clauses by removing Jackson exception types " +
                "when `RuntimeException` is also caught, since catching both is redundant. " +
                "For example, `catch (JacksonException | RuntimeException e)` becomes `catch (RuntimeException e)`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("tools.jackson.core.JacksonException", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MultiCatch visitMultiCatch(J.MultiCatch multiCatch, ExecutionContext ctx) {
                        J.MultiCatch mc = super.visitMultiCatch(multiCatch, ctx);

                        // Check if RuntimeException is in the multi-catch
                        if (mc.getAlternatives().stream().noneMatch(nt -> TypeUtils.isOfClassType(nt.getType(), RUNTIME_EXCEPTION))) {
                            return mc;
                        }

                        List<NameTree> filtered = ListUtils.filter(mc.getAlternatives(), nt -> {
                            boolean isJacksonException = TypeUtils.isAssignableTo(JACKSON_RUNTIME_EXCEPTION, nt.getType());
                            if (isJacksonException) {
                                maybeRemoveImport(TypeUtils.asFullyQualified(nt.getType()));
                            }
                            return !isJacksonException;
                        });
                        return mc.withAlternatives(ListUtils.mapFirst(filtered, first -> first.withPrefix(mc.getAlternatives().get(0).getPrefix())));
                    }
                }
        );
    }
}
