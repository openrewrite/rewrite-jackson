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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceObjectMapperCopy extends Recipe {

    private static final MethodMatcher COPY_MATCHER =
            new MethodMatcher("com.fasterxml.jackson.databind.ObjectMapper copy()", true);

    String displayName = "Replace `ObjectMapper.copy()` with `rebuild().build()`";

    String description = "In Jackson 3, `ObjectMapper.copy()` was removed. " +
               "Use `mapper.rebuild().build()` instead.";

    Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(COPY_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!COPY_MATCHER.matches(mi)) {
                            return mi;
                        }
                        return JavaTemplate
                                .builder("#{any(tools.jackson.databind.ObjectMapper)}.rebuild().build()")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        // the annotation still live in jackson-annotation version 2 for Jackson 3
                                        "jackson-annotations-2", "jackson-core-3", "jackson-databind-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                    }
                }
        );
    }
}
