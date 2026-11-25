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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;

import java.util.ArrayList;
import java.util.List;

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
        for (String[] usage : USAGES) {
            for (int i = 2; i < usage.length; i++) {
                recipes.add(new AddDependency(usage[0], usage[1], newVersion, null, usage[i],
                        null, "com.fasterxml.jackson*", null, null, null, null, null, null, true));
            }
        }
        return recipes;
    }
}
