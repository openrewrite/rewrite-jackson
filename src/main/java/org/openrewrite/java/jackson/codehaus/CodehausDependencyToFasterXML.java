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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.search.DoesNotIncludeDependency;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Version;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class CodehausDependencyToFasterXML extends Recipe {

    @Option(displayName = "Codehaus Jackson version",
            description = "The version of Codehaus Jackson to replace.",
            example = "2.x",
            required = false)
    @Nullable
    String version;

    String displayName = "Migrate dependencies from Jackson Codehaus (legacy) to FasterXML";

    String description = "Replace Codehaus Jackson dependencies, including the `-lgpl` artifacts and the `jackson-jaxrs`, " +
                         "`jackson-xc`, `jackson-smile` and `jackson-mrbean` companion modules, with their FasterXML " +
                         "Jackson 2 counterparts. Where a module uses Jackson 1 types that reach it transitively, or " +
                         "from an artifact other than the one that declares them, adds the Jackson 2 artifact that " +
                         "holds their counterparts, in the scope the types are used in and at the version of the " +
                         "Jackson 2 artifacts the build already has, if any.";

    private static final String CODEHAUS = "org.codehaus.jackson";

    /**
     * Each Jackson 1 artifact and the Jackson 2 artifact that replaces it.
     */
    private static final String[][] ARTIFACTS = {
            {"jackson-core-asl", "com.fasterxml.jackson.core", "jackson-core"},
            {"jackson-core-lgpl", "com.fasterxml.jackson.core", "jackson-core"},
            {"jackson-mapper-asl", "com.fasterxml.jackson.core", "jackson-databind"},
            {"jackson-mapper-lgpl", "com.fasterxml.jackson.core", "jackson-databind"},
            {"jackson-jaxrs", "com.fasterxml.jackson.jaxrs", "jackson-jaxrs-json-provider"},
            {"jackson-xc", "com.fasterxml.jackson.module", "jackson-module-jaxb-annotations"},
            {"jackson-smile", "com.fasterxml.jackson.dataformat", "jackson-dataformat-smile"},
            {"jackson-mrbean", "com.fasterxml.jackson.module", "jackson-module-mrbean"}
    };

    /**
     * Each Jackson 2 artifact, and the types whose use calls for it: the Jackson 1 types whose counterparts it holds,
     * since the dependency is decided on the sources as they were before the types moved, and the Jackson 2 types.
     */
    private static final String[][] USAGES = {
            {"com.fasterxml.jackson.core", "jackson-databind",
                    "org.codehaus.jackson.map..*",
                    "org.codehaus.jackson.node..*",
                    "org.codehaus.jackson.schema..*",
                    "org.codehaus.jackson.JsonNode",
                    "org.codehaus.jackson.type.JavaType",
                    "org.codehaus.jackson.util.TokenBuffer",
                    "com.fasterxml.jackson.databind..*"},
            {"com.fasterxml.jackson.core", "jackson-core",
                    "org.codehaus.jackson.*",
                    "org.codehaus.jackson.format..*",
                    "org.codehaus.jackson.impl..*",
                    "org.codehaus.jackson.io..*",
                    "org.codehaus.jackson.sym..*",
                    "org.codehaus.jackson.util..*",
                    "org.codehaus.jackson.type.TypeReference",
                    "com.fasterxml.jackson.core..*"},
            {"com.fasterxml.jackson.core", "jackson-annotations",
                    "org.codehaus.jackson.annotate..*",
                    "com.fasterxml.jackson.annotation..*"},
            {"com.fasterxml.jackson.jaxrs", "jackson-jaxrs-json-provider",
                    "org.codehaus.jackson.jaxrs..*"},
            {"com.fasterxml.jackson.module", "jackson-module-jaxb-annotations",
                    "org.codehaus.jackson.xc..*"},
            {"com.fasterxml.jackson.dataformat", "jackson-dataformat-smile",
                    "org.codehaus.jackson.smile..*"},
            {"com.fasterxml.jackson.module", "jackson-module-mrbean",
                    "org.codehaus.jackson.mrbean..*"}
    };

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new RemoveJackson1WhereJackson2Declared());
        String newVersion = version == null ? "2.x" : version;
        for (String[] artifact : ARTIFACTS) {
            recipes.add(new ChangeDependency(CODEHAUS, artifact[0], artifact[1], artifact[2], newVersion,
                    null, null, null));
        }
        // jackson-databind brings jackson-core and jackson-annotations, so a module that needs it needs neither added.
        // They are added before it: once a POM of a multi-module build is given jackson-databind in the test scope,
        // AddDependency no longer adds jackson-annotations to its compile scope.
        List<String> databind = Arrays.asList(USAGES[0]).subList(2, USAGES[0].length);
        List<String[]> usages = new ArrayList<>(Arrays.asList(USAGES).subList(1, USAGES.length));
        usages.add(2, USAGES[0]);
        for (String[] usage : usages) {
            boolean broughtByDatabind = "jackson-core".equals(usage[1]) || "jackson-annotations".equals(usage[1]);
            for (int i = 2; i < usage.length; i++) {
                recipes.add(new AddJackson2Dependency(usage[0], usage[1], newVersion, usage[i],
                        broughtByDatabind ? databind : emptyList()));
            }
        }
        return recipes;
    }

    /**
     * Removes a Jackson 1 dependency from a {@code <dependencies>} section of a POM that already declares its Jackson 2
     * counterpart in a scope at least as broad, or from a Gradle configuration that is or extends one that declares it,
     * where changing it would give the build a second declaration of the Jackson 2 artifact and move the one already
     * there, and the version property of the Jackson 1 one, to the newest release.
     */
    static class RemoveJackson1WhereJackson2Declared extends Recipe {
        @Getter
        final String displayName = "Remove Jackson 1 dependencies whose Jackson 2 counterpart is already declared";

        @Getter
        final String description = "Removes a Jackson 1 dependency, or managed dependency, from a POM section or Gradle " +
                                   "configuration that already has the Jackson 2 artifact that replaces it, which keeps that " +
                                   "declaration and its version.";

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            TreeVisitor<?, ExecutionContext> maven = mavenVisitor();
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    if (tree instanceof Xml.Document) {
                        return maven.visit(tree, ctx);
                    }
                    if (tree instanceof SourceFile && IsBuildGradle.matches(((SourceFile) tree).getSourcePath())) {
                        GradleProject project = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
                        if (project != null) {
                            Tree t = tree;
                            for (String[] removal : gradleRemovals(project)) {
                                t = new org.openrewrite.gradle.RemoveDependency(CODEHAUS, removal[0], removal[1]).getVisitor().visit(t, ctx);
                            }
                            return t;
                        }
                    }
                    return tree;
                }
            };
        }

        /**
         * The Jackson 1 artifact and configuration of each dependency declared in a configuration that is, or extends,
         * one that declares its Jackson 2 counterpart.
         */
        private static List<String[]> gradleRemovals(GradleProject project) {
            List<String[]> removals = new ArrayList<>();
            for (GradleDependencyConfiguration configuration : project.getConfigurations()) {
                for (org.openrewrite.maven.tree.Dependency dependency : configuration.getRequested()) {
                    String jackson2 = CODEHAUS.equals(dependency.getGroupId()) && dependency.getClassifier() == null ?
                            jackson2Counterpart(dependency.getArtifactId()) : null;
                    if (jackson2 == null) {
                        continue;
                    }
                    List<GradleDependencyConfiguration> covering = new ArrayList<>(configuration.allExtendsFrom());
                    covering.add(configuration);
                    for (GradleDependencyConfiguration c : covering) {
                        if (c.getRequested().stream().anyMatch(d -> jackson2.equals(d.getGroupId() + ":" + d.getArtifactId()))) {
                            removals.add(new String[]{dependency.getArtifactId(), configuration.getName()});
                            break;
                        }
                    }
                }
            }
            return removals;
        }

        private static TreeVisitor<?, ExecutionContext> mavenVisitor() {
            return new MavenIsoVisitor<ExecutionContext>() {
                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                    Xml.Tag t = super.visitTag(tag, ctx);
                    if (!"dependencies".equals(t.getName()) || t.getContent() == null) {
                        return t;
                    }
                    Map<String, Set<String>> declared = new HashMap<>();
                    for (Xml.Tag dependency : t.getChildren("dependency")) {
                        declared.computeIfAbsent(coordinates(dependency), k -> new HashSet<>()).add(scope(dependency));
                    }
                    List<Content> kept = new ArrayList<>(t.getContent().size());
                    boolean removed = false;
                    for (Content content : t.getContent()) {
                        if (content instanceof Xml.Tag && "dependency".equals(((Xml.Tag) content).getName())) {
                            Xml.Tag dependency = (Xml.Tag) content;
                            String jackson2 = jackson2Counterpart(dependency);
                            Set<String> jackson2Scopes = jackson2 == null ? emptySet() : declared.getOrDefault(jackson2, emptySet());
                            if (jackson2Scopes.stream().anyMatch(scope -> holds(scope, scope(dependency)))) {
                                removed = true;
                                continue;
                            }
                        }
                        kept.add(content);
                    }
                    if (!removed) {
                        return t;
                    }
                    maybeUpdateModel();
                    return t.withContent(kept);
                }
            };
        }

        private static String coordinates(Xml.Tag dependency) {
            return dependency.getChildValue("groupId").orElse("") + ":" + dependency.getChildValue("artifactId").orElse("");
        }

        private static String scope(Xml.Tag dependency) {
            return dependency.getChildValue("scope").orElse("compile");
        }

        private static @Nullable String jackson2Counterpart(Xml.Tag dependency) {
            if (!CODEHAUS.equals(dependency.getChildValue("groupId").orElse(null)) ||
                dependency.getChildValue("classifier").isPresent()) {
                return null;
            }
            return jackson2Counterpart(dependency.getChildValue("artifactId").orElse(null));
        }

        private static @Nullable String jackson2Counterpart(@Nullable String artifactId) {
            for (String[] artifact : ARTIFACTS) {
                if (artifact[0].equals(artifactId)) {
                    return artifact[1] + ":" + artifact[2];
                }
            }
            return null;
        }
    }

    /**
     * Whether the classpath of a Maven scope holds the dependencies of another: compile holds every other scope, and
     * provided and runtime each hold test.
     */
    private static boolean holds(String broad, String narrow) {
        return broad.equals(narrow) || "compile".equals(broad) ||
               "test".equals(narrow) && ("provided".equals(broad) || "runtime".equals(broad));
    }

    /**
     * Adds the Jackson 2 artifact where the types it is for are used, and leaves alone the build files that already
     * declare it, where {@link AddDependency} would move the declared version to the newest release, and the modules
     * where an artifact that brings it is added to every source set that uses its types, where it would be added to a
     * narrower scope than that artifact brings it in, such as the test scope when only tests use it directly, and so
     * hide it from the main code. An artifact used in the main source set is available to every source set; one used
     * only in another source set, such as the tests, is available to that source set alone.
     */
    @Getter
    @EqualsAndHashCode(callSuper = false, exclude = "delegate")
    static class AddJackson2Dependency extends ScanningRecipe<AddJackson2Dependency.Accumulator> {
        private static final String MAIN = "main";

        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String onlyIfUsing;
        private final List<String> notIfUsing;
        private final transient AddDependency delegate;

        AddJackson2Dependency(String groupId, String artifactId, String version, String onlyIfUsing, List<String> notIfUsing) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.onlyIfUsing = onlyIfUsing;
            this.notIfUsing = notIfUsing;
            this.delegate = addDependency(version);
        }

        private final String displayName = "Add a Jackson 2 dependency where its types are used";

        private final String description = "Adds the Jackson 2 artifact to the modules that use the types it is for and don't declare it.";

        @Override
        public String getInstanceNameSuffix() {
            return String.format("`%s:%s` if using `%s`", groupId, artifactId, onlyIfUsing);
        }

        static class Accumulator {
            AddDependency.@Nullable Accumulator delegate;

            /**
             * The source sets of each project that use the types this artifact is for.
             */
            final Map<JavaProject, Set<String>> using = new HashMap<>();

            /**
             * The source sets of each project that use the types of an artifact that brings this one.
             */
            final Map<JavaProject, Set<String>> broughtIn = new HashMap<>();

            boolean isExcluded(JavaProject project) {
                Set<String> usedIn = using.get(project);
                Set<String> bringingIn = broughtIn.get(project);
                if (usedIn == null || bringingIn == null) {
                    return false;
                }
                if (bringingIn.contains(MAIN)) {
                    return true;
                }
                return bringingIn.containsAll(usedIn);
            }
        }

        @Override
        public Accumulator getInitialValue(ExecutionContext ctx) {
            Accumulator acc = new Accumulator();
            acc.delegate = delegate.getInitialValue(ctx);
            return acc;
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
            TreeVisitor<?, ExecutionContext> scanner = delegate.getScanner(requireNonNull(acc.delegate));
            return new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    scanner.visit(tree, ctx);
                    if (tree instanceof JavaSourceFile && !notIfUsing.isEmpty()) {
                        JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                        if (project != null) {
                            String sourceSet = tree.getMarkers().findFirst(JavaSourceSet.class)
                                    .map(JavaSourceSet::getName)
                                    .orElse(MAIN);
                            if (new UsesType<>(onlyIfUsing, true).visit(tree, ctx) != tree) {
                                acc.using.computeIfAbsent(project, p -> new HashSet<>()).add(sourceSet);
                            }
                            for (String type : notIfUsing) {
                                if (new UsesType<>(type, false).visit(tree, ctx) != tree) {
                                    acc.broughtIn.computeIfAbsent(project, p -> new HashSet<>()).add(sourceSet);
                                    break;
                                }
                            }
                        }
                    }
                    return tree;
                }
            };
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
            TreeVisitor<?, ExecutionContext> notExcluded = new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    if (tree instanceof SourceFile &&
                        !tree.getMarkers().findFirst(JavaProject.class).map(acc::isExcluded).orElse(false)) {
                        return SearchResult.found(tree);
                    }
                    return tree;
                }
            };
            Map<String, TreeVisitor<?, ExecutionContext>> byVersion = new HashMap<>();
            TreeVisitor<?, ExecutionContext> add = new TreeVisitor<Tree, ExecutionContext>() {
                @Override
                public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                    if (!(tree instanceof SourceFile)) {
                        return tree;
                    }
                    String alignedVersion = alignedVersion((SourceFile) tree);
                    return byVersion.computeIfAbsent(alignedVersion,
                                    v -> addDependency(v).getVisitor(requireNonNull(acc.delegate)))
                            .visit(tree, ctx);
                }
            };
            return Preconditions.check(
                    Preconditions.and(notExcluded, new DoesNotIncludeDependency(groupId, artifactId, null, true, null, null).getVisitor()),
                    add);
        }

        private AddDependency addDependency(String version) {
            return new AddDependency(groupId, artifactId, version, null, onlyIfUsing,
                    null, null, null, null, null, null, null, null, true);
        }

        /**
         * The version to add the artifact at: that of the Jackson 2 artifacts the build file already declares, as it is
         * written there, or the version option when it declares none. {@code jackson-annotations} doesn't share their
         * versions: since 2.20 it is versioned 2.20, 2.21 and so on, without their patch number, it has no
         * micro-patches such as 2.12.7.1, and before 2.10 it had only the first release of each minor version.
         */
        private String alignedVersion(SourceFile sourceFile) {
            String[] others = null;
            String[] annotations = null;
            MavenResolutionResult maven = sourceFile.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
            if (maven != null) {
                for (List<ResolvedDependency> dependencies : maven.getDependencies().values()) {
                    for (ResolvedDependency d : dependencies) {
                        if (d.isDirect() && isJackson2(d.getGroupId(), d.getArtifactId()) && d.getRequested().getVersion() != null) {
                            String[] found = {d.getRequested().getVersion(), d.getVersion()};
                            if ("jackson-annotations".equals(d.getArtifactId())) {
                                annotations = newer(annotations, found);
                            } else {
                                others = newer(others, found);
                            }
                        }
                    }
                }
            }
            GradleProject gradle = sourceFile.getMarkers().findFirst(GradleProject.class).orElse(null);
            if (gradle != null) {
                for (GradleDependencyConfiguration configuration : gradle.getConfigurations()) {
                    for (org.openrewrite.maven.tree.Dependency d : configuration.getRequested()) {
                        if (isJackson2(d.getGroupId(), d.getArtifactId()) && d.getVersion() != null &&
                            d.getVersion().matches("\\d+(\\.\\d+)+")) {
                            String[] found = {d.getVersion(), d.getVersion()};
                            if ("jackson-annotations".equals(d.getArtifactId())) {
                                annotations = newer(annotations, found);
                            } else {
                                others = newer(others, found);
                            }
                        }
                    }
                }
            }
            if (!"jackson-annotations".equals(artifactId)) {
                if (others != null) {
                    return others[0];
                }
                if (annotations != null) {
                    return annotations[1].split("\\.").length == 2 ? annotations[1] + ".0" : annotations[0];
                }
                return version;
            }
            if (others == null) {
                return version;
            }
            String[] parts = others[1].split("\\.");
            try {
                int minor = parts.length < 2 ? -1 : Integer.parseInt(parts[1]);
                if (!"2".equals(parts[0]) || minor < 0) {
                    return version;
                }
                if (minor >= 20) {
                    return "2." + minor;
                }
                if (minor < 10) {
                    return "2." + minor + ".0";
                }
                return parts.length == 3 ? others[0] : parts[0] + "." + parts[1] + "." + parts[2];
            } catch (NumberFormatException e) {
                return version;
            }
        }

        private static String[] newer(String @Nullable [] current, String[] found) {
            return current == null || new Version(found[1]).compareTo(new Version(current[1])) > 0 ? found : current;
        }

        private static boolean isJackson2(@Nullable String groupId, String artifactId) {
            return groupId != null && groupId.startsWith("com.fasterxml.jackson") && !artifactId.endsWith("-bom");
        }
    }
}
