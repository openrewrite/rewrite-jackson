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

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

import java.util.HashSet;
import java.util.Set;

public class RemoveDoublyAnnotatedCodehausAnnotations extends Recipe {

    private static final AnnotationMatcher MATCHER_FASTERXML = new AnnotationMatcher("@com.fasterxml.jackson.databind.annotation.JsonSerialize", true);
    private static final AnnotationMatcher MATCHER_CODEHAUS = new AnnotationMatcher("@org.codehaus.jackson.map.annotate.JsonSerialize", true);

    @Override
    public String getDisplayName() {
        return "Remove Codehaus Jackson annotations if doubly annotated";
    }

    @Override
    public String getDescription() {
        return "Remove Codehaus Jackson annotations if they are doubly annotated with Jackson annotations from the `com.fasterxml.jackson` package.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("com.fasterxml.jackson.databind.annotation.JsonSerialize", false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(@NonNull J tree, ExecutionContext ctx) {
                        stopAfterPreVisit();

                        Set<J.Annotation> annotationsToRemove = new FindDoublyAnnotatedVisitor().reduce(tree, new HashSet<>());
                        AnnotationMatcher matcher = new AnnotationMatcher(
                                // ignored in practice, as we only match annotations previously found just above
                                "@org.codehaus.jackson.map.annotate.JsonSerialize", true) {
                            @Override
                            public boolean matches(J.Annotation annotation) {
                                return annotationsToRemove.contains(annotation);
                            }
                        };

                        doAfterVisit(new RemoveAnnotationVisitor(matcher));
                        maybeRemoveImport("org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.*");
                        maybeRemoveImport("org.codehaus.jackson.map.annotate.JsonSerialize.Typing.*");
                        doAfterVisit(new ShortenFullyQualifiedTypeReferences().getVisitor());
                        return tree;
                    }
                });
    }

    private static class FindDoublyAnnotatedVisitor extends JavaIsoVisitor<Set<J.Annotation>> {
        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, Set<J.Annotation> doublyAnnotated) {
            J.Annotation a = super.visitAnnotation(annotation, doublyAnnotated);
            if (MATCHER_CODEHAUS.matches(annotation) && service(AnnotationService.class).matches(getCursor().getParentOrThrow(), MATCHER_FASTERXML)) {
                doublyAnnotated.add(annotation);
            }
            return a;
        }
    }

}
