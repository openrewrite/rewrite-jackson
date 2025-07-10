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
import org.openrewrite.java.tree.Space;

import java.util.List;

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
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        
                        if (JSON_PROPERTY_MATCHER.matches(a)) {
                            // Get the parent variable declaration
                            J parent = getCursor().dropParentUntil(J.class::isInstance).getValue();
                            if (parent instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) parent;
                                J.Annotation modifiedAnnotation = processAnnotation(a, vd);
                                if (modifiedAnnotation == null) {
                                    // Schedule annotation removal after this pass
                                    final J.Annotation annotationToRemove = a;
                                    doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + JACKSON_JSON_PROPERTY) {
                                        @Override
                                        public boolean matches(J.Annotation annotation) {
                                            return annotation == annotationToRemove;
                                        }
                                    }));
                                    maybeRemoveImport(JACKSON_JSON_PROPERTY);
                                    return a;
                                } else if (modifiedAnnotation != a) {
                                    return modifiedAnnotation;
                                }
                            }
                        }
                        
                        return a;
                    }

                    private J.@Nullable Annotation processAnnotation(J.Annotation annotation, J.VariableDeclarations variableDeclarations) {
                        if (variableDeclarations.getVariables().isEmpty()) {
                            return annotation;
                        }

                        String parameterName = variableDeclarations.getVariables().get(0).getSimpleName();
                        List<Expression> args = annotation.getArguments();

                        if (args == null || args.isEmpty()) {
                            return annotation;
                        }

                        // Process the arguments and remove redundant "value" argument
                        List<Expression> filteredArgs = ListUtils.map(args, arg -> {
                            if (arg instanceof J.Literal) {
                                // Unnamed argument case: @JsonProperty("name")
                                Object value = ((J.Literal) arg).getValue();
                                if (parameterName.equals(value)) {
                                    return null; // Remove this argument
                                }
                            } else if (arg instanceof J.Assignment) {
                                // Named argument case: @JsonProperty(value = "name", ...)
                                J.Assignment assignment = (J.Assignment) arg;
                                if (assignment.getVariable() instanceof J.Identifier) {
                                    J.Identifier varId = (J.Identifier) assignment.getVariable();
                                    if ("value".equals(varId.getSimpleName()) && assignment.getAssignment() instanceof J.Literal) {
                                        Object value = ((J.Literal) assignment.getAssignment()).getValue();
                                        if (parameterName.equals(value)) {
                                            return null; // Remove this argument
                                        }
                                    }
                                }
                            }
                            return arg;
                        });

                        // If all arguments were removed, remove the entire annotation
                        if (filteredArgs.isEmpty()) {
                            return null;
                        }

                        // If we have remaining arguments and the first one has extra prefix whitespace, fix it
                        Expression firstArg = filteredArgs.get(0);
                        if (firstArg.getPrefix().getWhitespace().contains(" ")) {
                            filteredArgs.set(0, firstArg.withPrefix(Space.EMPTY));
                        }

                        // Return annotation with filtered arguments
                        return annotation.withArguments(filteredArgs);
                    }
                }
        );
    }
}
