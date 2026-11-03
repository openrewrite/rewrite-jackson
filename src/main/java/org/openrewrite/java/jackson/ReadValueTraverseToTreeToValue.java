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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;

/**
 * Migrates {@code mapper.readValue(node.traverse(), type)} to {@code mapper.treeToValue(node, type)}.
 * In Jackson 3.x {@code TreeNode.traverse()} requires an {@code ObjectReadContext}, so the no-argument
 * call no longer compiles. {@code treeToValue} reads the tree directly and exists in both versions.
 */
public class ReadValueTraverseToTreeToValue extends Recipe {
    private static final MethodMatcher READ_VALUE_PARSER_CLASS = new MethodMatcher(
            "com.fasterxml.jackson.databind.ObjectMapper readValue(com.fasterxml.jackson.core.JsonParser, java.lang.Class)", true);
    private static final MethodMatcher READ_VALUE_PARSER_JAVA_TYPE = new MethodMatcher(
            "com.fasterxml.jackson.databind.ObjectMapper readValue(com.fasterxml.jackson.core.JsonParser, com.fasterxml.jackson.databind.JavaType)", true);
    private static final MethodMatcher TRAVERSE = new MethodMatcher(
            "com.fasterxml.jackson.core.TreeNode traverse()", true);

    @Getter
    final String displayName = "Migrate `ObjectMapper.readValue(node.traverse(), ...)` to `treeToValue(node, ...)`";

    @Getter
    final String description = "In Jackson 3.x, `TreeNode.traverse()` requires an `ObjectReadContext`, so " +
            "`mapper.readValue(node.traverse(), type)` no longer compiles. Rewrite it to " +
            "`mapper.treeToValue(node, type)`, which reads the tree directly and exists in Jackson 2.x and 3.x alike.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(TRAVERSE),
                        Preconditions.or(new UsesMethod<>(READ_VALUE_PARSER_CLASS), new UsesMethod<>(READ_VALUE_PARSER_JAVA_TYPE))),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (mi.getSelect() == null ||
                                !READ_VALUE_PARSER_CLASS.matches(mi) && !READ_VALUE_PARSER_JAVA_TYPE.matches(mi)) {
                            return mi;
                        }
                        Expression parser = mi.getArguments().get(0);
                        if (!(parser instanceof J.MethodInvocation) || !TRAVERSE.matches(parser) ||
                                ((J.MethodInvocation) parser).getSelect() == null) {
                            return mi;
                        }
                        Expression node = ((J.MethodInvocation) parser).getSelect();
                        JavaType.Method readValue = mi.getMethodType();
                        JavaType.Method treeToValue = readValue == null ? null : readValue
                                .withName("treeToValue")
                                .withParameterNames(Arrays.asList("n", "valueType"))
                                .withParameterTypes(ListUtils.mapFirst(readValue.getParameterTypes(),
                                        t -> JavaType.ShallowClass.build("com.fasterxml.jackson.core.TreeNode")))
                                .withThrownExceptions(Arrays.asList(
                                        JavaType.ShallowClass.build("java.lang.IllegalArgumentException"),
                                        JavaType.ShallowClass.build("com.fasterxml.jackson.core.JsonProcessingException")));
                        return mi
                                .withName(mi.getName().withSimpleName("treeToValue").withType(treeToValue))
                                .withMethodType(treeToValue)
                                .withArguments(ListUtils.mapFirst(mi.getArguments(), a -> node.withPrefix(parser.getPrefix())));
                    }
                });
    }
}
