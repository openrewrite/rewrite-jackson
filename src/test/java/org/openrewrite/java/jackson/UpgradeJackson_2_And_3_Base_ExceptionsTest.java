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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpgradeJackson_2_And_3_Base_ExceptionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.java.jackson.UpgradeJackson_2_And_3_Base_Exceptions")
          );
    }

    @DocumentExample
    @Test
    void jacksonUpgradeToNewBaseExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonProcessingException;
              import com.fasterxml.jackson.databind.JsonMappingException;
              import com.fasterxml.jackson.databind.ObjectMapper;
              
              class Test {
                  static void helloJackson() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      Object object = new Object();
                      try {
                          String json = objectMapper.writeValueAsString(object);
                          try {
                              objectMapper.readValue(json, Object.class);
                          } catch (JsonMappingException e) {
                              throw new RuntimeException(e);
                          }
                      } catch (JsonProcessingException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JacksonException;
              import com.fasterxml.jackson.databind.DatabindException;
              import com.fasterxml.jackson.databind.ObjectMapper;
              
              class Test {
                  static void helloJackson() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      Object object = new Object();
                      try {
                          String json = objectMapper.writeValueAsString(object);
                          try {
                              objectMapper.readValue(json, Object.class);
                          } catch (DatabindException e) {
                              throw new RuntimeException(e);
                          }
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
    void jacksonUpgradeToVersion3_JsonProcessingException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.core.JsonProcessingException;
              
              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (JsonProcessingException e) {
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JacksonException;
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              
              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (JacksonException e) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_JsonMappingException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.databind.JsonMappingException;
              
              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (JsonMappingException e) {
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.databind.DatabindException;
              
              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (DatabindException e) {
                      }
                  }
              }
              """
          )
        );
    }
}
