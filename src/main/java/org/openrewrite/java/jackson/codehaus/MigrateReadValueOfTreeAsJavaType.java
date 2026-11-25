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
package org.openrewrite.java.jackson.codehaus;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

/**
 * Jackson 1's {@code ObjectMapper.readValue(JsonNode, JavaType)} has no counterpart of that shape in Jackson 2 before
 * 2.13, which added {@code treeToValue(TreeNode, JavaType)}. Reading the tree through an {@code ObjectReader} for the
 * type works on every Jackson 2 version from 2.6, and throws the same {@code IOException}.
 */
public class MigrateReadValueOfTreeAsJavaType extends Recipe {

    private static final MethodMatcher READ_VALUE = new MethodMatcher(
            "org.codehaus.jackson.map.ObjectMapper readValue(org.codehaus.jackson.JsonNode, org.codehaus.jackson.type.JavaType)", true);

    @Getter
    final String displayName = "Read a Jackson Codehaus (legacy) tree as a `JavaType` through an `ObjectReader`";

    @Getter
    final String description = "Rewrites `mapper.readValue(node, javaType)` to `mapper.reader(javaType).readValue(node)`, " +
                               "which becomes `readerFor(javaType)` in Jackson 2, rather than `treeToValue(node, javaType)`, " +
                               "which Jackson 2 only has from 2.13.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(READ_VALUE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!READ_VALUE.matches(m)) {
                    return m;
                }
                JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "jackson-core-asl", "jackson-mapper-asl");
                if (m.getSelect() == null) {
                    return JavaTemplate.builder("reader(#{any(org.codehaus.jackson.type.JavaType)}).readValue(#{any(org.codehaus.jackson.JsonNode)})")
                            .contextSensitive()
                            .javaParser(parser)
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(1), m.getArguments().get(0));
                }
                return JavaTemplate.builder("#{any(org.codehaus.jackson.map.ObjectMapper)}.reader(#{any(org.codehaus.jackson.type.JavaType)}).readValue(#{any(org.codehaus.jackson.JsonNode)})")
                        .javaParser(parser)
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(1), m.getArguments().get(0));
            }
        });
    }
}
