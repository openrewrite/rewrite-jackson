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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;

public class UpdateAutoDetectVisibilityConfiguration extends Recipe {

    private static final MethodMatcher MAPPER_BUILDER_DISABLE_MATCHER =
            new MethodMatcher("com.fasterxml.jackson.databind..MapperBuilder disable(..)", true);

    private static final Map<String, String> AUTO_DETECT_TO_VISIBILITY;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("AUTO_DETECT_FIELDS", "withFieldVisibility");
        map.put("AUTO_DETECT_GETTERS", "withGetterVisibility");
        map.put("AUTO_DETECT_IS_GETTERS", "withIsGetterVisibility");
        map.put("AUTO_DETECT_SETTERS", "withSetterVisibility");
        map.put("AUTO_DETECT_CREATORS", "withCreatorVisibility");
        AUTO_DETECT_TO_VISIBILITY = map;
    }

    @Getter
    final String displayName = "Replace `disable(MapperFeature.AUTO_DETECT_*)` with `changeDefaultVisibility()` for Jackson 3";

    @Getter
    final String description = "In Jackson 3, auto-detection `MapperFeature` flags like `AUTO_DETECT_FIELDS` are removed. " +
            "Use `changeDefaultVisibility()` on the builder instead.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(MAPPER_BUILDER_DISABLE_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!MAPPER_BUILDER_DISABLE_MATCHER.matches(mi)) {
                            return mi;
                        }
                        Expression arg = mi.getArguments().get(0);
                        if (arg instanceof J.Empty) {
                            return mi;
                        }
                        String visibilityMethod = getVisibilityMethod(arg);
                        if (visibilityMethod == null) {
                            return mi;
                        }
                        J.MethodInvocation result = JavaTemplate
                                .builder("#{any(tools.jackson.databind.json.JsonMapper$Builder)}.changeDefaultVisibility(vc -> vc." +
                                        visibilityMethod + "(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE))")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "jackson-annotations-2", "jackson-core-3", "jackson-databind-3"))
                                .build()
                                .apply(
                                        getCursor(),
                                        mi.getCoordinates().replace(),
                                        mi.getSelect());
                        result = result.getPadding().withSelect(JRightPadded.build(result.getSelect()).withAfter(mi.getPadding().getSelect().getAfter()));
                        maybeRemoveImport("com.fasterxml.jackson.databind.MapperFeature");
                        maybeAddImport("com.fasterxml.jackson.annotation.JsonAutoDetect");
                        return fixKotlinLambdaParameterTypeAndBodySpacing(result);
                    }

                    private @Nullable String getVisibilityMethod(Expression arg) {
                        String fieldName = null;
                        JavaType ownerType = null;
                        if (arg instanceof J.FieldAccess) {
                            J.FieldAccess fa = (J.FieldAccess) arg;
                            fieldName = fa.getName().getSimpleName();
                            ownerType = fa.getTarget().getType();
                        } else if (arg instanceof J.Identifier) {
                            J.Identifier id = (J.Identifier) arg;
                            fieldName = id.getSimpleName();
                            if (id.getFieldType() != null) {
                                ownerType = id.getFieldType().getOwner();
                            }
                        }
                        if (fieldName != null && TypeUtils.isOfClassType(ownerType, "com.fasterxml.jackson.databind.MapperFeature")) {
                            return AUTO_DETECT_TO_VISIBILITY.get(fieldName);
                        }
                        return null;
                    }

                    private J.MethodInvocation fixKotlinLambdaParameterTypeAndBodySpacing(J.MethodInvocation mi) {
                        JavaType autoDetectValueType = JavaType.ShallowClass.build("com.fasterxml.jackson.annotation.JsonAutoDetect$Value");
                        return (J.MethodInvocation) new JavaIsoVisitor<Integer>() {
                            @Override
                            public J.Lambda visitLambda(J.Lambda lambda, Integer p) {
                                J.Lambda l = super.visitLambda(lambda, p);
                                if (l.getBody().getPrefix().isEmpty()) {
                                    return l.withBody(l.getBody().withPrefix(Space.SINGLE_SPACE));
                                }
                                return l;
                            }

                            @Override
                            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                                J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, p);
                                if ("vc".equals(nv.getSimpleName())) {
                                    return nv
                                            .withName(nv.getName().withType(autoDetectValueType))
                                            .withVariableType(new JavaType.Variable(null, 0, "vc", autoDetectValueType, autoDetectValueType, null));
                                }
                                return nv;
                            }
                        }.visitNonNull(mi, 0);
                    }
                });
    }
}
