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
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;

import java.util.*;

/** Repository-wide precondition; build files must follow the same policy as source files. */
public class UsesExternalJackson2Api extends ScanningRecipe<UsesExternalJackson2Api.Accumulator> {
    @Getter
    final String displayName = "Uses an external Jackson 2 API";
    @Getter
    final String description = "Match every file in a repository when an attributed call to a non-Spring external API " +
            "has a fixed Jackson 2 signature. Excludes supplied source APIs, Jackson APIs, shared annotations, and " +
            "generic signatures whose Jackson types may be inferred at the call site. This is a conservative " +
            "repository-wide precondition, not an analysis of reflective use or runtime bean wiring.";

    static class Accumulator {
        final Map<JavaProject, Set<String>> sourceTypes = new HashMap<>();
        final Map<JavaProject, List<JavaType.Method>> calls = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> sources = new FindJacksonMigrationBoundaries().getScanner(acc.sourceTypes);
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    sources.visit(tree, ctx);
                }
                return tree;
            }

            private void record(JavaType.Method method) {
                if (method == null) {
                    return;
                }
                String owner = method.getDeclaringType().getFullyQualifiedName();
                if (owner.startsWith("org.springframework.") || FindJacksonMigrationBoundaries.isJackson(owner) ||
                        FindJacksonMigrationBoundaries.isGenericApi(method)) {
                    return;
                }
                Set<String> types = FindJacksonMigrationBoundaries.signatureTypes(method);
                types.addAll(FindJacksonMigrationBoundaries.jacksonTypes(method.getDeclaringType()));
                if (types.stream().noneMatch(type -> type.startsWith("com.fasterxml.jackson."))) {
                    return;
                }
                JavaSourceFile source = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                JavaProject project = source.getMarkers().findFirst(JavaProject.class).orElse(null);
                acc.calls.computeIfAbsent(project, p -> new ArrayList<>()).add(method);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                record(method.getMethodType());
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass constructor, ExecutionContext ctx) {
                record(constructor.getConstructorType());
                return super.visitNewClass(constructor, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
                record(reference.getMethodType());
                return super.visitMemberReference(reference, ctx);
            }
        };
    }

    protected boolean negate() {
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> visitor = match(acc);
        return negate() ? Preconditions.not(visitor) : visitor;
    }

    private TreeVisitor<?, ExecutionContext> match(Accumulator acc) {
        for (Map.Entry<JavaProject, List<JavaType.Method>> project : acc.calls.entrySet()) {
            Set<String> sources = acc.sourceTypes.getOrDefault(project.getKey(), Collections.emptySet());
            for (JavaType.Method method : project.getValue()) {
                String owner = method.getDeclaringType().getFullyQualifiedName();
                if (!sources.contains(owner)) {
                    return new TreeVisitor<Tree, ExecutionContext>() {
                        @Override
                        public Tree preVisit(Tree tree, ExecutionContext ctx) {
                            stopAfterPreVisit();
                            return SearchResult.found(tree);
                        }
                    };
                }
            }
        }
        return TreeVisitor.noop();
    }
}
