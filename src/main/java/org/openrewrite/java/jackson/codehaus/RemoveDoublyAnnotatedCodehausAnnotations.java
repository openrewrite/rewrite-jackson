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
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.Map;

public class RemoveDoublyAnnotatedCodehausAnnotations extends Recipe {

    private static final String ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE = "org.codehaus.jackson.map.annotate.JsonSerialize";
    private static final String COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE = "com.fasterxml.jackson.databind.annotation.JsonSerialize";

    private static final AnnotationMatcher MATCHER_CODEHAUS = new AnnotationMatcher("@" + ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE, true);
    private static final AnnotationMatcher MATCHER_FASTERXML = new AnnotationMatcher("@" + COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE, true);

    @Getter
    final String displayName = "Remove Codehaus Jackson annotations if doubly annotated";

    @Getter
    final String description = "Remove Codehaus Jackson annotations if they are doubly annotated with Jackson annotations from the `com.fasterxml.jackson` package, " +
                               "such as a Jackson 1 `@JsonTypeInfo` beside the Jackson 2 `@JsonTypeInfo` of the same element, which would otherwise become a repeated annotation.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(COM_FASTERXML_JACKSON_DATABIND_ANNOTATION_JSON_SERIALIZE, false),
                        Preconditions.and(new UsesType<>("org.codehaus.jackson..*", false), new UsesType<>("com.fasterxml.jackson..*", false))),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(@NonNull J tree, ExecutionContext ctx) {
                        stopAfterPreVisit();

                        // Map from codehaus -> fasterxml annotation
                        Map<J.Annotation, J.Annotation> doubleAnnotated = new FindDoublyAnnotatedVisitor().reduce(tree, new HashMap<>());

                        AnnotationMatcher removeCodehausMatcher = new AnnotationMatcher(
                                // ignored in practice, as we only match annotations previously found just above
                                "@" + ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE, true) {
                            @Override
                            public boolean matches(J.Annotation annotation) {
                                return doubleAnnotated.containsKey(annotation);
                            }
                        };
                        doAfterVisit(new RemoveAnnotationVisitor(removeCodehausMatcher));
                        for (J.Annotation codehaus : doubleAnnotated.keySet()) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(codehaus.getType());
                            if (type != null && !ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE.equals(type.getFullyQualifiedName())) {
                                maybeRemoveImport(type.getFullyQualifiedName());
                            }
                        }
                        maybeRemoveImport(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE + ".Inclusion.*");
                        maybeRemoveImport(ORG_CODEHAUS_JACKSON_MAP_ANNOTATE_JSON_SERIALIZE + ".Typing.*");
                        doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(tree));
                        return tree;
                    }
                });
    }

    static class FindDoublyAnnotatedVisitor extends JavaIsoVisitor<Map<J.Annotation, J.Annotation>> {

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, Map<J.Annotation, J.Annotation> doublyAnnotated) {
            J.Annotation a = super.visitAnnotation(annotation, doublyAnnotated);
            if (MATCHER_CODEHAUS.matches(annotation)) {
                // Find sibling fasterXMl annotation
                service(AnnotationService.class)
                        .getAllAnnotations(getCursor().getParentOrThrow())
                        .stream()
                        .filter(MATCHER_FASTERXML::matches)
                        .findFirst()
                        .ifPresent(fasterxml -> doublyAnnotated.put(annotation, fasterxml));
            } else {
                // Any other Jackson 1 annotation beside the Jackson 2 annotation of the same name, which Jackson 2 reads
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                Object annotated = getCursor().getParentTreeCursor().getValue();
                if (type != null && type.getFullyQualifiedName().startsWith("org.codehaus.jackson.") &&
                    (annotated instanceof J.ClassDeclaration || annotated instanceof J.MethodDeclaration ||
                     annotated instanceof J.VariableDeclarations)) {
                    service(AnnotationService.class)
                            .getAllAnnotations(getCursor().getParentOrThrow())
                            .stream()
                            .filter(other -> isJackson2Counterpart(other, type))
                            .findFirst()
                            .ifPresent(fasterxml -> doublyAnnotated.put(annotation, fasterxml));
                }
            }
            return a;
        }
    }

    private static boolean isJackson2Counterpart(J.Annotation annotation, JavaType.FullyQualified jackson1) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
        return type != null && type.getFullyQualifiedName().startsWith("com.fasterxml.jackson.") &&
               type.getClassName().equals(jackson1.getClassName());
    }
}
