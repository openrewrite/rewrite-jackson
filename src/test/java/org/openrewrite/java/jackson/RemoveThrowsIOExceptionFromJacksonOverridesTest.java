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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Tests for gap: {@code throws IOException} is not removed from
 * {@code serialize()} / {@code deserialize()} method overrides.
 * In Jackson 3, these methods no longer throw {@code IOException}
 * ({@code JacksonException} extends {@code RuntimeException}).
 *
 * @see <a href="https://github.com/moderneinc/customer-requests/issues/1963">customer-requests#1963</a>
 */
@Issue("https://github.com/moderneinc/customer-requests/issues/1963")
class RemoveThrowsIOExceptionFromJacksonOverridesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @Test
    @DocumentExample
    void removeThrowsIOExceptionFromSerializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.SerializerProvider;
              import java.io.IOException;

              class MySerializer extends JsonSerializer<String> {
                  @Override
                  public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                      gen.writeString(value.toUpperCase());
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.databind.SerializationContext;
              import tools.jackson.databind.ValueSerializer;

              class MySerializer extends ValueSerializer<String> {
                  @Override
                  public void serialize(String value, JsonGenerator gen, SerializationContext provider) {
                      gen.writeString(value.toUpperCase());
                  }
              }
              """
          )
        );
    }

    @Test
    void removeThrowsIOExceptionFromDeserializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonDeserializer;
              import java.io.IOException;

              class MyDeserializer extends JsonDeserializer<String> {
                  @Override
                  public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                      return p.getValueAsString();
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.ValueDeserializer;

              class MyDeserializer extends ValueDeserializer<String> {
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
    void removeThrowsIOExceptionFromStdSerializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.databind.SerializerProvider;
              import com.fasterxml.jackson.databind.ser.std.StdSerializer;
              import java.io.IOException;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  @Override
                  public void serialize(java.util.Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                      gen.writeString(value.toString());
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.databind.ser.std.StdSerializer;
              import tools.jackson.databind.SerializationContext;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  @Override
                  public void serialize(java.util.Date value, JsonGenerator gen, SerializationContext provider) {
                      gen.writeString(value.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void keepOtherException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.databind.SerializerProvider;
              import com.fasterxml.jackson.databind.ser.std.StdSerializer;
              import java.io.IOException;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  @Override
                  public void serialize(java.util.Date value, JsonGenerator gen, SerializerProvider provider) throws IOException, IllegalArgumentException {
                      gen.writeString(value.toString());
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.databind.ser.std.StdSerializer;
              import tools.jackson.databind.SerializationContext;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  @Override
                  public void serialize(java.util.Date value, JsonGenerator gen, SerializationContext provider) throws IllegalArgumentException {
                      gen.writeString(value.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void keepOnOtherMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.databind.SerializerProvider;
              import com.fasterxml.jackson.databind.ser.std.StdSerializer;

              import java.io.IOException;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  public void otherSerialize(java.util.Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                      gen.writeString(value.toString());
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.databind.ser.std.StdSerializer;
              import tools.jackson.databind.SerializationContext;

              import java.io.IOException;

              class DateSerializer extends StdSerializer<java.util.Date> {
                  public DateSerializer() {
                      super(java.util.Date.class);
                  }

                  public void otherSerialize(java.util.Date value, JsonGenerator gen, SerializationContext provider) throws IOException {
                      gen.writeString(value.toString());
                  }
              }
              """
          )
        );
    }
}
