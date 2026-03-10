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

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

/**
 * Tests for gap: Lombok's {@code @Jacksonized} annotation generates Jackson 2.x annotations
 * by default. When migrating to Jackson 3, {@code lombok.config} must be updated to set
 * {@code lombok.jacksonized.jacksonVersion = 3}.
 *
 * @see <a href="https://github.com/moderneinc/customer-requests/issues/1963">customer-requests#1963</a>
 */
@Issue("https://github.com/moderneinc/customer-requests/issues/1963")
class LombokJacksonizedConfigTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind",
            "lombok"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @Test
    void addJacksonVersionToLombokConfig() {
        rewriteRun(
          //language=java
          java(
            """
              import lombok.Builder;
              import lombok.Value;
              import lombok.extern.jackson.Jacksonized;

              @Value
              @Builder
              @Jacksonized
              class MyDto {
                  String name;
                  int age;
              }
              """
          ),
          text(
            """
              lombok.addLombokGeneratedAnnotation = true
              """,
            """
              lombok.addLombokGeneratedAnnotation = true
              lombok.jacksonized.jacksonVersion = 3
              """,
            spec -> spec.path("lombok.config")
          )
        );
    }

    @Test
    void createLombokConfigWhenMissing() {
        rewriteRun(
          //language=java
          java(
            """
              import lombok.Builder;
              import lombok.Value;
              import lombok.extern.jackson.Jacksonized;

              @Value
              @Builder
              @Jacksonized
              class MyDto {
                  String name;
              }
              """
          ),
          //noinspection DataFlowIssue
          text(
            null, // file does not exist before the recipe runs
            """
              lombok.jacksonized.jacksonVersion = 3
              """,
            spec -> spec.path("lombok.config")
          )
        );
    }
}
