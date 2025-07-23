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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class RemoveRedundantJsonPropertyValue extends Recipe {

    private static final String JACKSON_JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final AnnotationMatcher JSON_PROPERTY_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_PROPERTY, true);

    @Override
    public String getDisplayName() {
        return "Remove redundant `@JsonProperty` argument";
    }

    @Override
    public String getDescription() {
        return "Remove `@JsonProperty` annotation or value attribute when the value matches the argument name.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JACKSON_JSON_PROPERTY, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        final J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (JSON_PROPERTY_MATCHER.matches(a)) {
                            // Get the parent variable declaration
                            J parent = getCursor().getParentTreeCursor().getValue();
                            if (parent instanceof J.VariableDeclarations) {
                                String parameterName = ((J.VariableDeclarations) parent).getVariables().get(0).getSimpleName();
                                J.Annotation modifiedAnnotation = processAnnotation(a, parameterName);
                                if (modifiedAnnotation != null) {
                                    return modifiedAnnotation;
                                }
                                // Schedule annotation removal after this pass
                                doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + JACKSON_JSON_PROPERTY) {
                                    @Override
                                    public boolean matches(J.Annotation annotation) {
                                        return annotation == a;
                                    }
                                }));
                                maybeRemoveImport(JACKSON_JSON_PROPERTY);
                                return a;
                            }
                        }

                        return a;
                    }

                    private J.@Nullable Annotation processAnnotation(J.Annotation annotation, String parameterName) {
                        List<Expression> args = annotation.getArguments();
                        if (args == null || args.isEmpty() || args.get(0) instanceof J.Empty) {
                            return annotation;
                        }

                        // Process the arguments and remove redundant "value" argument
                        List<Expression> filteredArgs = ListUtils.map(args, arg -> {
                            if (J.Literal.isLiteralValue(arg, parameterName)) {
                                // Unnamed argument case: @JsonProperty("name")
                                return null;
                            }
                            if (arg instanceof J.Assignment) {
                                // Named argument case: @JsonProperty(value = "name", ...)
                                J.Assignment assignment = (J.Assignment) arg;
                                if (assignment.getVariable() instanceof J.Identifier &&
                                        "value".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                                        J.Literal.isLiteralValue(assignment.getAssignment(), parameterName)) {
                                    return null;
                                }
                            }
                            return arg;
                        });

                        // If all arguments were removed, remove the entire annotation
                        if (filteredArgs.isEmpty()) {
                            return null;
                        }

                        // Return annotation with filtered arguments
                        return annotation.withArguments(ListUtils.mapFirst(filteredArgs, firstArg ->
                                requireNonNull(firstArg).withPrefix(annotation.getArguments().get(0).getPrefix())
                        ));
                    }
                }
        );
    }
}
