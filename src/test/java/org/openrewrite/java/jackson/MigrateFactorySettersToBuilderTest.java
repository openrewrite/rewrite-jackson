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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateFactorySettersToBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFactorySettersToBuilder())
          .parser(JavaParser.fromJavaVersion()
            .classpath("jackson-core", "jackson-databind", "jackson-annotations"));
    }

    @DocumentExample
    @Test
    void bareConstructorMigratedToBuilder() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;

              class A {
                  JsonFactory factory = new JsonFactory();
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;

              class A {
                  JsonFactory factory = JsonFactory.builder()
                          .build();
              }
              """
          )
        );
    }

    @Test
    void singleEnable() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonParser;

              class A {
                  JsonFactory create() {
                      JsonFactory factory = new JsonFactory();
                      factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                      return factory;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonParser;

              class A {
                  JsonFactory create() {
                      JsonFactory factory = JsonFactory.builder()
                              .enable(JsonParser.Feature.ALLOW_COMMENTS)
                              .build();
                      return factory;
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleSetters() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.JsonParser;

              class A {
                  JsonFactory create() {
                      JsonFactory factory = new JsonFactory();
                      factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                      factory.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                      return factory;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.JsonParser;

              class A {
                  JsonFactory create() {
                      JsonFactory factory = JsonFactory.builder()
                              .enable(JsonParser.Feature.ALLOW_COMMENTS)
                              .disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES)
                              .build();
                      return factory;
                  }
              }
              """
          )
        );
    }

    @Nested
    class FluentChain {

        @Test
        void fluentChainOnConstructor() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      JsonFactory create() {
                          return new JsonFactory()
                                  .enable(JsonParser.Feature.ALLOW_COMMENTS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      JsonFactory create() {
                          return JsonFactory.builder()
                                  .enable(JsonParser.Feature.ALLOW_COMMENTS)
                                  .build();
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class CommentFallback {

        @Test
        void factoryFromParameter() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      void configure(JsonFactory factory) {
                          factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      void configure(JsonFactory factory) {
                          // TODO enable could not be folded to the builder of JsonFactory. Use factory.rebuild().enable(...).build() or move to the factory's instantiation site.
                          factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void factoryPassedToOtherMethodStillFolded() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      void configure() {
                          JsonFactory factory = new JsonFactory();
                          factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                          doSomething(factory);
                      }
                      void doSomething(JsonFactory factory) {}
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonParser;

                  class A {
                      void configure() {
                          JsonFactory factory = JsonFactory.builder()
                                  .enable(JsonParser.Feature.ALLOW_COMMENTS)
                                  .build();
                          doSomething(factory);
                      }
                      void doSomething(JsonFactory factory) {}
                  }
                  """
              )
            );
        }
    }
}
