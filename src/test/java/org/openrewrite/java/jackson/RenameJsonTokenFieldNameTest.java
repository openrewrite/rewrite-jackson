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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RenameJsonTokenFieldNameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameJsonTokenFieldName())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),"jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
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
                  void parse(JsonParser parser) throws Exception {
                      if (parser.currentToken() == JsonToken.FIELD_NAME) {
                          String name = parser.currentName();
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

              class Test {
                  void parse(JsonParser parser) throws Exception {
                      if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
                          String name = parser.currentName();
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
                  void parse(JsonParser parser) throws Exception {
                      if (parser.currentToken() == JsonToken.VALUE_STRING) {
                          String value = parser.getText();
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
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

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
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonToken;

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
