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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeJackson_2_3Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          );
    }

    @DocumentExample
    @Test
    void jacksonUpgradeToVersion3() {
        rewriteRun(
          //language=xml
          pomXml(
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
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+(-rc[\\d]*)?").matcher(pom);
                assertThat(versionMatcher.find()).describedAs("Expected 3.0.x in %s", pom).isTrue();
                String jacksonVersion = versionMatcher.group(0);

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
                                     <version>2.20</version>
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
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      TokenStreamFactory factory = new JsonFactoryBuilder().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_jacksonBomOnly() {
        rewriteRun(
          //language=xml
          pomXml(
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
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+(-rc[\\d]*)?").matcher(pom);
                assertThat(versionMatcher.find()).describedAs("Expected 3.0.x in %s", pom).isTrue();
                String jacksonVersion = versionMatcher.group(0);
                return """
                         <project>
                             <modelVersion>4.0.0</modelVersion>
                             <groupId>org.example</groupId>
                             <artifactId>example</artifactId>
                             <version>1.0.0</version>
                             <dependencyManagement>
                               <dependencies>
                                 <dependency>
                                     <groupId>tools.jackson</groupId>
                                     <artifactId>jackson-bom</artifactId>
                                     <version>%s</version>
                                     <scope>pom</scope>
                                     <type>import</type>
                                 </dependency>
                               </dependencies>
                             </dependencyManagement>
                         </project>
                  """.formatted(jacksonVersion);
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
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      TokenStreamFactory factory = new JsonFactoryBuilder().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_java8Only() {
        rewriteRun(
          //language=xml
          pomXml(
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
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+(-rc[\\d]*)?").matcher(pom);
                assertThat(versionMatcher.find()).describedAs("Expected 3.0.x in %s", pom).isTrue();
                String jacksonVersion = versionMatcher.group(0);

                Matcher annotationsVersionMatcher = Pattern.compile("3\\.\\d+(\\.\\d+)*(-rc[\\d]*)?").matcher(pom);
                assertThat(annotationsVersionMatcher.find()).describedAs("Expected 3.x in %s", pom).isTrue();
                String annotationsVersion = annotationsVersionMatcher.group(0);

                return """
                         <project>
                             <modelVersion>4.0.0</modelVersion>
                             <groupId>org.example</groupId>
                             <artifactId>example</artifactId>
                             <version>1.0.0</version>
                             <dependencies>
                                 <dependency>
                                     <groupId>tools.jackson.core</groupId>
                                     <artifactId>jackson-databind</artifactId>
                                     <version>%s</version>
                                 </dependency>
                             </dependencies>
                         </project>
                  """.formatted(annotationsVersion, jacksonVersion, jacksonVersion);
            }))
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
              import com.fasterxml.jackson.databind.ObjectMapper;

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
              import tools.jackson.core.JacksonException;
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          TokenStreamFactory factory = new JsonFactoryBuilder().build();
                      } catch (JacksonException e) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_JsonParseException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.core.JsonParseException;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (JsonParseException e) {
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.StreamReadException;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          TokenStreamFactory factory = new JsonFactoryBuilder().build();
                      } catch (StreamReadException e) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void jacksonUpgradeToVersion3_JsonGenerationException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonFactoryBuilder;
              import com.fasterxml.jackson.core.JsonGenerationException;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          JsonFactory factory = new JsonFactoryBuilder().build();
                      } catch (JsonGenerationException e) {
                      }
                  }
              }
              """,
            """
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.StreamWriteException;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          TokenStreamFactory factory = new JsonFactoryBuilder().build();
                      } catch (StreamWriteException e) {
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
              import com.fasterxml.jackson.databind.ObjectMapper;

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
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.DatabindException;
              import tools.jackson.databind.ObjectMapper;
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  static void helloJackson() {
                      try {
                          Object[] input = new Object[] { "one", "two" };
                          TokenStreamFactory factory = new JsonFactoryBuilder().build();
                      } catch (DatabindException e) {
                      }
                  }
              }
              """
          )
        );
    }

}
