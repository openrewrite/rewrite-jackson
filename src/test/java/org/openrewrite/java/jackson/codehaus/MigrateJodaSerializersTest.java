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
import org.openrewrite.test.TypeValidation;


import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.RewriteTest.fromRuntimeClasspath;

class MigrateJodaSerializersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(fromRuntimeClasspath("org.openrewrite.java.jackson.codehaus.MigrateJodaSerializers"))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-asl", "jackson-mapper-asl", "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
            "jackson-datatype-joda", "joda-time"));
    }

    @DocumentExample
    @Test
    void nestedSerializerMovesAndModuleIsAdded() {
        rewriteRun(
          // the Jackson 1 JsonSerialize annotation now names a Jackson 2 serializer, until CodehausClassesToFasterXML moves it
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.ext.JodaSerializers.DateTimeSerializer;
                  import org.joda.time.DateTime;

                  class Classified {
                      @JsonSerialize(using = DateTimeSerializer.class)
                      private DateTime created;
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.ser.DateTimeSerializer;
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.joda.time.DateTime;

                  class Classified {
                      @JsonSerialize(using = DateTimeSerializer.class)
                      private DateTime created;
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void moduleOfOnlyJodaSerializersIsReplacedByJodaModule() {
        rewriteRun(
          // JodaModule is a Jackson 2 module, registered on a Jackson 1 mapper until CodehausClassesToFasterXML moves it
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.ext.JodaDeserializers;
                  import org.codehaus.jackson.map.ext.JodaSerializers;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.joda.time.DateTime;

                  class JsonConfig {
                      ObjectMapper mapper() {
                          ObjectMapper mapper = new ObjectMapper();
                          SimpleModule module = new SimpleModule("joda", Version.unknownVersion());
                          module.addSerializer(DateTime.class, new JodaSerializers.DateTimeSerializer());
                          module.addDeserializer(DateTime.class, new JodaDeserializers.DateTimeDeserializer());
                          mapper.registerModule(module);
                          return mapper;
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.JodaModule;
                  import org.codehaus.jackson.map.ObjectMapper;

                  class JsonConfig {
                      ObjectMapper mapper() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.registerModule(new JodaModule());
                          return mapper;
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void jodaModuleIsRegisteredOnEveryMapperTheModuleWasRegisteredOn() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.ext.JodaSerializers;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.joda.time.DateTime;

                  class JsonConfig {
                      void configure(ObjectMapper first, ObjectMapper second) {
                          SimpleModule module = new SimpleModule("joda", Version.unknownVersion());
                          module.addSerializer(DateTime.class, new JodaSerializers.DateTimeSerializer());
                          first.registerModule(module);
                          second.registerModule(module);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.JodaModule;
                  import org.codehaus.jackson.map.ObjectMapper;

                  class JsonConfig {
                      void configure(ObjectMapper first, ObjectMapper second) {
                          first.registerModule(new JodaModule());
                          second.registerModule(new JodaModule());
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void jodaModuleIsRegisteredBesideEveryRegistrationOfAModuleWithOtherSerializers() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.ext.JodaSerializers;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.codehaus.jackson.map.ser.std.ToStringSerializer;
                  import org.joda.time.DateTime;

                  class JsonConfig {
                      void configure(ObjectMapper first, ObjectMapper second) {
                          SimpleModule module = new SimpleModule("joda", Version.unknownVersion());
                          module.addSerializer(DateTime.class, new JodaSerializers.DateTimeSerializer());
                          module.addSerializer(Long.class, ToStringSerializer.instance);
                          first.registerModule(module);
                          second.registerModule(module);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.JodaModule;
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.codehaus.jackson.map.ser.std.ToStringSerializer;

                  class JsonConfig {
                      void configure(ObjectMapper first, ObjectMapper second) {
                          SimpleModule module = new SimpleModule("joda", Version.unknownVersion());
                          module.addSerializer(Long.class, ToStringSerializer.instance);
                          first.registerModule(module);
                          first.registerModule(new JodaModule());
                          second.registerModule(module);
                          second.registerModule(new JodaModule());
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void jodaSerializersMoveOutOfModuleWithOtherSerializers() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.JsonGenerator;
                  import org.codehaus.jackson.map.JsonSerializer;
                  import org.codehaus.jackson.map.SerializerProvider;

                  public class StringUnicodeSerializer extends JsonSerializer<String> {
                      @Override
                      public void serialize(String value, JsonGenerator gen, SerializerProvider provider) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.ext.JodaSerializers.LocalDateSerializer;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.joda.time.LocalDate;

                  class JsonConfig {
                      void configure(ObjectMapper mapper) {
                          SimpleModule module = new SimpleModule("custom", Version.unknownVersion());
                          module.addSerializer(String.class, new StringUnicodeSerializer());
                          module.addSerializer(LocalDate.class, new LocalDateSerializer());
                          mapper.registerModule(module);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.JodaModule;
                  import org.codehaus.jackson.Version;
                  import org.codehaus.jackson.map.ObjectMapper;
                  import org.codehaus.jackson.map.module.SimpleModule;

                  class JsonConfig {
                      void configure(ObjectMapper mapper) {
                          SimpleModule module = new SimpleModule("custom", Version.unknownVersion());
                          module.addSerializer(String.class, new StringUnicodeSerializer());
                          mapper.registerModule(module);
                          mapper.registerModule(new JodaModule());
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void moduleRegisteredElsewhereKeepsSerializerTypeChange() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          mavenProject("my-app",
            srcMainJava(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.ext.JodaSerializers;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.joda.time.DateTime;

                  class JsonConfig {
                      void configure(SimpleModule module) {
                          module.addSerializer(DateTime.class, new JodaSerializers.DateTimeSerializer());
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.datatype.joda.ser.DateTimeSerializer;
                  import org.codehaus.jackson.map.module.SimpleModule;
                  import org.joda.time.DateTime;

                  class JsonConfig {
                      void configure(SimpleModule module) {
                          module.addSerializer(DateTime.class, new DateTimeSerializer());
                      }
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).contains("<artifactId>jackson-datatype-joda</artifactId>");
                  return pom;
              })
            )
          )
        );
    }
}
