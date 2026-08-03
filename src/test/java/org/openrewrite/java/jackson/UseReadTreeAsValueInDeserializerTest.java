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

class UseReadTreeAsValueInDeserializerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new UseReadTreeAsValueInDeserializer())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2",
              "jackson-core-2",
              "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void treeToValueInDeserializeRewrittenToReadTreeAsValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Corepos {}

              class PlatformMapDeserializer extends StdDeserializer<Corepos> {
                  protected PlatformMapDeserializer() {
                      super(Corepos.class);
                  }

                  @Override
                  public Corepos deserialize(JsonParser parser, DeserializationContext context) throws Exception {
                      JsonNode value = parser.readValueAsTree();
                      return parser.getCodec().treeToValue(value, Corepos.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Corepos {}

              class PlatformMapDeserializer extends StdDeserializer<Corepos> {
                  protected PlatformMapDeserializer() {
                      super(Corepos.class);
                  }

                  @Override
                  public Corepos deserialize(JsonParser parser, DeserializationContext context) throws Exception {
                      JsonNode value = parser.readValueAsTree();
                      return context.readTreeAsValue(value, Corepos.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void ctxtParamNameIsPreserved() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Foo {}

              class FooDeserializer extends StdDeserializer<Foo> {
                  protected FooDeserializer() {
                      super(Foo.class);
                  }

                  @Override
                  public Foo deserialize(JsonParser p, DeserializationContext ctxt) throws Exception {
                      JsonNode node = p.readValueAsTree();
                      return p.getCodec().treeToValue(node, Foo.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Foo {}

              class FooDeserializer extends StdDeserializer<Foo> {
                  protected FooDeserializer() {
                      super(Foo.class);
                  }

                  @Override
                  public Foo deserialize(JsonParser p, DeserializationContext ctxt) throws Exception {
                      JsonNode node = p.readValueAsTree();
                      return ctxt.readTreeAsValue(node, Foo.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsTodoCommentWhenNoDeserializationContextInScope() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.JsonNode;

              class Foo {}

              class Util {
                  static Foo readFoo(JsonParser parser, JsonNode node) throws Exception {
                      return parser.getCodec().treeToValue(node, Foo.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.JsonNode;

              class Foo {}

              class Util {
                  static Foo readFoo(JsonParser parser, JsonNode node) throws Exception {
                      return /* TODO Jackson 3: ObjectReadContext has no treeToValue; call readTreeAsValue on a DeserializationContext */ parser.getCodec().treeToValue(node, Foo.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void endToEndWithinUpgradeJackson_2_3() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3"),
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Corepos {}

              class PlatformMapDeserializer extends StdDeserializer<Corepos> {
                  protected PlatformMapDeserializer() {
                      super(Corepos.class);
                  }

                  @Override
                  public Corepos deserialize(JsonParser parser, DeserializationContext context) throws Exception {
                      JsonNode value = parser.readValueAsTree();
                      return parser.getCodec().treeToValue(value, Corepos.class);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.JsonNode;
              import tools.jackson.databind.deser.std.StdDeserializer;

              class Corepos {}

              class PlatformMapDeserializer extends StdDeserializer<Corepos> {
                  protected PlatformMapDeserializer() {
                      super(Corepos.class);
                  }

                  @Override
                  public Corepos deserialize(JsonParser parser, DeserializationContext context) throws Exception {
                      JsonNode value = parser.readValueAsTree();
                      return context.readTreeAsValue(value, Corepos.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRewriteStandaloneObjectMapperCall() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

              class Foo {}

              class FooDeserializer extends StdDeserializer<Foo> {
                  private final ObjectMapper mapper = new ObjectMapper();

                  protected FooDeserializer() {
                      super(Foo.class);
                  }

                  @Override
                  public Foo deserialize(JsonParser p, DeserializationContext ctxt) throws Exception {
                      JsonNode node = p.readValueAsTree();
                      return mapper.treeToValue(node, Foo.class);
                  }
              }
              """
          )
        );
    }
}
