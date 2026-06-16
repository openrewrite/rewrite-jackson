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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * The recipe's structural job is independent of feature-constant renames — those happen in a
 * separate pipeline pass before this recipe runs. To keep these unit tests self-contained, they
 * use setters that take non-feature arguments ({@code setCharacterEscapes},
 * {@code setRootValueSeparator}). The full feature-constant-bearing flow is covered end-to-end
 * in {@code Jackson3TypeChangesTest}.
 */
class MigrateFactorySettersToBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFactorySettersToBuilder())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
              "jackson-dataformat-avro-2", "jackson-dataformat-cbor-2",
              "jackson-dataformat-csv-2", "jackson-dataformat-smile-2",
              "jackson-dataformat-xml-2", "jackson-dataformat-yaml-2"));
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
              import com.fasterxml.jackson.core.JsonFactoryBuilder;

              class A {
                  JsonFactory factory = new JsonFactoryBuilder()
                          .build();
              }
              """
          )
        );
    }

    @Test
    void singleSetterMigratesToBuilder() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.io.CharacterEscapes;

              class A {
                  JsonFactory create(CharacterEscapes escapes) {
                      JsonFactory factory = new JsonFactory();
                      factory.setCharacterEscapes(escapes);
                      return factory;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.core.io.CharacterEscapes;

              class A {
                  JsonFactory create(CharacterEscapes escapes) {
                      JsonFactory factory = new JsonFactoryBuilder()
                              .characterEscapes(escapes)
                              .build();
                      return factory;
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleSettersMigrateToBuilder() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.io.CharacterEscapes;

              class A {
                  JsonFactory create(CharacterEscapes escapes) {
                      JsonFactory factory = new JsonFactory();
                      factory.setCharacterEscapes(escapes);
                      factory.setRootValueSeparator(",");
                      return factory;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.core.io.CharacterEscapes;

              class A {
                  JsonFactory create(CharacterEscapes escapes) {
                      JsonFactory factory = new JsonFactoryBuilder()
                              .characterEscapes(escapes)
                              .rootValueSeparator(",")
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
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      JsonFactory create(CharacterEscapes escapes) {
                          return new JsonFactory()
                                  .setCharacterEscapes(escapes);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonFactoryBuilder;
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      JsonFactory create(CharacterEscapes escapes) {
                          return new JsonFactoryBuilder()
                                  .characterEscapes(escapes)
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
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      void configure(JsonFactory factory, CharacterEscapes escapes) {
                          factory.setCharacterEscapes(escapes);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      void configure(JsonFactory factory, CharacterEscapes escapes) {
                          // TODO setCharacterEscapes could not be folded to the builder of JsonFactory. Use factory.rebuild().characterEscapes(...).build() or move to the factory's instantiation site.
                          factory.setCharacterEscapes(escapes);
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
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      void configure(CharacterEscapes escapes) {
                          JsonFactory factory = new JsonFactory();
                          factory.setCharacterEscapes(escapes);
                          doSomething(factory);
                      }
                      void doSomething(JsonFactory factory) {}
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.JsonFactory;
                  import com.fasterxml.jackson.core.JsonFactoryBuilder;
                  import com.fasterxml.jackson.core.io.CharacterEscapes;

                  class A {
                      void configure(CharacterEscapes escapes) {
                          JsonFactory factory = new JsonFactoryBuilder()
                                  .characterEscapes(escapes)
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

    /**
     * Format-aligned factories (Avro, CBOR, Csv, Smile, Xml, YAML) migrate via the concrete
     * {@code XFactory.builder()} static, not {@code new XFactoryBuilder()} — the no-arg
     * {@code XFactoryBuilder} constructor is package-protected, so a direct {@code new}
     * call would emit uncompilable code outside its package. IonFactory is omitted from
     * the migration because it has no plain {@code IonFactory.builder()} static.
     * <p>
     * Stream-constraint setters resolve through {@link com.fasterxml.jackson.core.TSFBuilder}
     * inherited methods. {@code characterEscapes} / {@code rootValueSeparator} are only
     * present on {@code JsonFactoryBuilder}, so calls to {@code setCharacterEscapes} /
     * {@code setRootValueSeparator} on a format-aligned factory fall through to the TODO
     * path rather than fold.
     */
    @Nested
    class FormatAlignedFactories {

        @Test
        void yamlBareConstructorMigratedToBuilder() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

                  class A {
                      YAMLFactory factory = new YAMLFactory();
                  }
                  """,
                """
                  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

                  class A {
                      YAMLFactory factory = YAMLFactory.builder()
                              .build();
                  }
                  """
              )
            );
        }

        @Test
        void cborStreamReadConstraintsMigrateToBuilder() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.StreamReadConstraints;
                  import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

                  class A {
                      CBORFactory create(StreamReadConstraints c) {
                          CBORFactory factory = new CBORFactory();
                          factory.setStreamReadConstraints(c);
                          return factory;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.StreamReadConstraints;
                  import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

                  class A {
                      CBORFactory create(StreamReadConstraints c) {
                          CBORFactory factory = CBORFactory.builder()
                                  .streamReadConstraints(c)
                                  .build();
                          return factory;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void xmlFluentChainOnConstructor() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.StreamReadConstraints;
                  import com.fasterxml.jackson.dataformat.xml.XmlFactory;

                  class A {
                      XmlFactory create(StreamReadConstraints c) {
                          return new XmlFactory()
                                  .setStreamReadConstraints(c);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.StreamReadConstraints;
                  import com.fasterxml.jackson.dataformat.xml.XmlFactory;

                  class A {
                      XmlFactory create(StreamReadConstraints c) {
                          return XmlFactory.builder()
                                  .streamReadConstraints(c)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void yamlCharacterEscapesFallsBackToTodoComment() {
            // characterEscapes() is concrete on JsonFactoryBuilder only — YAMLFactoryBuilder
            // doesn't expose it, so we leave the original setter call in place with a TODO.
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.core.io.CharacterEscapes;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

                  class A {
                      YAMLFactory create(CharacterEscapes escapes) {
                          YAMLFactory factory = new YAMLFactory();
                          factory.setCharacterEscapes(escapes);
                          return factory;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.core.io.CharacterEscapes;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

                  class A {
                      YAMLFactory create(CharacterEscapes escapes) {
                          YAMLFactory factory = YAMLFactory.builder()
                                  .build();
                          // TODO setCharacterEscapes could not be folded to the builder of YAMLFactory. Use factory.rebuild().characterEscapes(...).build() or move to the factory's instantiation site.
                          factory.setCharacterEscapes(escapes);
                          return factory;
                      }
                  }
                  """
              )
            );
        }
    }
}
