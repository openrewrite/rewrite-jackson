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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class ObjectCodecToReadWriteContextTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ObjectCodecToReadWriteContext())
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
          spec -> spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3"),
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
          spec -> spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3"),
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
    void sameNameInSiblingBlockKeepsObjectCodecType() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void test(JsonParser p, ObjectMapper mapper) throws Exception {
                      {
                          ObjectCodec codec = p.getCodec();
                          codec.readTree(p);
                      }
                      {
                          ObjectCodec codec = mapper;
                          codec.readTree(p);
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.core.ObjectReadContext;

              class Test {
                  void test(JsonParser p, ObjectMapper mapper) throws Exception {
                      {
                          ObjectReadContext codec = p.getCodec();
                          codec.readTree(p);
                      }
                      {
                          ObjectCodec codec = mapper;
                          codec.readTree(p);
                      }
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> assertThat(readTreeDeclaringTypes(cu)).containsExactly("tools.jackson.core.ObjectReadContext", "com.fasterxml.jackson.core.ObjectCodec"))
          )
        );
    }

    @Test
    void sameNameInSiblingForLoopKeepsObjectCodecType() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void test(JsonParser p, ObjectMapper mapper) throws Exception {
                      for (ObjectCodec codec = p.getCodec(); codec != null; codec = null) {
                          codec.readTree(p);
                      }
                      for (ObjectCodec codec = mapper; codec != null; codec = null) {
                          codec.readTree(p);
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.core.ObjectReadContext;

              class Test {
                  void test(JsonParser p, ObjectMapper mapper) throws Exception {
                      for (ObjectReadContext codec = p.getCodec(); codec != null; codec = null) {
                          codec.readTree(p);
                      }
                      for (ObjectCodec codec = mapper; codec != null; codec = null) {
                          codec.readTree(p);
                      }
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> assertThat(readTreeDeclaringTypes(cu)).containsExactly("tools.jackson.core.ObjectReadContext", "com.fasterxml.jackson.core.ObjectCodec"))
          )
        );
    }

    @Test
    void fieldReferencedBeforeDeclarationAndThroughThis() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;

              class Test {
                  static JsonParser parser;

                  void test(JsonParser p) throws Exception {
                      codec.readTree(p);
                      this.codec.readTree(p);
                  }

                  ObjectCodec codec = parser.getCodec();
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import tools.jackson.core.ObjectReadContext;

              class Test {
                  static JsonParser parser;

                  void test(JsonParser p) throws Exception {
                      codec.readTree(p);
                      this.codec.readTree(p);
                  }

                  ObjectReadContext codec = parser.getCodec();
              }
              """,
            spec -> spec.afterRecipe(cu -> assertThat(readTreeDeclaringTypes(cu)).containsExactly("tools.jackson.core.ObjectReadContext", "tools.jackson.core.ObjectReadContext"))
          )
        );
    }

    @Test
    void parenthesizedInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.ObjectCodec;

              class Test {
                  void test(JsonParser p) throws Exception {
                      ObjectCodec codec = (p.getCodec());
                      codec.readTree(p);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import tools.jackson.core.ObjectReadContext;

              class Test {
                  void test(JsonParser p) throws Exception {
                      ObjectReadContext codec = (p.getCodec());
                      codec.readTree(p);
                  }
              }
              """
          )
        );
    }

    @Test
    void redundantCastIsRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.ObjectCodec;

              class Test {
                  void test(JsonGenerator g) throws Exception {
                      ObjectCodec codec = (ObjectCodec) g.getCodec();
                      codec.writeValue(g, "x");
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import tools.jackson.core.ObjectWriteContext;

              class Test {
                  void test(JsonGenerator g) throws Exception {
                      ObjectWriteContext codec = g.getCodec();
                      codec.writeValue(g, "x");
                  }
              }
              """
          )
        );
    }

    @Test
    void codecNotFromParserOrGeneratorIsLeftAlone() {
        rewriteRun(
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

    private static List<String> readTreeDeclaringTypes(J.CompilationUnit cu) {
        return new JavaIsoVisitor<List<String>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<String> declaringTypes) {
                if ("readTree".equals(method.getSimpleName())) {
                    declaringTypes.add(method.getMethodType().getDeclaringType().getFullyQualifiedName());
                }
                return super.visitMethodInvocation(method, declaringTypes);
            }
        }.reduce(cu, new ArrayList<>());
    }
}
