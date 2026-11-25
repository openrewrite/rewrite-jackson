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
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.search.DoesNotIncludeDependency;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Collections.emptyList;
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
        String newVersion = version == null ? "2.x" : version;
        for (String[] artifact : ARTIFACTS) {
            recipes.add(new ChangeDependency(CODEHAUS, artifact[0], artifact[1], artifact[2], newVersion,
                    null, null, null));
        }
        // jackson-databind brings jackson-core and jackson-annotations, so a module that needs it needs neither added
        List<String> databind = Arrays.asList(USAGES[0]).subList(2, USAGES[0].length);
        for (String[] usage : USAGES) {
            boolean broughtByDatabind = "jackson-core".equals(usage[1]) || "jackson-annotations".equals(usage[1]);
            for (int i = 2; i < usage.length; i++) {
                recipes.add(new AddJackson2Dependency(usage[0], usage[1], newVersion, usage[i],
                        broughtByDatabind ? databind : emptyList()));
            }
        }
        return recipes;
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
            this.delegate = new AddDependency(groupId, artifactId, version, null, onlyIfUsing,
                    null, "com.fasterxml.jackson*", null, null, null, null, null, null, true);
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
            return Preconditions.check(
                    Preconditions.and(notExcluded, new DoesNotIncludeDependency(groupId, artifactId, null, true, null, null).getVisitor()),
                    delegate.getVisitor(requireNonNull(acc.delegate)));
        }
    }
}
