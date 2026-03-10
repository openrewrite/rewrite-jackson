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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class RemoveThrowsIOExceptionFromJacksonOverrides extends Recipe {

    private static final String IO_EXCEPTION = "java.io.IOException";

    final String displayName = "Remove `throws IOException` from Jackson serializer/deserializer overrides";

    final String description = "In Jackson 3, serializer and deserializer methods no longer throw `IOException` " +
            "since `JacksonException` extends `RuntimeException`. This recipe removes `throws IOException` " +
            "from `serialize()` and `deserialize()` method overrides in Jackson serializer/deserializer subclasses.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("com.fasterxml.jackson.databind.JsonSerializer", true),
                        new UsesType<>("com.fasterxml.jackson.databind.JsonDeserializer", true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                        List<NameTree> throws_ = md.getThrows();
                        if (throws_ == null || throws_.isEmpty()) {
                            return md;
                        }

                        String methodName = md.getSimpleName();
                        if (!"serialize".equals(methodName) && !"deserialize".equals(methodName)) {
                            return md;
                        }

                        J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (classDecl == null || classDecl.getType() == null) {
                            return md;
                        }

                        if (!TypeUtils.isAssignableTo("com.fasterxml.jackson.databind.JsonSerializer", classDecl.getType()) &&
                                !TypeUtils.isAssignableTo("com.fasterxml.jackson.databind.JsonDeserializer", classDecl.getType())) {
                            return md;
                        }

                        boolean hasIOException = throws_.stream()
                                .anyMatch(t -> TypeUtils.isOfClassType(t.getType(), IO_EXCEPTION));
                        if (!hasIOException) {
                            return md;
                        }

                        List<NameTree> newThrows = new ArrayList<>();
                        for (NameTree t : throws_) {
                            if (!TypeUtils.isOfClassType(t.getType(), IO_EXCEPTION)) {
                                newThrows.add(t);
                            }
                        }

                        maybeRemoveImport(IO_EXCEPTION);
                        return md.withThrows(newThrows.isEmpty() ? null : newThrows);
                    }
                }
        );
    }
}
