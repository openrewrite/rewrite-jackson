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
package org.openrewrite.java.jackson.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class JacksonMigrationBoundaries extends DataTable<JacksonMigrationBoundaries.Row> {
    public JacksonMigrationBoundaries(Recipe recipe) {
        super(recipe, "Jackson migration boundaries",
                "Evidence of Jackson usage and API boundaries. External means absent from the build project's supplied sources, " +
                "not necessarily a third-party dependency. Findings are not automatic migration decisions.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Project", description = "Build project name, or unknown when the JavaProject marker is absent.")
        String project;
        @Column(displayName = "Source set", description = "Build source set, or unknown when its marker is absent.")
        String sourceSet;
        @Column(displayName = "Source path", description = "Repository-relative path of the source containing the evidence.")
        String sourcePath;
        @Column(displayName = "Category", description = "EXTERNAL_API, EXTERNAL_GENERIC_API, SOURCE_API, SPRING_API, JACKSON_API, MAPPER_CONSTRUCTION, DECLARATION, SHARED_ANNOTATION, or UNRESOLVED_CALL.")
        String category;
        @Column(displayName = "Jackson types", description = "Sorted Jackson types in the resolved signature or declaration. Shared annotations alone are not version-specific evidence.")
        String jacksonTypes;
        @Column(displayName = "API", description = "Resolved method signature or declared type. Empty for an unresolved call.")
        String api;
        @Column(displayName = "Evidence", description = "Source expression or declaration to inspect. No claim of whole-program data flow is made.")
        String evidence;
    }
}
