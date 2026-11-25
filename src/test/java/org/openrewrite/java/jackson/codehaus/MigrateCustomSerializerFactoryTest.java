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
package org.openrewrite.java.jackson.codehaus;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateCustomSerializerFactoryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MigrateCustomSerializerFactory())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-asl", "jackson-mapper-asl"));
    }

    //language=java
    private static final String STRING_SERIALIZER = """
      import org.codehaus.jackson.JsonGenerator;
      import org.codehaus.jackson.map.JsonSerializer;
      import org.codehaus.jackson.map.SerializerProvider;

      public class StringUnicodeSerializer extends JsonSerializer<String> {
          @Override
          public void serialize(String value, JsonGenerator gen, SerializerProvider provider) {
          }
      }
      """;

    @DocumentExample
    @Test
    void specificMappingSetOnMapper() {
        rewriteRun(
          java(STRING_SERIALIZER),
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class JsonUtil {
                  static ObjectMapper mapper() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      CustomSerializerFactory serializerFactory = new CustomSerializerFactory();
                      serializerFactory.addSpecificMapping(String.class, new StringUnicodeSerializer());
                      objectMapper.setSerializerFactory(serializerFactory);
                      return objectMapper;
                  }
              }
              """,
            """
              import org.codehaus.jackson.Version;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.module.SimpleModule;

              class JsonUtil {
                  static ObjectMapper mapper() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      SimpleModule serializerFactory = new SimpleModule("JsonUtil", Version.unknownVersion());
                      serializerFactory.addSerializer(String.class, new StringUnicodeSerializer());
                      objectMapper.registerModule(serializerFactory);
                      return objectMapper;
                  }
              }
              """
          )
        );
    }

    @Test
    void genericMappingInObjectMapperSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.text.SimpleDateFormat;
              import java.util.Date;

              import org.codehaus.jackson.JsonGenerator;
              import org.codehaus.jackson.map.JsonSerializer;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.SerializerProvider;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class CustomObjectMapper extends ObjectMapper {
                  CustomObjectMapper() {
                      CustomSerializerFactory factory = new CustomSerializerFactory();
                      factory.addGenericMapping(Date.class, new JsonSerializer<Date>() {
                          @Override
                          public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                              gen.writeString(new SimpleDateFormat("yyyy-MM-dd").format(value));
                          }
                      });
                      this.setSerializerFactory(factory);
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.text.SimpleDateFormat;
              import java.util.Date;

              import org.codehaus.jackson.JsonGenerator;
              import org.codehaus.jackson.Version;
              import org.codehaus.jackson.map.JsonSerializer;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.SerializerProvider;
              import org.codehaus.jackson.map.module.SimpleModule;

              class CustomObjectMapper extends ObjectMapper {
                  CustomObjectMapper() {
                      SimpleModule factory = new SimpleModule("CustomObjectMapper", Version.unknownVersion());
                      factory.addSerializer(Date.class, new JsonSerializer<Date>() {
                          @Override
                          public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                              gen.writeString(new SimpleDateFormat("yyyy-MM-dd").format(value));
                          }
                      });
                      this.registerModule(factory);
                  }
              }
              """
          )
        );
    }

    @Test
    void unqualifiedSetSerializerFactory() {
        rewriteRun(
          java(STRING_SERIALIZER),
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class CustomObjectMapper extends ObjectMapper {
                  CustomObjectMapper() {
                      CustomSerializerFactory factory = new CustomSerializerFactory();
                      factory.addSpecificMapping(String.class, new StringUnicodeSerializer());
                      setSerializerFactory(factory);
                  }
              }
              """,
            """
              import org.codehaus.jackson.Version;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.module.SimpleModule;

              class CustomObjectMapper extends ObjectMapper {
                  CustomObjectMapper() {
                      SimpleModule factory = new SimpleModule("CustomObjectMapper", Version.unknownVersion());
                      factory.addSerializer(String.class, new StringUnicodeSerializer());
                      registerModule(factory);
                  }
              }
              """
          )
        );
    }

    @Test
    void factoryUsedOtherwiseIsLeftAlone() {
        rewriteRun(
          java(STRING_SERIALIZER),
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.SerializerFactory;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class JsonUtil {
                  SerializerFactory factory() {
                      CustomSerializerFactory serializerFactory = new CustomSerializerFactory();
                      serializerFactory.addSpecificMapping(String.class, new StringUnicodeSerializer());
                      new ObjectMapper().setSerializerFactory(serializerFactory);
                      return serializerFactory;
                  }
              }
              """
          )
        );
    }

    @Test
    void factoryNeverSetOnMapperIsLeftAlone() {
        rewriteRun(
          java(STRING_SERIALIZER),
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class JsonUtil {
                  void factory() {
                      CustomSerializerFactory serializerFactory = new CustomSerializerFactory();
                      serializerFactory.addSpecificMapping(String.class, new StringUnicodeSerializer());
                  }
              }
              """
          )
        );
    }
}
