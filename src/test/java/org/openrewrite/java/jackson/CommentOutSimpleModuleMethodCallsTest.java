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

class CommentOutSimpleModuleMethodCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CommentOutSimpleModuleMethodCalls())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-2", "jackson-databind-2", "jackson-datatype-joda-2"));
    }

    @DocumentExample
    @Test
    void addCommentToAddSerializerOnJodaModule() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.datatype.joda.JodaModule;

              class Test {
                  void configure(JsonSerializer<String> serializer) {
                      JodaModule module = new JodaModule();
                      module.addSerializer(String.class, serializer);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.datatype.joda.JodaModule;

              class Test {
                  void configure(JsonSerializer<String> serializer) {
                      JodaModule module = new JodaModule();
                      /* TODO this module no longer extends SimpleModule in Jackson 3,
                       * so addSerializer/addDeserializer calls are no longer available.
                       * Move this call to a new SimpleModule and register it separately:
                       *   SimpleModule customModule = new SimpleModule();
                       *   customModule.addSerializer(...);
                       *   mapper.registerModule(customModule);
                       * Note: register the custom module AFTER the original module,
                       * as the last registered serializer for a given type wins.
                       */
                      module.addSerializer(String.class, serializer);
                  }
              }
              """
          )
        );
    }

    @Test
    void addCommentToAddDeserializerOnJodaModule() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.JsonDeserializer;
              import com.fasterxml.jackson.datatype.joda.JodaModule;

              class Test {
                  void configure(JsonDeserializer<String> deserializer) {
                      JodaModule module = new JodaModule();
                      module.addDeserializer(String.class, deserializer);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonDeserializer;
              import com.fasterxml.jackson.datatype.joda.JodaModule;

              class Test {
                  void configure(JsonDeserializer<String> deserializer) {
                      JodaModule module = new JodaModule();
                      /* TODO this module no longer extends SimpleModule in Jackson 3,
                       * so addSerializer/addDeserializer calls are no longer available.
                       * Move this call to a new SimpleModule and register it separately:
                       *   SimpleModule customModule = new SimpleModule();
                       *   customModule.addSerializer(...);
                       *   mapper.registerModule(customModule);
                       * Note: register the custom module AFTER the original module,
                       * as the last registered serializer for a given type wins.
                       */
                      module.addDeserializer(String.class, deserializer);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForSimpleModule() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.module.SimpleModule;

              class Test {
                  void configure(JsonSerializer<String> serializer) {
                      SimpleModule module = new SimpleModule();
                      module.addSerializer(String.class, serializer);
                  }
              }
              """
          )
        );
    }

    @Test
    void idempotent() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.datatype.joda.JodaModule;

              class Test {
                  void configure(JsonSerializer<String> serializer) {
                      JodaModule module = new JodaModule();
                      /* TODO this module no longer extends SimpleModule in Jackson 3,
                       * so addSerializer/addDeserializer calls are no longer available.
                       * Move this call to a new SimpleModule and register it separately:
                       *   SimpleModule customModule = new SimpleModule();
                       *   customModule.addSerializer(...);
                       *   mapper.registerModule(customModule);
                       * Note: register the custom module AFTER the original module,
                       * as the last registered serializer for a given type wins.
                       */
                      module.addSerializer(String.class, serializer);
                  }
              }
              """
          )
        );
    }
}
