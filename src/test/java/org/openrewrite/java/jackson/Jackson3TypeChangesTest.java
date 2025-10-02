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

class Jackson3TypeChangesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"));
    }

    @Test
    void jsonFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;

              class Test {
                  JsonFactory factory = new JsonFactory();
              }
              """,
            """
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  TokenStreamFactory factory = new TokenStreamFactory();
              }
              """
          )
        );
    }


    @Test
    void jsonDeserializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonDeserializer;

              class CustomDeserializer extends JsonDeserializer<String> {
                  @Override
                  public String deserialize(com.fasterxml.jackson.core.JsonParser p,
                                           com.fasterxml.jackson.databind.DeserializationContext ctxt) {
                      return null;
                  }
              }
              """,
            """
              import tools.jackson.databind.ValueDeserializer;

              class CustomDeserializer extends ValueDeserializer<String> {
                  @Override
                  public String deserialize(tools.jackson.core.JsonParser p,
                                           tools.jackson.databind.DeserializationContext ctxt) {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonSerializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializer;

              class CustomSerializer extends JsonSerializer<String> {
                  @Override
                  public void serialize(String value,
                                       com.fasterxml.jackson.core.JsonGenerator gen,
                                       com.fasterxml.jackson.databind.SerializerProvider provider) {
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationContext;
              import tools.jackson.databind.ValueSerializer;

              class CustomSerializer extends ValueSerializer<String> {
                  @Override
                  public void serialize(String value,
                                       tools.jackson.core.JsonGenerator gen,
                                       SerializationContext provider) {
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonSerializable() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializable;

              class CustomObject implements JsonSerializable {
                  @Override
                  public void serialize(com.fasterxml.jackson.core.JsonGenerator gen,
                                       com.fasterxml.jackson.databind.SerializerProvider serializers) {
                  }
              }
              """,
            """
              import tools.jackson.databind.JacksonSerializable;
              import tools.jackson.databind.SerializationContext;

              class CustomObject implements JacksonSerializable {
                  @Override
                  public void serialize(tools.jackson.core.JsonGenerator gen,
                                       SerializationContext serializers) {
                  }
              }
              """
          )
        );
    }

    @Test
    void serializerProvider() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializerProvider;

              class Test {
                  void test(SerializerProvider provider) {
                      provider.getConfig();
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationContext;

              class Test {
                  void test(SerializationContext provider) {
                      provider.getConfig();
                  }
              }
              """
          )
        );
    }

    @Test
    void textNode() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.node.TextNode;

              class Test {
                  TextNode node = TextNode.valueOf("test");
              }
              """,
            """
              import tools.jackson.databind.node.StringNode;

              class Test {
                  StringNode node = StringNode.valueOf("test");
              }
              """
          )
        );
    }

    @Test
    void module() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.Module;

              class CustomModule extends Module {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }
              }
              """,
            """
              import tools.jackson.databind.JacksonModule;

              class CustomModule extends JacksonModule {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }
              }
              """
          )
        );
    }
}
