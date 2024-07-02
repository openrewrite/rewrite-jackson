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

import lombok.Value;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;

import java.util.ArrayList;
import java.util.List;

@Value
public class CodehausDependencyToFasterXML extends Recipe {

    @Option(displayName = "Codehaus Jackson version",
            description = "The version of Codehaus Jackson to replace.",
            example = "2.x",
            required = false)
    @Nullable
    String version;

    @Override
    public String getDisplayName() {
        return "Migrate dependencies from Jackson Codehaus (legacy) to FasterXML";
    }

    @Override
    public String getDescription() {
        return "Replace Codehaus Jackson dependencies with FasterXML Jackson dependencies, and add databind if needed.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        String newVersion = version == null ? "2.x" : version;
        recipes.add(new ChangeDependency(
                "org.codehaus.jackson",
                "jackson-core-asl",
                "com.fasterxml.jackson.core",
                "jackson-core",
                newVersion,
                null, null, null));
        recipes.add(new ChangeDependency(
                "org.codehaus.jackson",
                "jackson-mapper-asl",
                "com.fasterxml.jackson.core",
                "jackson-databind",
                newVersion,
                null, null, null));
        recipes.add(new AddDependency(
                "com.fasterxml.jackson.core",
                "jackson-databind",
                newVersion,
                null,
                "com.fasterxml.jackson.databind.*",
                null, null, null, null, null, null, null, null, true));
        return recipes;
    }
}
