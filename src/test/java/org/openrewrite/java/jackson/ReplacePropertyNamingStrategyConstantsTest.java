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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-jackson/issues/126")
class ReplacePropertyNamingStrategyConstantsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.ReplacePropertyNamingStrategyConstants")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),
            "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void replaceSnakeCaseStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy.SnakeCaseStrategy strategy = new PropertyNamingStrategy.SnakeCaseStrategy();
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

              class Test {
                  SnakeCaseStrategy strategy = new SnakeCaseStrategy();
              }
              """
          )
        );
    }

    @Test
    void replaceSnakeCaseConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategy.SNAKE_CASE;
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies;
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategies.SNAKE_CASE;
              }
              """
          )
        );
    }

    @Test
    void replaceUpperCamelCaseStrategy() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy.UpperCamelCaseStrategy strategy = new PropertyNamingStrategy.UpperCamelCaseStrategy();
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies.UpperCamelCaseStrategy;

              class Test {
                  UpperCamelCaseStrategy strategy = new UpperCamelCaseStrategy();
              }
              """
          )
        );
    }

    @Test
    void replaceKebabCaseConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategy.KEBAB_CASE;
              }
              """,
            """
              import com.fasterxml.jackson.databind.PropertyNamingStrategies;
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;

              class Test {
                  PropertyNamingStrategy strategy = PropertyNamingStrategies.KEBAB_CASE;
              }
              """
          )
        );
    }
}
