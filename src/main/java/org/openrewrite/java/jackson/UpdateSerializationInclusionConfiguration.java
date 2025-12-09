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
import org.jspecify.annotations.Nullable;
package org.openrewrite.java.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.Set;

import static java.util.Collections.singleton;

public class UpdateSerializationInclusionConfiguration extends Recipe {

    @Override
    public String getDisplayName() {
        return "Update configuration of serialization inclusion in ObjectMapper for Jackson 3";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)` is no longer supported " +
                "and should be replaced by `changeDefaultPropertyInclusion()` for both `valueInclusion` and `contentInclusion`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return null;
    }
}
