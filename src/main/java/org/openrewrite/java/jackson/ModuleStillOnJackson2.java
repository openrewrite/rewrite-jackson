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
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Getter
public class ModuleStillOnJackson2 extends ScanningRecipe<ModuleStillOnJackson2.Accumulator> {

    private static final ModuleHasDependency HAS_JACKSON_3 = new ModuleHasDependency("tools.jackson*", "*", null, null, null, null);
    // Direct dependencies only, as many libraries still bring Jackson 2 along.
    // `jackson-annotations` is left out, as Jackson 3 uses it too.
    private static final List<ModuleHasDependency> DECLARES_JACKSON_2 = Stream.of(
                    "com.fasterxml.jackson.core:jackson-core", "com.fasterxml.jackson.core:jackson-databind",
                    "com.fasterxml.jackson.dataformat:*", "com.fasterxml.jackson.datatype:*", "com.fasterxml.jackson.module:*",
                    "com.fasterxml.jackson.jaxrs:*", "com.fasterxml.jackson.jakarta.rs:*", "com.fasterxml.jackson.jr:*")
            .map(ga -> new ModuleHasDependency(ga.split(":")[0], ga.split(":")[1], null, null, true, null))
            .collect(toList());

    String displayName = "Find modules still on Jackson 2";
    String description = "Marks the source files of modules that are not on Jackson 3 yet. " +
            "A module is on Jackson 3 when it resolves Jackson 3 and neither it nor any of its child modules is still on Jackson 2, " +
            "that is, uses Jackson 2 types or declares a Jackson 2 dependency without resolving Jackson 3. " +
            "A parent that neither resolves Jackson 3 nor declares Jackson 2 itself, such as one with only dependency management, " +
            "is on Jackson 3 once one of its child modules is and none is still on Jackson 2, " +
            "unless it imports the Jackson 2 BOM. " +
            "Jackson 2 dependencies that a module on Jackson 3 still declares are left alone, as they are there on purpose, " +
            "for instance for generated sources that are not part of the LST.";

    public static class Accumulator {
        final Set<JavaProject> usingJackson2 = new HashSet<>();
        final Set<JavaProject> declaringJackson2 = new HashSet<>();
        final Set<JavaProject> resolvingJackson3 = new HashSet<>();
        final Set<JavaProject> importingJackson2Bom = new HashSet<>();
        final Map<UUID, JavaProject> mavenProjects = new HashMap<>();
        final Map<JavaProject, List<UUID>> mavenModules = new HashMap<>();
        final Map<JavaProject, String> gradlePaths = new HashMap<>(); // e.g. ":", ":app", ":libs:core"

        @Nullable
        Set<JavaProject> alreadyOnJackson3;

        Set<JavaProject> alreadyOnJackson3() {
            if (alreadyOnJackson3 == null) {
                alreadyOnJackson3 = new HashSet<>();
                Map<JavaProject, JacksonVersion> versions = new HashMap<>();
                Set<JavaProject> projects = new HashSet<>(mavenModules.keySet());
                projects.addAll(gradlePaths.keySet());
                for (JavaProject javaProject : projects) {
                    if (jacksonVersion(javaProject, versions) == JacksonVersion.JACKSON_3) {
                        alreadyOnJackson3.add(javaProject);
                    }
                }
            }
            return alreadyOnJackson3;
        }

        private JacksonVersion jacksonVersion(JavaProject javaProject, Map<JavaProject, JacksonVersion> versions) {
            JacksonVersion version = versions.get(javaProject);
            if (version == null) {
                // In progress, so that a cycle of modules ends the recursion
                versions.put(javaProject, JacksonVersion.NONE);
                version = computeJacksonVersion(javaProject, versions);
                versions.put(javaProject, version);
            }
            return version;
        }

        private JacksonVersion computeJacksonVersion(JavaProject javaProject, Map<JavaProject, JacksonVersion> versions) {
            if (usingJackson2.contains(javaProject)) {
                return JacksonVersion.JACKSON_2;
            }
            boolean anyModuleOnJackson3 = false;
            for (JavaProject module : modules(javaProject)) {
                JacksonVersion moduleVersion = jacksonVersion(module, versions);
                if (moduleVersion == JacksonVersion.JACKSON_2) {
                    return JacksonVersion.JACKSON_2;
                }
                anyModuleOnJackson3 |= moduleVersion == JacksonVersion.JACKSON_3;
            }
            if (resolvingJackson3.contains(javaProject)) {
                return JacksonVersion.JACKSON_3;
            }
            // An imported Jackson 2 BOM also manages `jackson-annotations`, which Jackson 3 needs at 2.20 or later
            if (declaringJackson2.contains(javaProject) || importingJackson2Bom.contains(javaProject)) {
                return JacksonVersion.JACKSON_2;
            }
            return anyModuleOnJackson3 ? JacksonVersion.JACKSON_3 : JacksonVersion.NONE;
        }

        private List<JavaProject> modules(JavaProject javaProject) {
            List<JavaProject> modules = new ArrayList<>();
            for (UUID moduleId : mavenModules.getOrDefault(javaProject, emptyList())) {
                JavaProject module = mavenProjects.get(moduleId);
                if (module != null) {
                    modules.add(module);
                }
            }
            String gradlePath = gradlePaths.get(javaProject);
            if (gradlePath != null) {
                String prefix = ":".equals(gradlePath) ? ":" : gradlePath + ":";
                for (Map.Entry<JavaProject, String> project : gradlePaths.entrySet()) {
                    if (project.getValue().startsWith(prefix) && !project.getValue().equals(gradlePath)) {
                        modules.add(project.getKey());
                    }
                }
            }
            return modules;
        }
    }

    private enum JacksonVersion {
        NONE, JACKSON_2, JACKSON_3
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        List<TreeVisitor<?, ExecutionContext>> jackson2Scanners = DECLARES_JACKSON_2.stream()
                .map(declaresJackson2 -> declaresJackson2.getScanner(acc.declaringJackson2))
                .collect(toList());
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
                for (TreeVisitor<?, ExecutionContext> jackson2Scanner : jackson2Scanners) {
                    jackson2Scanner.visit(tree, ctx);
                }
                jackson3Scanner.visit(tree, ctx);
                if (tree instanceof JavaSourceFile && !acc.usingJackson2.contains(javaProject) &&
                        usesJackson2((JavaSourceFile) tree)) {
                    acc.usingJackson2.add(javaProject);
                }
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    acc.mavenProjects.put(mrr.getId(), javaProject);
                    acc.mavenModules.put(javaProject, mrr.getModules().stream().map(MavenResolutionResult::getId).collect(toList()));
                    for (ManagedDependency managed : mrr.getPom().getRequested().getDependencyManagement()) {
                        if (managed instanceof ManagedDependency.Imported &&
                                "com.fasterxml.jackson".equals(managed.getGroupId()) && "jackson-bom".equals(managed.getArtifactId())) {
                            acc.importingJackson2Bom.add(javaProject);
                        }
                    }
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
