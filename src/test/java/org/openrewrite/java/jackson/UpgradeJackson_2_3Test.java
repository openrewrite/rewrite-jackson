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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeJackson_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations",
            "jackson-core",
            "jackson-databind",
            "jackson-datatype-jsr310"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @DocumentExample
    @Test
    void jacksonUpgradeToVersion3() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-annotations</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.module</groupId>
                          <artifactId>jackson-module-parameter-names</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.datatype</groupId>
                          <artifactId>jackson-datatype-jdk8</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.datatype</groupId>
                          <artifactId>jackson-datatype-jsr310</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
                assertThat(versionMatcher.find()).describedAs("Expected 3.1.x in %s", pom).isTrue();
                String jacksonVersion = versionMatcher.group(0);

                //language=xml
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>org.example</groupId>
                      <artifactId>example</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                          <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-annotations</artifactId>
                              <version>2.22</version>
                          </dependency>
                          <dependency>
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                              <version>%s</version>
                          </dependency>
                          <dependency>
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion, jacksonVersion);
            })),
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      JsonFactory factory = new JsonFactoryBuilder().build();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.json.JsonFactory;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      JsonFactory factory = JsonFactory.builder().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_jacksonBomOnly() {
        rewriteRun(
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson</groupId>
                          <artifactId>jackson-bom</artifactId>
                          <version>2.19.0</version>
                          <scope>pom</scope>
                          <type>import</type>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .contains("<groupId>tools.jackson</groupId>")
              .containsPattern("3\\.\\d+\\.\\d+")
              .actual())),
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      JsonFactory factory = new JsonFactoryBuilder().build();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.json.JsonFactory;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      JsonFactory factory = JsonFactory.builder().build();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/3009")
    @Test
    void afterburnerImportAndDependencyNameTheSameArtifact() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),
            "jackson-annotations-2",
            "jackson-core-2",
            "jackson-databind-2",
            "jackson-module-afterburner-2")),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>example</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.module</groupId>
                          <artifactId>jackson-module-afterburner</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> assertThat(pom)
              .doesNotContain("blackbird")
              .contains("<groupId>tools.jackson.module</groupId>")
              .contains("<artifactId>jackson-module-afterburner</artifactId>")
              .containsPattern("3\\.\\d+\\.\\d+")
              .actual())),
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.json.JsonMapper;
              import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

              class Test {
                  JsonMapper create() {
                      return JsonMapper.builder()
                              .addModule(new AfterburnerModule())
                              .build();
                  }
              }
              """,
            """
              import tools.jackson.databind.json.JsonMapper;
              import tools.jackson.module.afterburner.AfterburnerModule;

              class Test {
                  JsonMapper create() {
                      return JsonMapper.builder()
                              .addModule(new AfterburnerModule())
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void mapperSettersMigratedToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class Test {
                  JsonMapper create() {
                      JsonMapper mapper = new JsonMapper();
                      mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      return mapper;
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationFeature;
              import tools.jackson.databind.json.JsonMapper;

              class Test {
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

    @Test
    void objectMapperSettersMigratedToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper create() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(SerializationFeature.INDENT_OUTPUT);
                      return mapper;
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationFeature;
              import tools.jackson.databind.json.JsonMapper;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper create() {
                      return JsonMapper.builder()
                              .disable(SerializationFeature.INDENT_OUTPUT)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/89")
    @Test
    void visibilityAndDefaultTypingMigratedToBuilder() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper create() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
                      objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
                      return objectMapper;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.json.JsonMapper;
              import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
              import tools.jackson.databind.DefaultTyping;

              class Test {
                  ObjectMapper create() {
                      return JsonMapper.builder()
                              .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                              .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(), DefaultTyping.NON_FINAL)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void mutatorsInStaticInitializerFoldedIntoBuilder() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations",
            "jackson-core",
            "jackson-databind",
            "jackson-module-parameter-names")),
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonCreator.Mode;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

              class WebSocketsIT {
                  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

                  static {
                      OBJECT_MAPPER.registerModule(new ParameterNamesModule(Mode.PROPERTIES));
                      OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                      OBJECT_MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.cfg.ConstructorDetector;
              import tools.jackson.databind.json.JsonMapper;

              class WebSocketsIT {
                  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                          .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
                          .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL))
                          .build();
              }
              """
          )
        );
    }

    @Test
    void chainedConfigurationOnNewObjectMapper() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              import java.util.TimeZone;

              class Test {
                  ObjectMapper objectMapper() {
                      return new ObjectMapper()
                              .registerModule(new JavaTimeModule())
                              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                              .setTimeZone(TimeZone.getDefault())
                              .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.json.JsonMapper;

              import java.util.TimeZone;

              class Test {
                  ObjectMapper objectMapper() {
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

    @Issue("https://github.com/moderneinc/customer-requests/issues/1995")
    @Test
    void multiCatchWithRuntimeExceptionAndIOExceptionDoesNotCrash() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileInputStream;
              import java.io.IOException;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void readAndDeserialize() {
                      ObjectMapper mapper = new ObjectMapper();
                      try {
                          byte[] data = new FileInputStream("data.json").readAllBytes();
                          mapper.readValue(data, String.class);
                      } catch (RuntimeException | IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """,
            """
              import java.io.FileInputStream;
              import java.io.IOException;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.json.JsonMapper;

              class Test {
                  void readAndDeserialize() {
                      ObjectMapper mapper = new JsonMapper();
                      try {
                          byte[] data = new FileInputStream("data.json").readAllBytes();
                          mapper.readValue(data, String.class);
                      } catch (RuntimeException | IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/2143")
    @Test
    void standaloneObjectMapperWithSetSerializationInclusion() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper createMapper() {
                      ObjectMapper objectMapper = new ObjectMapper();
                      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                      return objectMapper;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.json.JsonMapper;

              class Test {
                  ObjectMapper createMapper() {
                      return JsonMapper.builder()
                              .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL))
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_java8Only() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class A {
                      ObjectMapper mapper;
                  }
                  """,
                """
                  import tools.jackson.databind.ObjectMapper;

                  class A {
                      ObjectMapper mapper;
                  }
                  """
              )
            ),
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.module</groupId>
                            <artifactId>jackson-module-parameter-names</artifactId>
                            <version>2.19.0</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.datatype</groupId>
                            <artifactId>jackson-datatype-jdk8</artifactId>
                            <version>2.19.0</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.datatype</groupId>
                            <artifactId>jackson-datatype-jsr310</artifactId>
                            <version>2.19.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> assertThat(pom)
                .containsOnlyOnce("<dependency>")
                .contains(
                  "<groupId>tools.jackson.core</groupId>",
                  "<artifactId>jackson-databind</artifactId>")
                .containsPattern("3\\.\\d+\\.\\d+")
                .actual())
            )
          )
        );
    }

    @Test
    void readValueOfTraverseBecomesTreeToValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  String load(ObjectMapper mapper, JsonNode node) throws Exception {
                      return mapper.readValue(node.traverse(), String.class);
                  }
              }
              """,
            """
              import tools.jackson.databind.JsonNode;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  String load(ObjectMapper mapper, JsonNode node) throws Exception {
                      return mapper.treeToValue(node, String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepJackson2ValuePassedToLibraryOnJackson2() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind")
            //language=java
            .dependsOn(
              """
                package org.example.library;

                public class JsonApiMapper {
                    public JsonApiMapper(com.fasterxml.jackson.databind.ObjectMapper mapper) {
                    }
                }
                """
            )),
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.example.library.JsonApiMapper;

              class ApiConfig {
                  JsonApiMapper jsonApiMapper(ObjectMapper objectMapper) {
                      return new JsonApiMapper(objectMapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepJackson2AnnotationUsingLibraryDeserializerOnJackson2() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind")
            //language=java
            .dependsOn(
              """
                package org.example.library;

                import com.fasterxml.jackson.core.JsonParser;
                import com.fasterxml.jackson.databind.DeserializationContext;
                import com.fasterxml.jackson.databind.JsonDeserializer;
                import java.time.Instant;

                public class InstantDeserializer extends JsonDeserializer<Instant> {
                    @Override
                    public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
                        return null;
                    }
                }
                """
            )),
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
              import org.example.library.InstantDeserializer;

              import java.time.Instant;

              class MetadataRecord {
                  @JsonDeserialize(using = InstantDeserializer.class)
                  Instant createdAt;
              }
              """
          )
        );
    }

    @Test
    void migrateJackson2SubclassesDeclaredInTheSources() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.DeserializationContext;
              import com.fasterxml.jackson.databind.JsonDeserializer;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

              import java.time.Instant;
              import java.util.List;

              class MetadataRecord {
                  @JsonDeserialize(using = InstantDeserializer.class)
                  Instant createdAt;

                  List<String> tags(ObjectMapper mapper, String json) throws Exception {
                      return mapper.readValue(json, new TypeReference<List<String>>() {
                      });
                  }

                  static class InstantDeserializer extends JsonDeserializer<Instant> {
                      @Override
                      public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
                          return null;
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonParser;
              import tools.jackson.core.type.TypeReference;
              import tools.jackson.databind.DeserializationContext;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.databind.annotation.JsonDeserialize;
              import tools.jackson.databind.ValueDeserializer;

              import java.time.Instant;
              import java.util.List;

              class MetadataRecord {
                  @JsonDeserialize(using = InstantDeserializer.class)
                  Instant createdAt;

                  List<String> tags(ObjectMapper mapper, String json) throws Exception {
                      return mapper.readValue(json, new TypeReference<List<String>>() {
                      });
                  }

                  static class InstantDeserializer extends ValueDeserializer<Instant> {
                      @Override
                      public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
                          return null;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateJackson2ValuePassedToSpringWhichMovesToJackson3Too() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind")
            //language=java
            .dependsOn(
              """
                package org.springframework.kafka.support.serializer;

                public class JsonSerializer<T> {
                    public JsonSerializer(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
                    }
                }
                """
            )),
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.springframework.kafka.support.serializer.JsonSerializer;

              class KafkaConfig {
                  JsonSerializer<Object> serializer(ObjectMapper objectMapper) {
                      return new JsonSerializer<>(objectMapper);
                  }
              }
              """,
            """
              import tools.jackson.databind.ObjectMapper;
              import org.springframework.kafka.support.serializer.JsonSerializer;

              class KafkaConfig {
                  JsonSerializer<Object> serializer(ObjectMapper objectMapper) {
                      return new JsonSerializer<>(objectMapper);
                  }
              }
              """
          )
        );
    }
}
