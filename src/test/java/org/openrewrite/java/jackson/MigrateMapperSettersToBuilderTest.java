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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateMapperSettersToBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMapperSettersToBuilder())
          .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
            .classpath("jackson-core", "jackson-databind", "jackson-annotations"));
    }

    @DocumentExample
    @Test
    void allSettersMigratedToBuilder() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.core.Base64Variants;
              import com.fasterxml.jackson.core.PrettyPrinter;
              import com.fasterxml.jackson.databind.AnnotationIntrospector;
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.InjectableValues;
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.Module;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.cfg.CacheProvider;
              import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
              import com.fasterxml.jackson.databind.cfg.ContextAttributes;
              import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
              import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
              import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
              import com.fasterxml.jackson.databind.json.JsonMapper;
              import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
              import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
              import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
              import com.fasterxml.jackson.databind.node.JsonNodeFactory;
              import com.fasterxml.jackson.databind.ser.FilterProvider;
              import com.fasterxml.jackson.databind.ser.SerializerFactory;
              import com.fasterxml.jackson.databind.type.TypeFactory;

              import java.text.SimpleDateFormat;
              import java.util.Locale;
              import java.util.TimeZone;

              class A {
                  JsonMapper create(
                          Module module,
                          DeserializationProblemHandler handler,
                          PolymorphicTypeValidator validator,
                          TypeResolverBuilder<?> typeResolver,
                          FilterProvider filterProvider,
                          SerializerFactory serializerFactory,
                          PrettyPrinter prettyPrinter,
                          InjectableValues injectableValues,
                          ConstructorDetector constructorDetector,
                          CacheProvider cacheProvider,
                          AnnotationIntrospector introspector,
                          SubtypeResolver subtypeResolver,
                          HandlerInstantiator handlerInstantiator,
                          PropertyNamingStrategy namingStrategy,
                          AccessorNamingStrategy.Provider accessorNaming,
                          ContextAttributes attributes
                  ) {
                      JsonMapper mapper = new JsonMapper();
                      mapper.configure(MapperFeature.AUTO_DETECT_FIELDS, true);
                      mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                      mapper.registerModule(module);
                      mapper.registerModules(module);
                      mapper.findAndRegisterModules();
                      mapper.addMixIn(Object.class, Comparable.class);
                      mapper.registerSubtypes(Object.class);
                      mapper.addHandler(handler);
                      mapper.clearProblemHandlers();
                      mapper.activateDefaultTyping(validator);
                      mapper.activateDefaultTypingAsProperty(validator, ObjectMapper.DefaultTyping.NON_FINAL, "@type");
                      mapper.deactivateDefaultTyping();
                      mapper.setDefaultTyping(typeResolver);
                      mapper.setFilterProvider(filterProvider);
                      mapper.setSerializerFactory(serializerFactory);
                      mapper.setDefaultPrettyPrinter(prettyPrinter);
                      mapper.setInjectableValues(injectableValues);
                      mapper.setNodeFactory(JsonNodeFactory.instance);
                      mapper.setConstructorDetector(constructorDetector);
                      mapper.setCacheProvider(cacheProvider);
                      mapper.setAnnotationIntrospector(introspector);
                      mapper.setTypeFactory(TypeFactory.defaultInstance());
                      mapper.setSubtypeResolver(subtypeResolver);
                      mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                      mapper.setHandlerInstantiator(handlerInstantiator);
                      mapper.setPropertyNamingStrategy(namingStrategy);
                      mapper.setAccessorNaming(accessorNaming);
                      mapper.setPolymorphicTypeValidator(validator);
                      mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
                      mapper.setTimeZone(TimeZone.getDefault());
                      mapper.setLocale(Locale.US);
                      mapper.setBase64Variant(Base64Variants.MIME);
                      mapper.setDefaultAttributes(attributes);
                      mapper.setDefaultPropertyInclusion(JsonInclude.Value.empty());
                      mapper.setDefaultSetterInfo(JsonSetter.Value.empty());
                      mapper.setDefaultMergeable(Boolean.TRUE);
                      mapper.setDefaultLeniency(Boolean.TRUE);
                      return mapper;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.core.Base64Variants;
              import com.fasterxml.jackson.core.PrettyPrinter;
              import com.fasterxml.jackson.databind.AnnotationIntrospector;
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.InjectableValues;
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.Module;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.PropertyNamingStrategy;
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.cfg.CacheProvider;
              import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
              import com.fasterxml.jackson.databind.cfg.ContextAttributes;
              import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
              import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
              import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
              import com.fasterxml.jackson.databind.json.JsonMapper;
              import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
              import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
              import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
              import com.fasterxml.jackson.databind.node.JsonNodeFactory;
              import com.fasterxml.jackson.databind.ser.FilterProvider;
              import com.fasterxml.jackson.databind.ser.SerializerFactory;
              import com.fasterxml.jackson.databind.type.TypeFactory;

              import java.text.SimpleDateFormat;
              import java.util.Locale;
              import java.util.TimeZone;

              class A {
                  JsonMapper create(
                          Module module,
                          DeserializationProblemHandler handler,
                          PolymorphicTypeValidator validator,
                          TypeResolverBuilder<?> typeResolver,
                          FilterProvider filterProvider,
                          SerializerFactory serializerFactory,
                          PrettyPrinter prettyPrinter,
                          InjectableValues injectableValues,
                          ConstructorDetector constructorDetector,
                          CacheProvider cacheProvider,
                          AnnotationIntrospector introspector,
                          SubtypeResolver subtypeResolver,
                          HandlerInstantiator handlerInstantiator,
                          PropertyNamingStrategy namingStrategy,
                          AccessorNamingStrategy.Provider accessorNaming,
                          ContextAttributes attributes
                  ) {
                      return JsonMapper.builder()
                              .configure(MapperFeature.AUTO_DETECT_FIELDS, true)
                              .disable(SerializationFeature.INDENT_OUTPUT)
                              .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                              .addModule(module)
                              .addModules(module)
                              .findAndAddModules()
                              .addMixIn(Object.class, Comparable.class)
                              .registerSubtypes(Object.class)
                              .addHandler(handler)
                              .clearProblemHandlers()
                              .activateDefaultTyping(validator)
                              .activateDefaultTypingAsProperty(validator, ObjectMapper.DefaultTyping.NON_FINAL, "@type")
                              .deactivateDefaultTyping()
                              .setDefaultTyping(typeResolver)
                              .filterProvider(filterProvider)
                              .serializerFactory(serializerFactory)
                              .defaultPrettyPrinter(prettyPrinter)
                              .injectableValues(injectableValues)
                              .nodeFactory(JsonNodeFactory.instance)
                              .constructorDetector(constructorDetector)
                              .cacheProvider(cacheProvider)
                              .annotationIntrospector(introspector)
                              .typeFactory(TypeFactory.defaultInstance())
                              .subtypeResolver(subtypeResolver)
                              .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                              .handlerInstantiator(handlerInstantiator)
                              .propertyNamingStrategy(namingStrategy)
                              .accessorNaming(accessorNaming)
                              .polymorphicTypeValidator(validator)
                              .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
                              .defaultTimeZone(TimeZone.getDefault())
                              .defaultLocale(Locale.US)
                              .defaultBase64Variant(Base64Variants.MIME)
                              .defaultAttributes(attributes)
                              .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.empty())
                              .defaultSetterInfo(JsonSetter.Value.empty())
                              .defaultMergeable(Boolean.TRUE)
                              .defaultLeniency(Boolean.TRUE)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void singleDisable() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class A {
                  JsonMapper create() {
                      JsonMapper mapper = new JsonMapper();
                      mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      return mapper;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class A {
                  JsonMapper create() {
                      return JsonMapper.builder()
                              .disable(SerializationFeature.INDENT_OUTPUT)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Nested
    class BuilderMigration {

        @Test
        void multipleSetters() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void registerModuleRenamedToAddModule() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create(Module module) {
                          JsonMapper mapper = new JsonMapper();
                          mapper.registerModule(module);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create(Module module) {
                          return JsonMapper.builder()
                                  .addModule(module)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void setDateFormatRenamedToDefaultDateFormat() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.text.SimpleDateFormat;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.text.SimpleDateFormat;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"))
                                  .build();
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class CommentPreservation {

        @Test
        void commentsOnSettersPreserved() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          // Disable indentation
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          // Fail on unknown
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  // Disable indentation
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  // Fail on unknown
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void blockCommentsOnSettersPreserved() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          /* Disable indentation */
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          /* Fail on unknown */
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  /* Disable indentation */
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  /* Fail on unknown */
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void commentsPreservedOnFieldAssignment() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = new JsonMapper();
                          // Disable indentation
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          // Fail on unknown
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = JsonMapper.builder()
                                  // Disable indentation
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  // Fail on unknown
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainCommentsPreserved() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return new JsonMapper()
                                  // Disable indentation
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  // Fail on unknown
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  // Disable indentation
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  // Fail on unknown
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
        void mapperPassedToOtherMethod() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      void configure() {
                          JsonMapper mapper = new JsonMapper();
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          doSomething(mapper);
                      }
                      void doSomething(JsonMapper mapper) {}
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      void configure() {
                          JsonMapper mapper = JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .build();
                          doSomething(mapper);
                      }
                      void doSomething(JsonMapper mapper) {}
                  }
                  """
              )
            );
        }

        @Test
        void mapperFromParameter() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      void configure(JsonMapper mapper) {
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      void configure(JsonMapper mapper) {
                          // TODO disable could not be folded to the builder of JsonMapper. Use mapper.rebuild().disable(...).build() or move to the mapper's instantiation site.
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fieldAssignedInMethod() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = new JsonMapper();
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }


        @Test
        void setDateFormatWithAssignment() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.text.SimpleDateFormat;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                          mapper.setDateFormat(dateFormat);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.text.SimpleDateFormat;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                          // TODO setDateFormat could not be folded to the builder of JsonMapper. Use mapper.rebuild().defaultDateFormat(...).build() or move to the mapper's instantiation site.
                          mapper.setDateFormat(dateFormat);
                          return mapper;
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class FluentChain {

        @Test
        void fluentChainReturnStatement() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return new JsonMapper()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainFieldInitializer() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper = new JsonMapper()
                              .disable(SerializationFeature.INDENT_OUTPUT);
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper = JsonMapper.builder()
                              .disable(SerializationFeature.INDENT_OUTPUT)
                              .build();
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainWithSetTimeZone() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.util.TimeZone;

                  class A {
                      JsonMapper create() {
                          return new JsonMapper()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .setTimeZone(TimeZone.getDefault());
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.util.TimeZone;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .defaultTimeZone(TimeZone.getDefault())
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainSetDefaultPropertyInclusionWithRawInclude() {
            rewriteRun(
              spec -> spec
                .recipes(new MigrateMapperSettersToBuilder(), new UpdateSerializationInclusionConfiguration()),
              java(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.util.TimeZone;

                  class A {
                      JsonMapper create() {
                          return new JsonMapper()
                                  .setTimeZone(TimeZone.getDefault())
                                  .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  import java.util.TimeZone;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .defaultTimeZone(TimeZone.getDefault())
                                  .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL))
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainWithTrailingNonSetterMethod() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      String create() {
                          return new JsonMapper()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .writeValueAsString("test");
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      String create() {
                          return JsonMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .build()
                                  .writeValueAsString("test");
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class FormatAlignedMappers {

        private static final String YAML_MAPPER_STUB = """
                package com.fasterxml.jackson.dataformat.yaml;
                public class YAMLMapper extends com.fasterxml.jackson.databind.ObjectMapper {
                    public YAMLMapper() {}
                    public static com.fasterxml.jackson.databind.json.JsonMapper.Builder builder() { return null; }
                }
                """;

        @Test
        void yamlMapperSettersMigratedToBuilder() {
            rewriteRun(
              spec -> spec.parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                .classpath("jackson-core", "jackson-databind")
                .dependsOn(YAML_MAPPER_STUB)),
              java(
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

                  class A {
                      YAMLMapper create(Module module) {
                          YAMLMapper mapper = new YAMLMapper();
                          mapper.registerModule(module);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

                  class A {
                      YAMLMapper create(Module module) {
                          return YAMLMapper.builder()
                                  .addModule(module)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void yamlMapperFluentChain() {
            rewriteRun(
              spec -> spec.parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                .classpath("jackson-core", "jackson-databind")
                .dependsOn(YAML_MAPPER_STUB)),
              java(
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

                  class A {
                      YAMLMapper create() {
                          return new YAMLMapper()
                                  .disable(SerializationFeature.INDENT_OUTPUT);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

                  class A {
                      YAMLMapper create() {
                          return YAMLMapper.builder()
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void xmlMapperSettersMigratedToBuilder() {
            rewriteRun(
              spec -> spec.parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                .classpath("jackson-core", "jackson-databind")
                .dependsOn("""
                    package com.fasterxml.jackson.dataformat.xml;
                    public class XmlMapper extends com.fasterxml.jackson.databind.ObjectMapper {
                        public XmlMapper() {}
                        public static com.fasterxml.jackson.databind.json.JsonMapper.Builder builder() { return null; }
                    }
                    """)),
              java(
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.dataformat.xml.XmlMapper;

                  class A {
                      XmlMapper create(Module module) {
                          XmlMapper mapper = new XmlMapper();
                          mapper.registerModule(module);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.dataformat.xml.XmlMapper;

                  class A {
                      XmlMapper create(Module module) {
                          return XmlMapper.builder()
                                  .addModule(module)
                                  .build();
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class MixedFluentAndSetters {

        @Test
        void fluentChainAssignedToVariableFollowedBySetters() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.MapperFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
                          mapper.enable(SerializationFeature.INDENT_OUTPUT);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.MapperFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                                  .enable(SerializationFeature.INDENT_OUTPUT)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainAssignedToVariableFollowedByMultipleSetters() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create(Module module) {
                          JsonMapper mapper = new JsonMapper()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          mapper.registerModule(module);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.Module;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create(Module module) {
                          return JsonMapper.builder()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .addModule(module)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainAssignedToFieldFollowedBySetters() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = new JsonMapper()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper mapper;

                      void configure() {
                          mapper = JsonMapper.builder()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .build();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainFollowedBySetterThenUsedElsewhere() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          doSomething(mapper);
                          return mapper;
                      }
                      void doSomething(JsonMapper mapper) {}
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = JsonMapper.builder()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .build();
                          doSomething(mapper);
                          return mapper;
                      }
                      void doSomething(JsonMapper mapper) {}
                  }
                  """
              )
            );
        }

        @Test
        void fluentChainFollowedBySetterThenUnknownMethod() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.SerializationFeature;
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          return JsonMapper.builder()
                                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                  .disable(SerializationFeature.INDENT_OUTPUT)
                                  .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL))
                                  .build();
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class NonSetterMethodsNotFolded {

        @Issue("https://github.com/moderneinc/customer-requests/issues/2225")
        @Test
        void readValueNotFoldedIntoBuilder() {
            rewriteRun(
              java(
                """
                  import java.io.InputStream;
                  import java.util.List;
                  import com.fasterxml.jackson.databind.PropertyNamingStrategies;
                  import com.fasterxml.jackson.databind.json.JsonMapper;
                  import com.fasterxml.jackson.databind.Module;

                  class A {
                      List<Object> read(InputStream inputStream, Module module) throws Exception {
                          JsonMapper mapper = new JsonMapper();
                          mapper.registerModule(module);
                          mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                          List<Object> l = mapper.readValue(inputStream, List.class);
                          return l;
                      }
                  }
                  """,
                """
                  import java.io.InputStream;
                  import java.util.List;
                  import com.fasterxml.jackson.databind.PropertyNamingStrategies;
                  import com.fasterxml.jackson.databind.json.JsonMapper;
                  import com.fasterxml.jackson.databind.Module;

                  class A {
                      List<Object> read(InputStream inputStream, Module module) throws Exception {
                          JsonMapper mapper = JsonMapper.builder()
                                  .addModule(module)
                                  .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                  .build();
                          return mapper.readValue(inputStream, List.class);
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-jackson/issues/130")
        @Test
        void writeValueAsStringNotFoldedIntoBuilder() {
            rewriteRun(
              java(
                """
                  import java.util.Map;
                  import com.fasterxml.jackson.core.JsonProcessingException;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class A {
                      String convert(Map<String, Object> customPropertyMap) {
                          ObjectMapper mapper = new ObjectMapper();
                          String customProperties;
                          try {
                              customProperties = mapper.writeValueAsString(customPropertyMap);
                          } catch (JsonProcessingException ex) {
                              throw new RuntimeException(ex);
                          }
                          return customProperties;
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class NoChange {

        @Test
        void noSetterCalls() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      JsonMapper create() {
                          JsonMapper mapper = new JsonMapper();
                          return mapper;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void nonMapperMethodInvocation() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.json.JsonMapper;

                  class A {
                      String test(JsonMapper mapper) {
                          String s = "hello";
                          s.toUpperCase();
                          return s;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void objectMapperNotAffected() {
            rewriteRun(
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class A {
                      ObjectMapper create() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(SerializationFeature.INDENT_OUTPUT);
                          return mapper;
                      }
                  }
                  """
              )
            );
        }
    }
}
