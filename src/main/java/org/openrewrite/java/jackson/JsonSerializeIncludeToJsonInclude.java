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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Migrates the deprecated {@code com.fasterxml.jackson.databind.annotation.JsonSerialize(include = Inclusion.X)}
 * shape to {@code com.fasterxml.jackson.annotation.JsonInclude(JsonInclude.Include.X)}. The {@code include}
 * attribute on {@code @JsonSerialize} was deprecated in Jackson 2.x and removed entirely in Jackson 3.x; running
 * this recipe before the Jackson&nbsp;2&nbsp;→&nbsp;3 package rename produces the correct
 * {@code tools.jackson.annotation.JsonInclude} on the Jackson 3 side instead of a broken
 * {@code tools.jackson.databind.annotation.JsonSerialize(include = ...)}.
 *
 * <p>Other arguments on the original {@code @JsonSerialize} (e.g. {@code using}) are preserved on the annotation;
 * if {@code include} was the only argument the annotation is removed entirely. If a sibling {@code @JsonInclude}
 * already exists on the same target, the recipe strips {@code include} from {@code @JsonSerialize} but does not add
 * a duplicate {@code @JsonInclude} ({@code @JsonInclude} is not {@code @Repeatable}).
 */
public class JsonSerializeIncludeToJsonInclude extends Recipe {
    private static final String COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonSerialize";
    private static final String COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";
    private static final AnnotationMatcher JSON_SERIALIZE_MATCHER = new AnnotationMatcher("@" + COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE, false);
    private static final AnnotationMatcher JSON_INCLUDE_MATCHER = new AnnotationMatcher("@" + COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE, false);

    @Getter
    final String displayName = "Migrate deprecated `@JsonSerialize(include = ...)` to `@JsonInclude`";

    @Getter
    final String description = "Move the deprecated `include` attribute of FasterXML's `@JsonSerialize` to a " +
            "separate `@JsonInclude` annotation. The `include` attribute was deprecated in Jackson 2.x and " +
            "removed in Jackson 3.x; running this recipe before the Jackson 2 → 3 package rename produces " +
            "a correct `tools.jackson.annotation.JsonInclude` on the Jackson 3 side.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE, false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration decl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(decl, ctx);

                        AtomicReference<String> includeArgument = new AtomicReference<>();
                        cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(),
                                ann -> mapAnnotation(ann, includeArgument)));

                        if (includeArgument.get() != null && !hasJsonIncludeSibling(cd.getLeadingAnnotations())) {
                            maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
                            return JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                                    .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-annotations"))
                                    .build()
                                    .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        }

                        return cd;
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration decl, ExecutionContext ctx) {
                        J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(decl, ctx);

                        AtomicReference<String> includeArgument = new AtomicReference<>();
                        md = md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(),
                                ann -> mapAnnotation(ann, includeArgument)));

                        if (includeArgument.get() != null && !hasJsonIncludeSibling(md.getLeadingAnnotations())) {
                            maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
                            return JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                                    .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-annotations"))
                                    .build()
                                    .apply(updateCursor(md), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        }

                        return md;
                    }

                    @Override
                    public J visitVariableDeclarations(J.VariableDeclarations decl, ExecutionContext ctx) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(decl, ctx);

                        AtomicReference<String> includeArgument = new AtomicReference<>();
                        vd = vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(),
                                ann -> mapAnnotation(ann, includeArgument)));

                        if (includeArgument.get() != null && !hasJsonIncludeSibling(vd.getLeadingAnnotations())) {
                            maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
                            return JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                                    .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-annotations"))
                                    .build()
                                    .apply(updateCursor(vd), vd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        }
                        return vd;
                    }

                    private J.@Nullable Annotation mapAnnotation(J.Annotation ann, AtomicReference<String> includeArgument) {
                        if (!JSON_SERIALIZE_MATCHER.matches(ann)) {
                            return ann;
                        }

                        ann = ann.withArguments(ListUtils.map(ann.getArguments(), arg -> {
                            if (!(arg instanceof J.Assignment)) {
                                return arg;
                            }
                            J.Assignment assignment = (J.Assignment) arg;
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            if (!"include".equals(variable.getSimpleName())) {
                                return arg;
                            }

                            J right = assignment.getAssignment();
                            if (right instanceof J.FieldAccess) {
                                includeArgument.set(((J.FieldAccess) right).getName().getSimpleName());
                            } else if (right instanceof J.Identifier) {
                                includeArgument.set(((J.Identifier) right).getSimpleName());
                            }

                            maybeRemoveImport(COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE + ".Inclusion");
                            if (includeArgument.get() != null) {
                                maybeRemoveImport(COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE + ".Inclusion." + includeArgument.get());
                            }
                            return null;
                        }));

                        // If arguments are now empty remove the entire annotation
                        if (ann.getArguments() == null || ann.getArguments().isEmpty()) {
                            maybeRemoveImport(COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE);
                            return null;
                        }

                        return ann;
                    }

                    private boolean hasJsonIncludeSibling(List<J.Annotation> annotations) {
                        for (J.Annotation ann : annotations) {
                            if (JSON_INCLUDE_MATCHER.matches(ann)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
    }
}
