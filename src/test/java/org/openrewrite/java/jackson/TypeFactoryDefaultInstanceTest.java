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
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Tests for gap: {@code TypeFactory.defaultInstance()} was removed in Jackson 3.
 * It should be migrated to {@code TypeFactory.createDefaultInstance()}.
 *
 * @see <a href="https://github.com/moderneinc/customer-requests/issues/1963">customer-requests#1963</a>
 */
@Issue("https://github.com/moderneinc/customer-requests/issues/1963")
class TypeFactoryDefaultInstanceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @DocumentExample
    @Test
    void typeFactoryDefaultInstanceRenamed() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.type.TypeFactory;

              class Test {
                  JavaType getType() {
                      return TypeFactory.defaultInstance().constructType(String.class);
                  }
              }
              """,
            """
              import tools.jackson.databind.JavaType;
              import tools.jackson.databind.type.TypeFactory;

              class Test {
                  JavaType getType() {
                      return TypeFactory.createDefaultInstance().constructType(String.class);
                  }
              }
              """
          )
        );
    }
}
