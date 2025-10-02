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

public class RemoveRedundantJsonPropertyOnRecords extends Recipe {

    private static final String JACKSON_JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final AnnotationMatcher JSON_PROPERTY_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_PROPERTY, true);

    @Override
    public String getDisplayName() {
        return "Remove redundant `@JsonProperty` on Java records";
    }

    @Override
    public String getDescription() {
        return "Remove `@JsonProperty` annotations from Java record components when the annotation value matches " +
                "the component name, as Jackson automatically handles record component names.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JACKSON_JSON_PROPERTY, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        // Only process record components
                        J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (enclosingClass == null || enclosingClass.getKind() != J.ClassDeclaration.Kind.Type.Record) {
                            return vd;
                        }

                        // Check if this is a record component (part of primary constructor)
                        if (enclosingClass.getPrimaryConstructor() == null ||
                                !enclosingClass.getPrimaryConstructor().contains(vd)) {
                            return vd;
                        }

                        if (vd.getVariables().isEmpty()) {
                            return vd;
                        }

                        String componentName = vd.getVariables().get(0).getSimpleName();

                        // Process @JsonProperty annotations
                        for (J.Annotation annotation : vd.getLeadingAnnotations()) {
                            if (JSON_PROPERTY_MATCHER.matches(annotation)) {
                                J.Annotation modified = processAnnotation(annotation, componentName);
                                if (modified == null) {
                                    // Schedule annotation removal
                                    doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + JACKSON_JSON_PROPERTY) {
                                        @Override
                                        public boolean matches(J.Annotation ann) {
                                            return ann == annotation;
                                        }
                                    }));
                                    maybeRemoveImport(JACKSON_JSON_PROPERTY);
                                } else if (modified != annotation) {
                                    vd = vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(), ann ->
                                            ann == annotation ? modified : ann
                                    ));
                                }
                            }
                        }

                        return vd;
                    }

                    private J.@Nullable Annotation processAnnotation(J.Annotation annotation, String componentName) {
                        List<Expression> args = annotation.getArguments();
                        if (args == null || args.isEmpty() || args.get(0) instanceof J.Empty) {
                            return annotation;
                        }

                        // Process the arguments and remove redundant "value" argument
                        List<Expression> filteredArgs = ListUtils.map(args, arg -> {
                            if (J.Literal.isLiteralValue(arg, componentName)) {
                                // Unnamed argument case: @JsonProperty("name")
                                return null;
                            }
                            if (arg instanceof J.Assignment) {
                                // Named argument case: @JsonProperty(value = "name", ...)
                                J.Assignment assignment = (J.Assignment) arg;
                                if (assignment.getVariable() instanceof J.Identifier &&
                                        "value".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) &&
                                        J.Literal.isLiteralValue(assignment.getAssignment(), componentName)) {
                                    return null;
                                }
                            }
                            return arg;
                        });

                        // If all arguments were removed, remove the entire annotation
                        if (filteredArgs.isEmpty()) {
                            return null;
                        }

                        // If no arguments were removed, keep annotation as-is
                        if (filteredArgs.size() == args.size()) {
                            return annotation;
                        }

                        // Return annotation with filtered arguments, preserving whitespace
                        return annotation.withArguments(ListUtils.mapFirst(filteredArgs, firstArg ->
                                requireNonNull(firstArg).withPrefix(args.get(0).getPrefix())
                        ));
                    }
                }
        );
    }
}
