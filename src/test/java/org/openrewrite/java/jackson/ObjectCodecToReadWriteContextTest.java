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

class ObjectCodecToReadWriteContextTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2",
              "jackson-core-2",
              "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void parserCodecVariableBecomesObjectReadContext() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonDeserializer;
              import com.fasterxml.jackson.databind.JsonNode;

              import java.io.IOException;

              class MoneyDeserializer extends JsonDeserializer<String> {
                  @Override
                  public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                      ObjectCodec codec = p.getCodec();
                      JsonNode node = codec.readTree(p);
                      return node.get("amount").asText();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.JsonNode;
              import tools.jackson.core.ObjectReadContext;
              import tools.jackson.databind.ValueDeserializer;

              class MoneyDeserializer extends ValueDeserializer<String> {
                  @Override
                  public String deserialize(JsonParser p, DeserializationContext ctxt) {
                      ObjectReadContext codec = p.objectReadContext();
                      JsonNode node = codec.readTree(p);
                      return node.get("amount").asString();
                  }
              }
              """
          )
        );
    }

    @Test
    void generatorCodecVariableBecomesObjectWriteContext() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.SerializerProvider;

              import java.io.IOException;

              class WrappingSerializer extends JsonSerializer<Object> {
                  @Override
                  public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                      ObjectCodec codec = gen.getCodec();
                      codec.writeValue(gen, value);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.core.ObjectWriteContext;
              import tools.jackson.databind.SerializationContext;
              import tools.jackson.databind.ValueSerializer;

              class WrappingSerializer extends ValueSerializer<Object> {
                  @Override
                  public void serialize(Object value, JsonGenerator gen, SerializationContext serializers) {
                      ObjectWriteContext codec = gen.objectWriteContext();
                      codec.writeValue(gen, value);
                  }
              }
              """
          )
        );
    }

    @Test
    void codecNotFromParserOrGeneratorIsLeftAlone() {
        rewriteRun(
          spec -> spec.recipe(new ObjectCodecToReadWriteContext()),
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void test(JsonParser p, ObjectCodec param) {
                      ObjectCodec fromMapper = new ObjectMapper();
                      ObjectCodec mixed = p.getCodec(), other = param;
                      var inferred = p.getCodec();
                  }
              }
              """
          )
        );
    }
}
