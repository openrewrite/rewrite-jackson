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

class IOExceptionToJacksonExceptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IOExceptionToJacksonException())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-3", "jackson-databind-3"));
    }

    @DocumentExample
    @Test
    void objectMapperReadValue() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  void deserialize(byte[] data) {
                      ObjectMapper objectMapper = new ObjectMapper();
                      try {
                          objectMapper.readValue(data, String.class);
                      } catch (IOException e) {
                          throw new RuntimeException("IO exception", e);
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JacksonException;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  void deserialize(byte[] data) {
                      ObjectMapper objectMapper = new ObjectMapper();
                      try {
                          objectMapper.readValue(data, String.class);
                      } catch (JacksonException e) {
                          throw new RuntimeException("IO exception", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void objectMapperWriteValueAsString() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  String serialize(Object obj) {
                      ObjectMapper mapper = new ObjectMapper();
                      try {
                          return mapper.writeValueAsString(obj);
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JacksonException;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  String serialize(Object obj) {
                      ObjectMapper mapper = new ObjectMapper();
                      try {
                          return mapper.writeValueAsString(obj);
                      } catch (JacksonException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void objectWriterWriteValueAsBytes() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.ObjectWriter;

              class Test {
                  byte[] write(Object obj) {
                      ObjectWriter writer = new ObjectMapper().writer();
                      try {
                          return writer.writeValueAsBytes(obj);
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JacksonException;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.ObjectWriter;

              class Test {
                  byte[] write(Object obj) {
                      ObjectWriter writer = new ObjectMapper().writer();
                      try {
                          return writer.writeValueAsBytes(obj);
                      } catch (JacksonException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNoJacksonCallInTry() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import java.io.FileInputStream;

              class Test {
                  void readFile() {
                      try {
                          new FileInputStream("test.txt").read();
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenCatchingException() {
        rewriteRun(
          java(
            """
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  void deserialize(byte[] data) {
                      ObjectMapper mapper = new ObjectMapper();
                      try {
                          mapper.readValue(data, String.class);
                      } catch (Exception e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAlreadyCatchingJacksonException() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.JacksonException;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  void deserialize(byte[] data) {
                      ObjectMapper mapper = new ObjectMapper();
                      try {
                          mapper.readValue(data, String.class);
                      } catch (JacksonException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }
}
