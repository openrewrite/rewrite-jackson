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

    @DocumentExample
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
    void jsonGeneratorGetCodec() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  Object test(JsonGenerator gen) {
                      return gen.getCodec();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;

              class Test {
                  Object test(JsonGenerator gen) {
                      return gen.objectWriteContext();
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
    void jsonGeneratorFieldMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen) {
                      gen.writeArrayFieldStart("ananas");
                      gen.writeBinaryField("banana", new byte[]{1,2,3});
                      gen.writeBooleanField("carrot", true);
                      gen.writeFieldId(1L);
                      gen.writeFieldName("test");
                      gen.writeNullField("kiwi");
                      gen.writeNumberField("orange", 1.0);
                      gen.writeObjectField("peach", "peach");
                      gen.writeObjectFieldStart("pear");
                      gen.writeOmittedField("plum");
                      gen.writePOJOField("strawberry", "strawberry");
                      gen.writeStringField("watermelon", "watermelon");
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;

              class Test {
                  void test(JsonGenerator gen) {
                      gen.writeArrayPropertyStart("ananas");
                      gen.writeBinaryProperty("banana", new byte[]{1,2,3});
                      gen.writeBooleanProperty("carrot", true);
                      gen.writePropertyId(1L);
                      gen.writeName("test");
                      gen.writeNullProperty("kiwi");
                      gen.writeNumberProperty("orange", 1.0);
                      gen.writeObjectProperty("peach", "peach");
                      gen.writeObjectPropertyStart("pear");
                      gen.writeOmittedProperty("plum");
                      gen.writePOJOProperty("strawberry", "strawberry");
                      gen.writeStringProperty("watermelon", "watermelon");
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonParserGetCodec() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;

              class Test {
                  char[] test(JsonParser parser) {
                      return parser.getCodec();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;

              class Test {
                  char[] test(JsonParser parser) {
                      return parser.objectReadContext();
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

    @Test
    void jsonParserTextMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;

              class Test {
                  void test(JsonParser parser) {
                      String text = parser.getText();
                      char[] characters = parser.getTextCharacters();
                      int length = parser.getTextLength();
                      int offset = parser.getTextOffset();
                      boolean hasText = parser.hasTextCharacters();
                      String nextText = parser.nextTextValue();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;

              class Test {
                  void test(JsonParser parser) {
                      String text = parser.getString();
                      char[] characters = parser.getStringCharacters();
                      int length = parser.getStringLength();
                      int offset = parser.getStringOffset();
                      boolean hasText = parser.hasStringCharacters();
                      String nextText = parser.nextStringValue();
                  }
              }
              """
          )
        );
    }

}
