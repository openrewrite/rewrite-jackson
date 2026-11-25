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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class CodehausDependencyToFasterXMLTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
          "jackson-core-asl", "jackson-mapper-asl"));
    }

    //language=java
    private static final String MAPPER_USER = """
      import org.codehaus.jackson.map.ObjectMapper;

      class Mapper {
          ObjectMapper mapper = new ObjectMapper();
      }
      """;
    @DocumentExample
    @Test
    void changeDependencyWithoutExplicitVersion() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML(null)),

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
                          <artifactId>jackson-core-asl</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.codehaus.jackson</groupId>
                          <artifactId>jackson-mapper-asl</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            after -> after.after(pomXml -> {
                // jackson-core and jackson-databind resolve independently to their own latest
                // release, which may differ when Jackson publishes the modules at different times.
                List<String> versions = Pattern.compile("<version>(2\\.\\d+\\.\\d+)</version>").matcher(pomXml).results()
                  .map(result -> result.group(1))
                  .toList();
                return """
                  <project>
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      <dependencies>
                          <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                              <version>%1$s</version>
                          </dependency>
                          <dependency>
                              <groupId>com.fasterxml.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%2$s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(versions.getFirst(), versions.get(1));
            })
          )
        );
    }

    @CsvSource({
      "2.12.x,2.12.7",
      "2.14.x,2.14.3"
    })
    @ParameterizedTest
    void changeDependencies(String pattern, String expectedVersion) {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML(pattern)),
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
                          <artifactId>jackson-core-asl</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>%1$s</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(expectedVersion)
          )
        );
    }
    @Test
    void lgplArtifacts() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
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
                          <artifactId>jackson-core-lgpl</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.codehaus.jackson</groupId>
                          <artifactId>jackson-mapper-lgpl</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-core</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void companionArtifacts() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
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
                          <artifactId>jackson-jaxrs</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.codehaus.jackson</groupId>
                          <artifactId>jackson-xc</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.codehaus.jackson</groupId>
                          <artifactId>jackson-smile</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                      <dependency>
                          <groupId>org.codehaus.jackson</groupId>
                          <artifactId>jackson-mrbean</artifactId>
                          <version>1.9.13</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.jaxrs</groupId>
                          <artifactId>jackson-jaxrs-json-provider</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.module</groupId>
                          <artifactId>jackson-module-jaxb-annotations</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.dataformat</groupId>
                          <artifactId>jackson-dataformat-smile</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                      <dependency>
                          <groupId>com.fasterxml.jackson.module</groupId>
                          <artifactId>jackson-module-mrbean</artifactId>
                          <version>2.14.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void addDatabindWhenJackson1ArrivesTransitively() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
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
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void addDatabindWhenOnlyCoreIsDeclared() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
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
                            <artifactId>jackson-core-asl</artifactId>
                            <version>1.9.13</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-core</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void declaredMapperIsChangedNotAddedAgain() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
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
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void alignToJackson2VersionAlreadyInTheBuild() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML(null)),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
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
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-annotations</artifactId>
                            <version>2.12.7</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-annotations</artifactId>
                            <version>2.12.7</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.12.7</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void declaredJackson2VersionIsKept() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML(null)),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
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
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.12.7</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void noTestScopedArtifactThatDatabindBringsToMainCode() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
            //language=java
            srcTestJava(java(
              """
                import org.codehaus.jackson.annotate.JsonProperty;
                import org.codehaus.jackson.JsonParser;

                class Dto {
                    @JsonProperty
                    String name;
                    JsonParser parser;
                }
                """
            )),
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
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void mainCodeKeepsArtifactsThatTestScopedDatabindWouldBring() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(
              """
                import org.codehaus.jackson.annotate.JsonIgnore;
                import org.codehaus.jackson.JsonParser;

                class Dto {
                    @JsonIgnore
                    String name;
                    JsonParser parser;
                }
                """
            )),
            //language=java
            srcTestJava(java(MAPPER_USER)),
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
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-annotations</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-core</artifactId>
                            <version>2.14.3</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void jackson1RemovedWhereJackson2CounterpartIsAlreadyDeclared() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.x")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
            //language=xml
            pomXml(
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <properties>
                        <jackson.version>2.5.4</jackson.version>
                        <jackson1.version>1.9.13</jackson1.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.fasterxml.jackson.core</groupId>
                                <artifactId>jackson-databind</artifactId>
                                <version>${jackson.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>com.fasterxml.jackson.jaxrs</groupId>
                                <artifactId>jackson-jaxrs-json-provider</artifactId>
                                <version>${jackson.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.codehaus.jackson</groupId>
                                <artifactId>jackson-mapper-asl</artifactId>
                                <version>${jackson1.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.codehaus.jackson</groupId>
                                <artifactId>jackson-jaxrs</artifactId>
                                <version>${jackson1.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.codehaus.jackson</groupId>
                            <artifactId>jackson-mapper-asl</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <properties>
                        <jackson.version>2.5.4</jackson.version>
                        <jackson1.version>1.9.13</jackson1.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.fasterxml.jackson.core</groupId>
                                <artifactId>jackson-databind</artifactId>
                                <version>${jackson.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>com.fasterxml.jackson.jaxrs</groupId>
                                <artifactId>jackson-jaxrs-json-provider</artifactId>
                                <version>${jackson.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void testScopeWhenOnlyTestsUseJackson1() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")),
          mavenProject("my-app",
            //language=java
            srcTestJava(java(MAPPER_USER)),
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
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.avro</groupId>
                            <artifactId>avro</artifactId>
                            <version>1.7.7</version>
                        </dependency>
                        <dependency>
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.14.3</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void gradleTransitiveJackson1() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.14.x")).beforeRecipe(withToolingApi()),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'org.apache.avro:avro:1.7.7'
                }
                """,
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation "com.fasterxml.jackson.core:jackson-databind:2.14.3"
                    implementation 'org.apache.avro:avro:1.7.7'
                }
                """
            )
          )
        );
    }

    @Test
    void gradleJackson1RemovedWhereJackson2CounterpartIsAlreadyDeclared() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.x")).beforeRecipe(withToolingApi()),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(MAPPER_USER)),
            //language=groovy
            buildGradle(
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.5.4'
                    implementation 'org.codehaus.jackson:jackson-mapper-asl:1.9.13'
                }
                """,
              """
                plugins {
                    id 'java-library'
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    implementation 'com.fasterxml.jackson.core:jackson-databind:2.5.4'
                }
                """
            )
          )
        );
    }

    @Test
    void mainAnnotationsWithTestOnlyDatabindAndJaxrs() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.x"))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-asl", "jackson-mapper-asl", "jackson-jaxrs-1")),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(
              """
                import org.codehaus.jackson.annotate.JsonIgnoreProperties;

                @JsonIgnoreProperties(ignoreUnknown = true)
                class TestResource {
                }
                """
            )),
            //language=java
            srcTestJava(java(
              """
                import org.codehaus.jackson.JsonNode;
                import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
                import org.codehaus.jackson.map.ObjectMapper;

                class ResourceTest {
                    JacksonJsonProvider provider;
                    ObjectMapper mapper;
                    JsonNode node;
                }
                """
            )),
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
                            <groupId>com.sun.jersey</groupId>
                            <artifactId>jersey-json</artifactId>
                            <version>1.12</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).containsPattern("<artifactId>jackson-annotations</artifactId>\\s*<version>[^<]+</version>\\s*</dependency>");
                  return pom;
              })
            )
          )
        );
    }

    @Test
    void multiModuleMainAnnotationsWithTestOnlyDatabind() {
        rewriteRun(
          spec -> spec.recipe(new CodehausDependencyToFasterXML("2.x"))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-asl", "jackson-mapper-asl", "jackson-jaxrs-1")),
          //language=xml
          pomXml(
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>my-app</module>
                  </modules>
              </project>
              """
          ),
          mavenProject("my-app",
            //language=java
            srcMainJava(java(
              """
                import org.codehaus.jackson.annotate.JsonIgnoreProperties;

                @JsonIgnoreProperties(ignoreUnknown = true)
                class TestResource {
                }
                """
            )),
            //language=java
            srcTestJava(java(
              """
                import org.codehaus.jackson.JsonNode;
                import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
                import org.codehaus.jackson.map.ObjectMapper;

                class ResourceTest {
                    JacksonJsonProvider provider;
                    ObjectMapper mapper;
                    JsonNode node;
                }
                """
            )),
            //language=xml
            pomXml(
              """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>my-app</artifactId>
                    <packaging>war</packaging>
                    <dependencies>
                        <dependency>
                            <groupId>com.sun.jersey</groupId>
                            <artifactId>jersey-json</artifactId>
                            <version>1.12</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom -> {
                  assertThat(pom).containsPattern("<artifactId>jackson-annotations</artifactId>\\s*<version>[^<]+</version>\\s*</dependency>");
                  return pom;
              })
            )
          )
        );
    }
}
