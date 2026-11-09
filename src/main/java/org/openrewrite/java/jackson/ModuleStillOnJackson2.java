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
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.dependencies.search.ModuleHasDependency;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Getter
public class ModuleStillOnJackson2 extends ScanningRecipe<ModuleStillOnJackson2.Accumulator> {

    private static final ModuleHasDependency HAS_JACKSON_3 = new ModuleHasDependency("tools.jackson*", "*", null, null, null, null);

    String displayName = "Find modules still on Jackson 2";
    String description = "Marks the source files of modules that either do not resolve Jackson 3 yet, " +
            "or still use Jackson 2 types in their own sources or those of their child modules. " +
            "Modules that already resolve Jackson 3 and no longer use Jackson 2 types are left unmarked, " +
            "as any Jackson 2 dependencies they still declare are there on purpose, " +
            "for instance for generated sources that are not part of the LST. " +
            "A parent that does not resolve Jackson 3 itself, such as one with only dependency management, " +
            "is left unmarked once all its child modules are.";

    public static class Accumulator {
        final Set<JavaProject> usingJackson2 = new HashSet<>();
        final Set<JavaProject> resolvingJackson3 = new HashSet<>();
        final Map<UUID, JavaProject> mavenProjects = new HashMap<>();
        final Map<JavaProject, List<UUID>> mavenModules = new HashMap<>();
        final Map<JavaProject, String> gradlePaths = new HashMap<>(); // e.g. ":", ":app", ":libs:core"

        @Nullable
        Set<JavaProject> alreadyOnJackson3;

        Set<JavaProject> alreadyOnJackson3() {
            if (alreadyOnJackson3 == null) {
                alreadyOnJackson3 = new HashSet<>();
                Set<JavaProject> projects = new HashSet<>(resolvingJackson3);
                projects.addAll(mavenModules.keySet());
                for (JavaProject javaProject : projects) {
                    if (isOnJackson3(javaProject)) {
                        alreadyOnJackson3.add(javaProject);
                    }
                }
            }
            return alreadyOnJackson3;
        }

        private boolean isOnJackson3(JavaProject javaProject) {
            if (usingJackson2.contains(javaProject)) {
                return false;
            }
            if (resolvingJackson3.contains(javaProject)) {
                return !anyChildUsesJackson2(javaProject);
            }
            // Any module still on Jackson 2 keeps its parent marked, so that the parent's managed versions,
            // which the module's renamed dependencies rely on, get renamed too.
            List<UUID> moduleIds = mavenModules.getOrDefault(javaProject, emptyList());
            for (UUID moduleId : moduleIds) {
                JavaProject module = mavenProjects.get(moduleId);
                if (module == null || !isOnJackson3(module)) {
                    return false;
                }
            }
            return !moduleIds.isEmpty();
        }

        private boolean anyChildUsesJackson2(JavaProject javaProject) {
            for (UUID moduleId : mavenModules.getOrDefault(javaProject, emptyList())) {
                JavaProject module = mavenProjects.get(moduleId);
                if (module != null && (usingJackson2.contains(module) || anyChildUsesJackson2(module))) {
                    return true;
                }
            }
            String gradlePath = gradlePaths.get(javaProject);
            if (gradlePath == null) {
                return false;
            }
            String prefix = ":".equals(gradlePath) ? ":" : gradlePath + ":";
            for (JavaProject project : usingJackson2) {
                String path = gradlePaths.get(project);
                if (path != null && path.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> jackson3Scanner = HAS_JACKSON_3.getScanner(acc.resolvingJackson3);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                JavaProject javaProject = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (javaProject == null) {
                    return tree;
                }
                acc.alreadyOnJackson3 = null;
                jackson3Scanner.visit(tree, ctx);
                if (tree instanceof JavaSourceFile && !acc.usingJackson2.contains(javaProject) &&
                        usesJackson2((JavaSourceFile) tree)) {
                    acc.usingJackson2.add(javaProject);
                }
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    acc.mavenProjects.put(mrr.getId(), javaProject);
                    acc.mavenModules.put(javaProject, mrr.getModules().stream().map(MavenResolutionResult::getId).collect(toList()));
                });
                tree.getMarkers().findFirst(GradleProject.class).ifPresent(gp -> acc.gradlePaths.put(javaProject, gp.getPath()));
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<JavaProject> alreadyOnJackson3 = acc.alreadyOnJackson3();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile &&
                        !tree.getMarkers().findFirst(JavaProject.class).filter(alreadyOnJackson3::contains).isPresent()) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }

    private static boolean usesJackson2(JavaSourceFile sourceFile) {
        TypesInUse typesInUse = sourceFile.getTypesInUse();
        for (JavaType type : typesInUse.getTypesInUse()) {
            if (type instanceof JavaType.FullyQualified && isJackson2Type((JavaType.FullyQualified) type)) {
                return true;
            }
        }
        for (JavaType.Method method : typesInUse.getUsedMethods()) {
            if (isJackson2Type(method.getDeclaringType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJackson2Type(JavaType.FullyQualified type) {
        String fqn = type.getFullyQualifiedName();
        // Jackson 3 still uses the `com.fasterxml.jackson.annotation` package, so those do not tie a module to Jackson 2
        return fqn.startsWith("com.fasterxml.jackson.") && !fqn.startsWith("com.fasterxml.jackson.annotation.") ||
                fqn.startsWith("org.codehaus.jackson.");
    }
}
