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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class RemoveDoublyAnnotatedCodehausAnnotations extends Recipe {

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
                Preconditions.or(
                        new UsesType<>("com.fasterxml.jackson.annotation.JsonInclude", false),
                        new UsesType<>("com.fasterxml.jackson.databind.annotation.JsonSerialize", false)
                ),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(@NonNull J tree, ExecutionContext ctx) {
                        stopAfterPreVisit();
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@org.codehaus.jackson.map.annotate.JsonSerialize", true)));
                        maybeRemoveImport("org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.*");
                        maybeRemoveImport("org.codehaus.jackson.map.annotate.JsonSerialize.Typing.*");
                        doAfterVisit(new ShortenFullyQualifiedTypeReferences().getVisitor());
                        return tree;
                    }
                });
    }
}
