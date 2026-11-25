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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeQualityValidation.ParserTypes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class CodehausToFasterXMLTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.jackson.CodehausToFasterXML")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-asl",
            "jackson-mapper-asl",
            "jackson-xc",
            "jackson-jaxrs-1",
            "jackson-smile",
            "jackson-mrbean",
            "jackson-annotations-2",
            "jackson-core-2",
            "jackson-databind-2",
            "jackson-jaxrs-base",
            "jackson-jaxrs-json-provider",
            "jackson-module-jaxb-annotations",
            "jackson-dataformat-smile-2",
            "jackson-module-mrbean",
            "jackson-datatype-joda",
            "joda-time"));
    }

    @DocumentExample
    @Test
    void onlyJsonSerializeInclusion() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.annotate.JsonSerialize;

              class Test {
                  private static ObjectMapper initializeObjectMapper() {
                      ObjectMapper mapper = new ObjectMapper();
                      return mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  private static ObjectMapper initializeObjectMapper() {
                      ObjectMapper mapper = new ObjectMapper();
                      return mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                  }
              }
              """
          )
        );
    }

    @Test
    void serializationConfigEnums() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.DeserializationConfig;
              import org.codehaus.jackson.map.ObjectMapper;

              import static org.codehaus.jackson.map.SerializationConfig.Feature.WRAP_ROOT_VALUE;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(WRAP_ROOT_VALUE, true);
                      mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.SerializationFeature.WRAP_ROOT_VALUE;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(WRAP_ROOT_VALUE, true);
                      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonNodeAccessors() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.JsonNode;
              import org.codehaus.jackson.node.ObjectNode;

              import java.util.Iterator;

              class Test {
                  String read(JsonNode node, ObjectNode object) {
                      Iterator<String> names = object.getFieldNames();
                      int count = node.get("count").getIntValue() + node.path("total").getValueAsInt(0);
                      return node.get("name").getTextValue() + node.getPath("id").getValueAsText() + names.next() + count;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.node.ObjectNode;

              import java.util.Iterator;

              class Test {
                  String read(JsonNode node, ObjectNode object) {
                      Iterator<String> names = object.fieldNames();
                      int count = node.get("count").intValue() + node.path("total").asInt(0);
                      return node.get("name").textValue() + node.path("id").asText() + names.next() + count;
                  }
              }
              """
          )
        );
    }

    @Test
    void streamingAndMapperTypes() {
        rewriteRun(
          // the calls keep the exceptions their Jackson 1 methods declare, where a parse against Jackson 2 gives that method's own
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
          //language=java
          java(
            """
              import org.codehaus.jackson.JsonFactory;
              import org.codehaus.jackson.JsonParser;
              import org.codehaus.jackson.annotate.JsonAutoDetect;
              import org.codehaus.jackson.annotate.JsonMethod;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.annotate.JsonView;
              import org.codehaus.jackson.type.JavaType;
              import org.codehaus.jackson.type.TypeReference;

              import java.util.List;

              class Test {
                  @JsonView(Test.class)
                  String name;

                  void configure(ObjectMapper mapper) throws Exception {
                      mapper.setVisibility(JsonMethod.FIELD, JsonAutoDetect.Visibility.ANY);
                      JsonFactory factory = mapper.getJsonFactory();
                      factory.enableParserFeature(JsonParser.Feature.ALLOW_COMMENTS);
                      JsonParser parser = factory.createJsonParser("{}");
                      parser.disableFeature(JsonParser.Feature.AUTO_CLOSE_SOURCE);
                      String json = mapper.defaultPrettyPrintingWriter().writeValueAsString(name);
                      JavaType type = mapper.getTypeFactory().constructType(String.class);
                      TypeReference<List<String>> reference = new TypeReference<List<String>>() {
                      };
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.JsonView;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.ObjectMapper;

              import java.util.List;

              class Test {
                  @JsonView(Test.class)
                  String name;

                  void configure(ObjectMapper mapper) throws Exception {
                      mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                      JsonFactory factory = mapper.getFactory();
                      factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
                      JsonParser parser = factory.createJsonParser("{}");
                      parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
                      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(name);
                      JavaType type = mapper.getTypeFactory().constructType(String.class);
                      TypeReference<List<String>> reference = new TypeReference<List<String>>() {
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void relocatedSerializerBaseClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.deser.StdDeserializer;
              import org.codehaus.jackson.map.ser.std.SerializerBase;

              class Money {
                  abstract static class Serializer extends SerializerBase<Money> {
                      Serializer() {
                          super(Money.class);
                      }
                  }

                  abstract static class Deserializer extends StdDeserializer<Money> {
                      Deserializer() {
                          super(Money.class);
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
              import com.fasterxml.jackson.databind.ser.std.StdSerializer;

              class Money {
                  abstract static class Serializer extends StdSerializer<Money> {
                      Serializer() {
                          super(Money.class);
                      }
                  }

                  abstract static class Deserializer extends StdDeserializer<Money> {
                      Deserializer() {
                          super(Money.class);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedTypesRelocatedOutOfTheirOuterType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.deser.std.StdDeserializer;
              import org.codehaus.jackson.map.ser.StdSerializers;

              class Test {
                  StdDeserializer.BigDecimalDeserializer deserializer = new StdDeserializer.BigDecimalDeserializer();
                  StdSerializers.BooleanSerializer serializer = new StdSerializers.BooleanSerializer(true);
              }
              """,
            """
              import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
              import com.fasterxml.jackson.databind.ser.std.BooleanSerializer;

              class Test {
                  NumberDeserializers.BigDecimalDeserializer deserializer = new NumberDeserializers.BigDecimalDeserializer();
                  BooleanSerializer serializer = new BooleanSerializer(true);
              }
              """
          )
        );
    }

    @Test
    void wildcardImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.*;

              class Test {
                  ObjectMapper mapper;
                  JsonSerializer<String> serializer;
                  TypeSerializer typeSerializer;
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

              class Test {
                  ObjectMapper mapper;
                  JsonSerializer<String> serializer;
                  TypeSerializer typeSerializer;
              }
              """
          )
        );
    }

    @Test
    void everyTargetTypeExistsInJackson2() throws Exception {
        assertTargetTypesExist("org.openrewrite.java.jackson.CodehausClassesToFasterXML", 300,
          "jackson-annotations-2", "jackson-core-2", "jackson-databind-2", "jackson-module-jaxb-annotations");
    }

    @Test
    void everyCompanionModuleTargetTypeExistsInJackson2() throws Exception {
        assertTargetTypesExist("org.openrewrite.java.jackson.CodehausModulesToFasterXML", 25,
          "jackson-jaxrs-base", "jackson-jaxrs-json-provider", "jackson-module-jaxb-annotations",
          "jackson-dataformat-smile-2", "jackson-module-mrbean");
    }

    @Test
    void everyJodaTargetTypeExistsInJackson2() throws Exception {
        assertTargetTypesExist("org.openrewrite.java.jackson.codehaus.MigrateJodaSerializers", 9,
          "jackson-datatype-joda");
    }

    private static void assertTargetTypesExist(String recipe, int atLeast, String... artifacts) throws Exception {
        List<String> types = targetTypes(RewriteTest.fromRuntimeClasspath(recipe)).toList();
        assertThat(types).hasSizeGreaterThanOrEqualTo(atLeast);
        List<Path> jars = JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), artifacts);
        URL[] jackson2 = new URL[jars.size()];
        for (var i = 0; i < jars.size(); i++) {
            jackson2[i] = jars.get(i).toUri().toURL();
        }
        try (var loader = new URLClassLoader(jackson2, null)) {
            assertThat(types).filteredOn(type -> loader.getResource(type.replace('.', '/') + ".class") == null).isEmpty();
        }
    }

    private static Stream<String> targetTypes(Recipe recipe) {
        if (recipe instanceof Recipe.DelegatingRecipe delegatingRecipe) {
            return targetTypes(delegatingRecipe.getDelegate());
        }
        return recipe instanceof ChangeType changeType ?
          Stream.of(changeType.getNewFullyQualifiedTypeName()) :
          recipe.getRecipeList().stream().flatMap(CodehausToFasterXMLTest::targetTypes);
    }

    @Test
    void companionModuleTypes() {
        rewriteRun(
          // the ObjectMapper move keeps the void that Jackson 1's registerModule returns, where Jackson 2's returns the mapper
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build()),
          //language=java
          java(
            """
              import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
              import org.codehaus.jackson.jaxrs.JsonMappingExceptionMapper;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.mrbean.MrBeanModule;
              import org.codehaus.jackson.smile.SmileFactory;
              import org.codehaus.jackson.smile.SmileGenerator;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new MrBeanModule());
                      JacksonJsonProvider provider = new JacksonJsonProvider(mapper);
                      Class<?> mapperClass = JsonMappingExceptionMapper.class;
                      SmileFactory smile = new SmileFactory();
                      smile.configure(SmileGenerator.Feature.WRITE_HEADER, true);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.dataformat.smile.SmileFactory;
              import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
              import com.fasterxml.jackson.jaxrs.base.JsonMappingExceptionMapper;
              import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
              import com.fasterxml.jackson.module.mrbean.MrBeanModule;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new MrBeanModule());
                      JacksonJsonProvider provider = new JacksonJsonProvider(mapper);
                      Class<?> mapperClass = JsonMappingExceptionMapper.class;
                      SmileFactory smile = new SmileFactory();
                      smile.configure(SmileGenerator.Feature.WRITE_HEADER, true);
                  }
              }
              """
          )
        );
    }

    @Test
    void jodaSerializersInAnnotations() {
        rewriteRun(
          // the annotation attributes keep the type arguments of the Jackson 1 JsonSerializer and JsonDeserializer
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
            .parserTypedTrees(ParserTypes.Erasure).build()),
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonDeserialize;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.ext.JodaDeserializers.LocalDateDeserializer;
              import org.codehaus.jackson.map.ext.JodaSerializers.DateTimeSerializer;
              import org.codehaus.jackson.map.ext.JodaSerializers.LocalDateSerializer;
              import org.joda.time.DateTime;
              import org.joda.time.LocalDate;

              class Classified {
                  @JsonSerialize(using = DateTimeSerializer.class)
                  private DateTime created;

                  @JsonSerialize(using = LocalDateSerializer.class)
                  @JsonDeserialize(using = LocalDateDeserializer.class)
                  private LocalDate day;
              }
              """,
            """
              import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import com.fasterxml.jackson.datatype.joda.deser.LocalDateDeserializer;
              import com.fasterxml.jackson.datatype.joda.ser.DateTimeSerializer;
              import com.fasterxml.jackson.datatype.joda.ser.LocalDateSerializer;
              import org.joda.time.DateTime;
              import org.joda.time.LocalDate;

              class Classified {
                  @JsonSerialize(using = DateTimeSerializer.class)
                  private DateTime created;

                  @JsonSerialize(using = LocalDateSerializer.class)
                  @JsonDeserialize(using = LocalDateDeserializer.class)
                  private LocalDate day;
              }
              """
          )
        );
    }

    @Test
    void customSerializerFactoryToSimpleModule() {
        rewriteRun(
          // the ObjectMapper move keeps the void that Jackson 1's registerModule returns, and the calls keep the exceptions
          // their Jackson 1 methods declare
          spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build()),
          //language=java
          java(
            """
              import java.io.IOException;
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
                              gen.writeNumber(value.getTime());
                          }
                      });
                      this.setSerializerFactory(factory);
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.util.Date;

              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.Version;
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializerProvider;
              import com.fasterxml.jackson.databind.module.SimpleModule;

              class CustomObjectMapper extends ObjectMapper {
                  CustomObjectMapper() {
                      SimpleModule factory = new SimpleModule("CustomObjectMapper", Version.unknownVersion());
                      factory.addSerializer(Date.class, new JsonSerializer<Date>() {
                          @Override
                          public void serialize(Date value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                              gen.writeNumber(value.getTime());
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
    void springXmlBeanClass() {
        rewriteRun(
          //language=xml
          xml(
            """
              <beans xsi:schemaLocation="www.springframework.org/schema/beans">
                  <bean id="objectMapper" class="org.codehaus.jackson.map.ObjectMapper"/>
                  <bean id="jsonFactory" class="org.codehaus.jackson.JsonFactory"/>
              </beans>
              """,
            """
              <beans xsi:schemaLocation="www.springframework.org/schema/beans">
                  <bean id="objectMapper" class="com.fasterxml.jackson.databind.ObjectMapper"/>
                  <bean id="jsonFactory" class="com.fasterxml.jackson.core.JsonFactory"/>
              </beans>
              """
          )
        );
    }

    @Test
    void typesWithoutJackson2CounterpartKeepTheirNamesAndAreMarked() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.ext.JodaSerializers;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class Test {
                  ObjectMapper mapper = new ObjectMapper();
                  CustomSerializerFactory factory = new CustomSerializerFactory();
                  Class<?> serializers = JodaSerializers.class;
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.codehaus.jackson.map.ext.JodaSerializers;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class Test {
                  ObjectMapper mapper = new ObjectMapper();
                  /*~~(Jackson 2 has no CustomSerializerFactory: add the serializers to a com.fasterxml.jackson.databind.module.SimpleModule with addSerializer and register it with ObjectMapper.registerModule)~~>*/CustomSerializerFactory factory = new /*~~(Jackson 2 has no CustomSerializerFactory: add the serializers to a com.fasterxml.jackson.databind.module.SimpleModule with addSerializer and register it with ObjectMapper.registerModule)~~>*/CustomSerializerFactory();
                  Class<?> serializers = /*~~(Jackson 2 handles Joda types in jackson-datatype-joda: register com.fasterxml.jackson.datatype.joda.JodaModule with ObjectMapper.registerModule, or name its serializers in com.fasterxml.jackson.datatype.joda.ser and .deser)~~>*/JodaSerializers.class;
              }
              """
          )
        );
    }

    @Nested
    class ClassAnnotations {
        @Test
        void retainOriginalAnnotationToo() {
            rewriteRun(
              // the `using` argument keeps the codehaus attribute's `Class<? extends JsonSerializer<?>>`, where the fasterxml one bounds `Class` by the raw `JsonSerializer`
              spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
                .parserTypedTrees(ParserTypes.Erasure).build()),
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.JsonSerializer.None;
                  import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

                  @JsonSerialize(include = NON_NULL, using = None.class)
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.JsonSerializer.None;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void retainOtherAnnotationArguments() {
            rewriteRun(
              // the `using` argument keeps the codehaus attribute's `Class<? extends JsonSerializer<?>>`, where the fasterxml one bounds `Class` by the raw `JsonSerializer`
              spec -> spec.recipeQualityValidation(RecipeQualityValidation.builder()
                .parserTypedTrees(ParserTypes.Erasure).build()),
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.JsonSerializer.None;

                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.JsonSerializer.None;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void doublyAnnotatedCodehausIncludeExtractedToJsonInclude() {
            // Both codehaus and fasterxml `@JsonSerialize(include = NON_NULL)` on the same target.
            // The codehaus side's `include` is lifted into `@JsonInclude(NON_NULL)`, the now-empty
            // codehaus annotation is removed, and the fasterxml `@JsonSerialize(include = ...)`
            // is left for the Jackson 2-3 chain's `JsonSerializeIncludeToJsonInclude` to handle.
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @org.codehaus.jackson.map.annotate.JsonSerialize(include = org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize(include = com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion.NON_NULL)
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void doublyAnnotatedCodehausUsingTransferredBeforeRemove() {
            // Regression for the previous chain order: `RemoveDoublyAnnotatedCodehausAnnotations`
            // ran before `TransferJsonSerializeArgumentsFromCodehausToFasterXML`, which dropped the
            // codehaus annotation before its `using = ...` could be moved to the fasterxml side.
            // The reorder runs the transfer first.
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.JsonSerializer.None;

                  @org.codehaus.jackson.map.annotate.JsonSerialize(using = org.codehaus.jackson.map.JsonSerializer.None.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.JsonSerializer;
                  import com.fasterxml.jackson.databind.JsonSerializer.None;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @JsonSerialize(using = JsonSerializer.None.class)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void doublyAnnotatedCodehausAsAndTypingTransferredBeforeRemove() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.codehaus.jackson.map.annotate.JsonSerialize.Typing.STATIC;

                  @org.codehaus.jackson.map.annotate.JsonSerialize(as = String.class, keyAs = String.class, contentAs = String.class, typing = STATIC)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  import static com.fasterxml.jackson.databind.annotation.JsonSerialize.Typing.STATIC;

                  @JsonSerialize(as = String.class, keyAs = String.class, contentAs = String.class, typing = STATIC)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void doublyAnnotatedCodehausNoClassNotTransferred() {
            rewriteRun(
              //language=java
              java(
                """
                  @org.codehaus.jackson.map.annotate.JsonSerialize(as = org.codehaus.jackson.map.annotate.NoClass.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;

                  @JsonSerialize
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void staticImport() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

                  @JsonSerialize(include = NON_NULL)
                  class StaticImport {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;

                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  class StaticImport {
                  }
                  """
              )
            );
        }
    }

    @Test
    void replaceJsonValueAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.annotate.JsonValue;

              public enum TypeEnumWithValue {
                  TYPE1(1, "Type A"), TYPE2(2, "Type 2");

                  private Integer id;
                  private String name;

                  public TypeEnumWithValue(Integer id, String name) {
                      this.id = id;
                      this.name = name;
                  }

                  @JsonValue
                  public String getName() {
                      return name;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonValue;

              public enum TypeEnumWithValue {
                  TYPE1(1, "Type A"), TYPE2(2, "Type 2");

                  private Integer id;
                  private String name;

                  public TypeEnumWithValue(Integer id, String name) {
                      this.id = id;
                      this.name = name;
                  }

                  @JsonValue
                  public String getName() {
                      return name;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/17")
    @Nested
    class AnnotationIntrospector {

        @Test
        void replaceWithSetConfigCallGeneric() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.AnnotationIntrospector;
                  import org.codehaus.jackson.map.ObjectMapper;

                  class Test {
                      void method(ObjectMapper mapper, AnnotationIntrospector introspector) {
                          mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.AnnotationIntrospector;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void method(ObjectMapper mapper, AnnotationIntrospector introspector) {
                          mapper.setConfig(mapper.getSerializationConfig().with(introspector));
                      }
                  }
                  """
              )
            );
        }

        @CsvSource(textBlock = """
          org.codehaus.jackson.xc.JaxbAnnotationIntrospector, com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector
          org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector, com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
          org.codehaus.jackson.map.introspect.NopAnnotationIntrospector, com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
          org.codehaus.jackson.map.AnnotationIntrospector.Pair, com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair
          """)
        @ParameterizedTest
        void replaceWithSetConfigCallJaxB(String codehausClass, String fasterXmlClass) {
            rewriteRun(
              spec -> {
                  if (fasterXmlClass.contains(".jaxb.")) {
                      // without the jaxb module on the classpath, the parser picks other overloads than the recipe types the calls with
                      spec.recipeQualityValidation(RecipeQualityValidation.builder().parserTypedTrees(false).build());
                  }
              },
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.ObjectMapper;
                  import %s;

                  class Test {
                      void method(ObjectMapper mapper, %s introspector) {
                          mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
                      }
                  }
                  """.formatted(codehausClass, codehausClass.substring(codehausClass.lastIndexOf('.') + 1)),
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import %s;

                  class Test {
                      void method(ObjectMapper mapper, %s introspector) {
                          mapper.setConfig(mapper.getSerializationConfig().with(introspector));
                      }
                  }
                  """.formatted(fasterXmlClass, fasterXmlClass.substring(fasterXmlClass.lastIndexOf('.') + 1))
              )
            );
        }
    }
}
