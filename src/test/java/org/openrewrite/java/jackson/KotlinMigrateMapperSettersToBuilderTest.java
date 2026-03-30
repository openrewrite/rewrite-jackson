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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

@Issue("https://github.com/moderneinc/customer-requests/issues/2079")
class KotlinMigrateMapperSettersToBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMapperSettersToBuilder())
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void fluentChainInFunction() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import com.fasterxml.jackson.databind.SerializationFeature
              import com.fasterxml.jackson.databind.json.JsonMapper

              class A {
                  fun create(): JsonMapper {
                      return JsonMapper()
                          .disable(SerializationFeature.INDENT_OUTPUT)
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.SerializationFeature
              import com.fasterxml.jackson.databind.json.JsonMapper

              class A {
                  fun create(): JsonMapper {
                      return JsonMapper.builder()
                          .disable(SerializationFeature.INDENT_OUTPUT)
                          .build()
                  }
              }
              """
          )
        );
    }

    @Nested
    class FunctionScoped {

        @Test
        void registerModuleInFunction() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class A {
                      fun create(module: Module): JsonMapper {
                          val mapper = JsonMapper()
                          mapper.registerModule(module)
                          return mapper
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class A {
                      fun create(module: Module): JsonMapper {
                          return JsonMapper.builder()
                              .addModule(module)
                              .build()
                      }
                  }
                  """
              )
            );
        }

        @Test
        void multipleSettersInFunction() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class A {
                      fun create(module: Module): JsonMapper {
                          val mapper = JsonMapper()
                          mapper.registerModule(module)
                          mapper.disable(SerializationFeature.INDENT_OUTPUT)
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                          return mapper
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class A {
                      fun create(module: Module): JsonMapper {
                          return JsonMapper.builder()
                              .addModule(module)
                              .disable(SerializationFeature.INDENT_OUTPUT)
                              .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                              .build()
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class InitBlock {

        @Test
        void settersInInitBlock() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class Config(module: Module) {
                      val mapper = JsonMapper()

                      init {
                          mapper.registerModule(module)
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class Config(module: Module) {
                      val mapper = JsonMapper.builder()
                          .addModule(module)
                          .build()
                  }
                  """
              )
            );
        }

        @Test
        void multipleSettersInInitBlock() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class Config(module: Module) {
                      val mapper = JsonMapper()

                      init {
                          mapper.registerModule(module)
                          mapper.disable(SerializationFeature.INDENT_OUTPUT)
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.databind.json.JsonMapper

                  class Config(module: Module) {
                      val mapper = JsonMapper.builder()
                          .addModule(module)
                          .disable(SerializationFeature.INDENT_OUTPUT)
                          .build()
                  }
                  """
              )
            );
        }

        @Test
        void yamlMapperSettersInInitBlock() {
            rewriteRun(
              spec -> spec.parser(KotlinParser.builder()
                .classpathFromResources(new InMemoryExecutionContext(),
                  "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                  "jackson-dataformat-yaml-2")),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

                  class Config(module: Module) {
                      val mapper = YAMLMapper()

                      init {
                          mapper.registerModule(module)
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

                  class Config(module: Module) {
                      val mapper = YAMLMapper.builder()
                          .addModule(module)
                          .build()
                  }
                  """
              )
            );
        }
    }
}
