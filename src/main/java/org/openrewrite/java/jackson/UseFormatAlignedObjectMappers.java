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
package org.openrewrite.java.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

public class UseFormatAlignedObjectMappers extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use format alignment `ObjectMappers`";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Replace wrapping `ObjectMapper` calls with their format aligned implementation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return super.getVisitor();
    }
}
