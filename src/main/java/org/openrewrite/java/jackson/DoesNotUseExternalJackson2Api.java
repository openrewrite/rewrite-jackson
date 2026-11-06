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

public class DoesNotUseExternalJackson2Api extends UsesExternalJackson2Api {
    @Override
    public String getDisplayName() {
        return "Does not use a known external Jackson 2 API";
    }

    @Override
    public String getDescription() {
        return "Match every file when the repository has no fixed external Jackson 2 API calls detected by " +
                "`UsesExternalJackson2Api`. Does not establish compatibility with reflective or runtime integration.";
    }

    @Override
    protected boolean negate() {
        return true;
    }
}
