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
import org.openrewrite.test.TypeValidation;

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
    class JacksonObjectMapperFactory {

        @Test
        void fluentChainOnJacksonObjectMapper() {
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  fun mapper(): ObjectMapper = jacksonObjectMapper()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  fun mapper(): ObjectMapper = jacksonMapperBuilder()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .changeDefaultPropertyInclusion({ incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL)})
                      .build()
                  """
              )
            );
        }

        @Test
        void fluentChainWithApplyBlockAtTail() {
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  fun mapper(): ObjectMapper = jacksonObjectMapper()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .apply {
                          this.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                      }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  fun mapper(): ObjectMapper = jacksonMapperBuilder()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .changeDefaultPropertyInclusion({ incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL)})
                      .build()
                  """
              )
            );
        }

        @Test
        void fluentChainWithApplyBlockImplicitReceiver() {
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  fun mapper(): ObjectMapper = jacksonObjectMapper()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .apply {
                          setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                      }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  fun mapper(): ObjectMapper = jacksonMapperBuilder()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .changeDefaultPropertyInclusion({ incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL)})
                      .build()
                  """
              )
            );
        }

        @Test
        void applyBlockPartiallyExtracted() {
            // When the apply block has recognized setters followed by non-setter statements,
            // the setters at the front are extracted into the builder chain. The remaining
            // apply block (with only non-setter statements like println) is preserved as a
            // suffix with no TODO comment since there are no setter calls left in it.
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  fun mapper(): ObjectMapper = jacksonObjectMapper()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .apply {
                          this.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                          println("configured")
                      }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  fun mapper(): ObjectMapper = jacksonMapperBuilder()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .changeDefaultPropertyInclusion({ incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL)})
                      .build()
                      .apply {
                          println("configured")
                      }
                  """
              )
            );
        }

        @Test
        void applyBlockWithNonSetterFirstGetsGuidanceComment() {
            // When the first statement in the apply block is a non-setter (println), no
            // setters can be extracted (conservative approach). The entire apply block is
            // kept as a suffix and a TODO comment provides per-setter migration guidance.
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  fun mapper(): ObjectMapper = jacksonObjectMapper()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .apply {
                          println("about to configure")
                          this.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                      }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude
                  import com.fasterxml.jackson.databind.DeserializationFeature
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  fun mapper(): ObjectMapper = jacksonMapperBuilder()
                      .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                      .build()
                      // TODO Move these setters into the builder chain above:
                      //   `setDefaultPropertyInclusion` -> `changeDefaultPropertyInclusion { incl -> incl.withValueInclusion(...).withContentInclusion(...) }`
                      // See https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md#6-immutability-of-objectmapper-jsonfactory
                      .apply {
                          println("about to configure")
                          this.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                      }
                  """
              )
            );
        }

        @Test
        void operationalMethodAtChainTailIsKeptAsSuffix() {
            // Same bug class as openrewrite/rewrite-jackson#130, but via the fluent-chain path:
            // a call like writeValueAsString that tails the chain must NOT be folded into the
            // builder. Our chain-split treats unknown-named calls as suffix; this test locks in
            // that behavior alongside main's isSetterReturnType gate for standalone calls.
            rewriteRun(
              spec -> spec
                .parser(KotlinParser.builder()
                  .classpathFromResources(new InMemoryExecutionContext(),
                    "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
                    "jackson-module-kotlin-2"))
                .typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
              //language=kotlin
              kotlin(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

                  class Serializer(private val source: ObjectMapper) {
                      fun serialize(value: Any): String = jacksonObjectMapper()
                          .disable(SerializationFeature.INDENT_OUTPUT)
                          .writeValueAsString(value)
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.ObjectMapper
                  import com.fasterxml.jackson.databind.SerializationFeature
                  import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

                  class Serializer(private val source: ObjectMapper) {
                      fun serialize(value: Any): String = jacksonMapperBuilder()
                          .disable(SerializationFeature.INDENT_OUTPUT)
                          .build()
                          .writeValueAsString(value)
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
