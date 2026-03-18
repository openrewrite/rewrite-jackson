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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RenameJsonTokenFieldNameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),"jackson-core-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void renameFieldNameToPropertyName() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) {
                      if (parser.currentToken() == JsonToken.FIELD_NAME) {
                          System.out.println("It's a field");
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) {
                      if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
                          System.out.println("It's a field");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForOtherJsonTokenConstants() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) {
                      if (parser.currentToken() == JsonToken.VALUE_STRING) {
                          System.out.println("It's a String");
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) {
                      if (parser.currentToken() == JsonToken.VALUE_STRING) {
                          System.out.println("It's a String");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renameInIfElseChain() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) throws Exception {
                      JsonToken token = parser.nextToken();
                      if (token == JsonToken.FIELD_NAME) {
                          System.out.println("field");
                      } else if (token == JsonToken.VALUE_STRING) {
                          System.out.println("string");
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) throws Exception {
                      JsonToken token = parser.nextToken();
                      if (token == JsonToken.PROPERTY_NAME) {
                          System.out.println("field");
                      } else if (token == JsonToken.VALUE_STRING) {
                          System.out.println("string");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renameInSwitchStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

              import static com.fasterxml.jackson.core.JsonToken.FIELD_NAME;
              import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;

              class Test {
                  void parse(JsonParser parser) throws Exception {
                      JsonToken token = parser.nextToken();
                      switch (token) {
                          case FIELD_NAME:
                              System.out.println("field");
                              break;
                          case VALUE_STRING:
                              System.out.println("string");
                              break;
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.JsonToken;

              import static tools.jackson.core.JsonToken.PROPERTY_NAME;
              import static tools.jackson.core.JsonToken.VALUE_STRING;

              class Test {
                  void parse(JsonParser parser) throws Exception {
                      JsonToken token = parser.nextToken();
                      switch (token) {
                          case PROPERTY_NAME:
                              System.out.println("field");
                              break;
                          case VALUE_STRING:
                              System.out.println("string");
                              break;
                      }
                  }
              }
              """
          )
        );
    }
}
