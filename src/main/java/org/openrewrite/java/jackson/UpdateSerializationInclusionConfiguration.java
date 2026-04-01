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
import org.openrewrite.java.tree.*;

import java.util.Set;

import static java.util.Collections.singleton;

public class UpdateSerializationInclusionConfiguration extends Recipe {

    private static final MethodMatcher MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER = new MethodMatcher("com.fasterxml.jackson.databind..MapperBuilder serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include)", true);
    private static final MethodMatcher MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_INCLUDE_MATCHER = new MethodMatcher("com.fasterxml.jackson.databind..MapperBuilder defaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include)", true);
    private static final MethodMatcher MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_VALUE_MATCHER = new MethodMatcher("com.fasterxml.jackson.databind..MapperBuilder defaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Value)", true);
    private static final MethodMatcher OBJECT_MAPPER_SET_SERIALIZATION_INCLUSION_MATCHER = new MethodMatcher("com.fasterxml.jackson.databind.ObjectMapper setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include)");

    @Getter
    final String displayName = "Update configuration of serialization inclusion in `ObjectMapper` for Jackson 3";

    @Getter
    final String description = "In Jackson 3, `mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)` is no longer supported " +
            "and should be replaced by `changeDefaultPropertyInclusion()` for both `valueInclusion` and `contentInclusion`.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER),
                        new UsesMethod<>(MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_INCLUDE_MATCHER),
                        new UsesMethod<>(MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_VALUE_MATCHER),
                        new UsesMethod<>(OBJECT_MAPPER_SET_SERIALIZATION_INCLUSION_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER.matches(mi) ||
                                MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_INCLUDE_MATCHER.matches(mi)) {
                            J.MethodInvocation result = JavaTemplate
                                    .builder("#{any(tools.jackson.databind.json.JsonMapper$Builder)}.changeDefaultPropertyInclusion(incl -> incl" +
                                            ".withContentInclusion(#{any(com.fasterxml.jackson.annotation.JsonInclude.Include)})" +
                                            ".withValueInclusion(#{any(com.fasterxml.jackson.annotation.JsonInclude.Include)}))")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                            "jackson-annotations-2", "jackson-core-3", "jackson-databind-3"))
                                    .build()
                                    .apply(
                                            getCursor(),
                                            mi.getCoordinates().replace(),
                                            mi.getSelect(),
                                            mi.getArguments().get(0),
                                            mi.getArguments().get(0));
                            result = result.getPadding().withSelect(JRightPadded.build(result.getSelect()).withAfter(mi.getPadding().getSelect().getAfter()));
                            return fixKotlinLambdaParameterTypeAndBodySpacing(result);
                        }
                        if (MAPPER_BUILDER_DEFAULT_PROPERTY_INCLUSION_VALUE_MATCHER.matches(mi)) {
                            J.MethodInvocation result = JavaTemplate
                                    .builder("#{any(tools.jackson.databind.json.JsonMapper$Builder)}.changeDefaultPropertyInclusion(incl -> #{any(com.fasterxml.jackson.annotation.JsonInclude.Value)})")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                            "jackson-annotations-2", "jackson-core-3", "jackson-databind-3"))
                                    .build()
                                    .apply(
                                            getCursor(),
                                            mi.getCoordinates().replace(),
                                            mi.getSelect(),
                                            mi.getArguments().get(0));
                            result = result.getPadding().withSelect(JRightPadded.build(result.getSelect()).withAfter(mi.getPadding().getSelect().getAfter()));
                            return fixKotlinLambdaParameterTypeAndBodySpacing(result);
                        }
                        if (OBJECT_MAPPER_SET_SERIALIZATION_INCLUSION_MATCHER.matches(mi)) {
                            // Simple rename from setSerializationInclusion to setDefaultPropertyInclusion;
                            // avoids JavaTemplate to preserve Kotlin AST structure
                            mi = mi.withName(mi.getName().withSimpleName("setDefaultPropertyInclusion"));
                            if (mi.getMethodType() != null) {
                                mi = mi.withMethodType(mi.getMethodType().withName("setDefaultPropertyInclusion"));
                            }
                            return mi;
                        }
                        return mi;
                    }

                    private J.MethodInvocation fixKotlinLambdaParameterTypeAndBodySpacing(J.MethodInvocation mi) {
                        JavaType includeValueType = JavaType.ShallowClass.build("com.fasterxml.jackson.annotation.JsonInclude$Value");
                        return (J.MethodInvocation) new JavaIsoVisitor<Integer>() {
                            /**
                             * The JavaTemplate-generated lambda body loses its leading space
                             * when rendered to Kotlin. Walk the result to ensure the lambda
                             * body has a single space prefix.
                             */
                            @Override
                            public J.Lambda visitLambda(J.Lambda lambda, Integer p) {
                                J.Lambda l = super.visitLambda(lambda, p);
                                if (l.getBody().getPrefix().isEmpty()) {
                                    return l.withBody(l.getBody().withPrefix(Space.SINGLE_SPACE));
                                }
                                return l;
                            }

                            /**
                             * The JavaTemplate-generated lambda has an untyped parameter which fails
                             * Kotlin AST type validation. Walk the result to add the type to the
                             * lambda parameter variable.
                             */
                            @Override
                            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                                J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, p);
                                if ("incl".equals(nv.getSimpleName())) {
                                    return nv
                                            .withName(nv.getName().withType(includeValueType))
                                            .withVariableType(new JavaType.Variable(null, 0, "incl", includeValueType, includeValueType, null));
                                }
                                return nv;
                            }
                        }.visitNonNull(mi, 0);
                    }
                });
    }
}
