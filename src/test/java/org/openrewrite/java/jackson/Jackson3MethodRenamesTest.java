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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Jackson3MethodRenamesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"));
    }

    @Test
    void jsonGeneratorWriteObject() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen, Object value) throws Exception {
                      gen.writeObject(value);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen, Object value) throws Exception {
                      gen.writePOJO(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonGeneratorGetCurrentValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  Object test(JsonGenerator gen) {
                      return gen.getCurrentValue();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;

              class Test {
                  Object test(JsonGenerator gen) {
                      return gen.currentValue();
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonGeneratorSetCurrentValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen, Object value) {
                      gen.setCurrentValue(value);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen, Object value) {
                      gen.assignCurrentValue(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserGetTextCharacters() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;

              class Test {
                  char[] test(JsonParser parser) {
                      return parser.getTextCharacters();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;

              class Test {
                  char[] test(JsonParser parser) {
                      return parser.getStringCharacters();
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserGetCurrentLocation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonLocation;

              class Test {
                  JsonLocation test(JsonParser parser) {
                      return parser.getCurrentLocation();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.TokenStreamLocation;

              class Test {
                  TokenStreamLocation test(JsonParser parser) {
                      return parser.currentLocation();
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserGetTokenLocation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonLocation;

              class Test {
                  JsonLocation test(JsonParser parser) {
                      return parser.getTokenLocation();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.TokenStreamLocation;

              class Test {
                  TokenStreamLocation test(JsonParser parser) {
                      return parser.currentTokenLocation();
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserGetCurrentValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;

              class Test {
                  Object test(JsonParser parser) {
                      return parser.getCurrentValue();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;

              class Test {
                  Object test(JsonParser parser) {
                      return parser.currentValue();
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserSetCurrentValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;

              class Test {
                  void test(JsonParser parser, Object value) {
                      parser.setCurrentValue(value);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;

              class Test {
                  void test(JsonParser parser, Object value) {
                      parser.assignCurrentValue(value);
                  }
              }
              """
          )
        );
    }
}
