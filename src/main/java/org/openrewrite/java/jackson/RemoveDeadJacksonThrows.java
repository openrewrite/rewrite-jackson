/*
 * Copyright 2024 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveDeadJacksonThrows extends Recipe {

    private static final Set<String> TARGET_FQNS;
    private static final Set<String> TARGET_SIMPLE_NAMES;

    static {
        Set<String> fqns = new HashSet<>(Arrays.asList(
                // Pre-migration (com.fasterxml) namespace
                "com.fasterxml.jackson.core.JsonProcessingException",
                "com.fasterxml.jackson.databind.JsonMappingException",
                // Post-migration (tools.jackson) namespace
                "tools.jackson.core.exc.JacksonException",
                "tools.jackson.core.exc.JsonProcessingException",
                "tools.jackson.databind.exc.JsonMappingException",
                "tools.jackson.databind.DatabindException"
        ));
        TARGET_FQNS = Collections.unmodifiableSet(fqns);

        Set<String> simpleNames = new HashSet<>(Arrays.asList(
                "JsonProcessingException",
                "JacksonException",
                "JsonMappingException",
                "DatabindException"
        ));
        TARGET_SIMPLE_NAMES = Collections.unmodifiableSet(simpleNames);
    }

    @Override
    public String getDisplayName() {
        return "Remove dead throws declarations for unchecked Jackson exceptions";
    }

    @Override
    public String getDescription() {
        return "Removes `throws` declarations for Jackson exception types that are unchecked " +
                "in Jackson 3 (`JacksonException extends RuntimeException`). " +
                "Covers `JsonProcessingException`, `JacksonException`, `JsonMappingException`, and `DatabindException` " +
                "in both `com.fasterxml.jackson` and `tools.jackson` namespaces.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                    ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                List<NameTree> throwz = m.getThrows();
                if (throwz == null || throwz.isEmpty()) {
                    return m;
                }

                List<NameTree> newThrowz = ListUtils.map(throwz, t -> {
                    if (!isTargetException(t)) {
                        return t;
                    }
                    JavaType type = t.getType();
                    if (type instanceof JavaType.FullyQualified) {
                        maybeRemoveImport(((JavaType.FullyQualified) type).getFullyQualifiedName());
                    } else {
                        maybeRemoveImport("com.fasterxml.jackson.core.JsonProcessingException");
                        maybeRemoveImport("com.fasterxml.jackson.databind.JsonMappingException");
                        maybeRemoveImport("tools.jackson.core.exc.JacksonException");
                        maybeRemoveImport("tools.jackson.core.exc.JsonProcessingException");
                        maybeRemoveImport("tools.jackson.databind.exc.JsonMappingException");
                        maybeRemoveImport("tools.jackson.databind.DatabindException");
                    }
                    return null;
                });

                if (newThrowz == throwz) {
                    return m;
                }

                return m.withThrows(newThrowz.isEmpty() ? null : newThrowz);
            }

            private boolean isTargetException(NameTree nameTree) {
                JavaType type = nameTree.getType();
                if (type instanceof JavaType.FullyQualified) {
                    String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                    if (fqn.startsWith("<")) {
                        return false;
                    }
                    return TARGET_FQNS.contains(fqn);
                }
                String simpleName = getSimpleName(nameTree);
                if (!TARGET_SIMPLE_NAMES.contains(simpleName)) {
                    return false;
                }
                J.CompilationUnit cu = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
                return cu.getImports().stream().anyMatch(imp -> {
                    String qualifier = imp.getQualid().toString();
                    return qualifier.toLowerCase().contains("jackson")
                            && qualifier.endsWith("." + simpleName);
                });
            }

            private String getSimpleName(NameTree nameTree) {
                if (nameTree instanceof J.Identifier) {
                    return ((J.Identifier) nameTree).getSimpleName();
                }
                if (nameTree instanceof J.FieldAccess) {
                    return ((J.FieldAccess) nameTree).getSimpleName();
                }
                return "";
            }
        };
    }
}
