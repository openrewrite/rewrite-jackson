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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

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
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        // Check if this variable declaration is directly inside a record class declaration
                        // In a record, parameters are direct children of the class declaration, not in a method
                        Object parent = getCursor().getParentOrThrow().getValue();
                        if (parent instanceof J.ClassDeclaration) {
                            J.ClassDeclaration classDecl = (J.ClassDeclaration) parent;
                            if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Record) {
                                // This is a record component
                                final J.VariableDeclarations currentVd = vd;
                                vd = vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(), annotation -> {
                                    if (JSON_PROPERTY_MATCHER.matches(annotation)) {
                                        J.Annotation modifiedAnnotation = processAnnotation(annotation, currentVd);
                                        if (modifiedAnnotation == null) {
                                            maybeRemoveImport(JACKSON_JSON_PROPERTY);
                                        }
                                        return modifiedAnnotation;
                                    }
                                    return annotation;
                                }));
                            }
                        }

                        return vd;
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

                        // Return annotation with filtered arguments
                        return annotation.withArguments(filteredArgs);
                    }
                }
        );
    }
}
