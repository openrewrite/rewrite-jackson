/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.jackson.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.jackson.codehaus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

public class JsonIncludeAnnotation extends Recipe {
    private static final String ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE = "org.codehaus.jackson.map.annotate.JsonSerialize";
    private static final String COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";

    @Override
    public String getDisplayName() {
        return "Migrate to Jackson `@JsonInclude`";
    }

    @Override
    public String getDescription() {
        return "Move Codehaus' `@JsonSerialize.include` argument to FasterXMLs `@JsonInclude` annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE, false),
                        Preconditions.not(new UsesType<>(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE, false))
                ),
                new IntroduceJsonIncludeVisitor());
    }

    private static class IntroduceJsonIncludeVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitClassDeclaration(J.ClassDeclaration decl, ExecutionContext ctx) {
            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(decl, ctx);

            // Loop over annotations and extract the include argument from the old JsonSerialize annotation
            AtomicReference<String> includeArgument = new AtomicReference<>();
            cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(),
                    ann -> mapAnnotation(ann, includeArgument)));

            // Add the new JsonInclude annotation with the include argument
            if (includeArgument.get() != null) {
                cd = JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                        .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                        .javaParser(JavaParser.fromJavaVersion().classpath("jackson-annotations"))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
            }

            return cd;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration decl, ExecutionContext ctx) {
            J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(decl, ctx);

            // Loop over annotations and extract the include argument from the old JsonSerialize annotation
            AtomicReference<String> includeArgument = new AtomicReference<>();
            md = md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(),
                    ann -> mapAnnotation(ann, includeArgument)));

            // Add the new JsonInclude annotation with the include argument
            if (includeArgument.get() != null) {
                md = JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                        .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                        .javaParser(JavaParser.fromJavaVersion().classpath("jackson-annotations"))
                        .build()
                        .apply(updateCursor(md), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
            }

            return md;
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations decl, ExecutionContext ctx) {
            J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(decl, ctx);

            // Loop over annotations and extract the include argument from the old JsonSerialize annotation
            AtomicReference<String> includeArgument = new AtomicReference<>();
            vd = vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(),
                    ann -> mapAnnotation(ann, includeArgument)));

            // Add the new JsonInclude annotation with the include argument
            if (includeArgument.get() != null) {
                vd = JavaTemplate.builder("@JsonInclude(value = JsonInclude.Include." + includeArgument.get() + ")")
                        .imports(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE)
                        .javaParser(JavaParser.fromJavaVersion().classpath("jackson-annotations"))
                        .build()
                        .apply(updateCursor(vd), vd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport(COM_FASTERXML_JACKSON_ANNOTATION_JSON_INCLUDE);
            }
            return vd;
        }

        private final AnnotationMatcher annotationMatcher = new AnnotationMatcher("@" + ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE, false);

        @Nullable
        private J.Annotation mapAnnotation(J.Annotation ann, AtomicReference<String> includeArgument) {
            if (!annotationMatcher.matches(ann)) {
                return ann;
            }

            // Strip out the include argument from the old JsonSerialize annotation
            ann = ann.withArguments(ListUtils.map(ann.getArguments(), arg -> {
                J.Assignment assignment = (J.Assignment) arg;
                J.Identifier variable = (J.Identifier) assignment.getVariable();
                if (!"include".equals(variable.getSimpleName())) {
                    return arg;
                }

                // Extract the include argument value as String for the new JsonInclude annotation
                J right = assignment.getAssignment();
                if (right instanceof J.FieldAccess) {
                    includeArgument.set(((J.FieldAccess) right).getName().getSimpleName());
                } else if (right instanceof J.Identifier) {
                    includeArgument.set(((J.Identifier) right).getSimpleName());
                }

                maybeRemoveImport(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE + ".Inclusion");
                maybeRemoveImport(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE + ".Inclusion." + includeArgument.get());
                return null;
            }));

            // If arguments are now empty remove the entire annotation
            if (ann.getArguments() == null || ann.getArguments().isEmpty()) {
                maybeRemoveImport(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE);
                return null;
            }

            return ann;
        }
    }
}
