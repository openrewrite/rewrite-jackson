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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Tests for gap: {@code StdDeserializer} no longer accepts {@code null} for the handled type
 * parameter in Jackson 3. The common {@code this(null)} / {@code super(vc)} pattern must be
 * replaced with {@code super(ActualType.class)}.
 *
 * @see <a href="https://github.com/moderneinc/customer-requests/issues/1963">customer-requests#1963</a>
 */
@Issue("https://github.com/moderneinc/customer-requests/issues/1963")
class StdDeserializerNullConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @Test
    void stdDeserializerThisNullPattern() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
              import java.io.IOException;

              class MyDeserializer extends StdDeserializer<String> {
                  public MyDeserializer() {
                      this(null);
                  }

                  public MyDeserializer(Class<?> vc) {
                      super(vc);
                  }

                  @Override
                  public String deserialize(JsonParser p, DeserializationContext ctxt)
                          throws IOException {
                      return p.getValueAsString();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.deser.std.StdDeserializer;

              class MyDeserializer extends StdDeserializer<String> {
                  public MyDeserializer() {
                      this(String.class);
                  }

                  public MyDeserializer(Class<?> vc) {
                      super(vc);
                  }

                  @Override
                  public String deserialize(JsonParser p, DeserializationContext ctxt) {
                      return p.getValueAsString();
                  }
              }
              """
          )
        );
    }

    @Test
    void stdDeserializerSuperNullDirectly() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
              import java.io.IOException;

              class ItemDeserializer extends StdDeserializer<Long> {
                  public ItemDeserializer() {
                      super((Class<?>) null);
                  }

                  @Override
                  public Long deserialize(JsonParser p, DeserializationContext ctxt)
                          throws IOException {
                      return p.getValueAsLong();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.deser.std.StdDeserializer;

              class ItemDeserializer extends StdDeserializer<Long> {
                  public ItemDeserializer() {
                      super(Long.class);
                  }

                  @Override
                  public Long deserialize(JsonParser p, DeserializationContext ctxt) {
                      return p.getValueAsLong();
                  }
              }
              """
          )
        );
    }
}
