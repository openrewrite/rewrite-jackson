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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class Jackson3DependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @DocumentExample
    @Test
    void jacksonAnnotations() {
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
                  </dependencies>
              </project>
              """,
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
                          <version>2.20</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void jacksonCore() {
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
                          <artifactId>jackson-core</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-core</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Test
    void jacksonDatabind() {
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
                          <artifactId>jackson-databind</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Test
    void jacksonModuleKotlin() {
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
                          <artifactId>jackson-module-kotlin</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.module</groupId>
                              <artifactId>jackson-module-kotlin</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"_2.12", "_2.13", "_3"})
    void jacksonModuleScala(String artifactSuffix) {
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
                          <artifactId>jackson-module-scala%s</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(artifactSuffix),
            spec -> spec.after(pom -> assertThat(pom)
              .doesNotContain(">com.fasterxml.jackson.module<")
              .contains(">tools.jackson.module<")
              .containsPattern("3\\.\\d+\\.\\d+")
              .actual())
          )
        );
    }

    @Test
    void jacksonBom() {
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
                          <scope>import</scope>
                          <type>pom</type>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <scope>import</scope>
                              <type>pom</type>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Test
    void jacksonModuleParameterNames() {
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
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Test
    void jacksonDatatypeJdk8() {
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
                          <groupId>com.fasterxml.jackson.datatype</groupId>
                          <artifactId>jackson-datatype-jdk8</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Test
    void jacksonDatatypeJsr310() {
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
                          <groupId>com.fasterxml.jackson.datatype</groupId>
                          <artifactId>jackson-datatype-jsr310</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/37")
    @Test
    void noDuplicateJacksonDatabindDependencies() {
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
                          <artifactId>jackson-databind</artifactId>
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
                              <groupId>tools.jackson.core</groupId>
                              <artifactId>jackson-databind</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/45")
    @Test
    void noDuplicateJacksonDatabindDependenciesInGradle() {
        rewriteRun(spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            //language=gradle
            """
              plugins {
                  id("java-library")
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
                  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")
              }
              """,
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
                assertThat(versionMatcher.find()).describedAs("Expected 3.0.x in %s", pom).isTrue();
                String jacksonVersion = versionMatcher.group(0);
                return """
                    plugins {
                        id("java-library")
                    }

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        implementation("tools.jackson.core:jackson-databind:%s")
                    }
                  """.formatted(jacksonVersion);
            })
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "xml", "csv", "cbor", "avro", "smile", "ion"})
    void jacksonDataformats(String format) {
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
                          <groupId>com.fasterxml.jackson.dataformat</groupId>
                          <artifactId>jackson-dataformat-%s</artifactId>
                          <version>2.19.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(format),
            spec -> spec.after(pom -> {
                Matcher versionMatcher = Pattern.compile("3\\.\\d+\\.\\d+").matcher(pom);
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
                              <groupId>tools.jackson.dataformat</groupId>
                              <artifactId>jackson-dataformat-%s</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(format, jacksonVersion);
            })
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/49")
    @Test
    void jsonSchemaModuleIsNotMigrated() {
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
                          <artifactId>jackson-module-jsonSchema</artifactId>
                          <version>2.20.1</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"jackson-datatype-eclipse-collections", "jackson-datatype-guava", "jackson-datatype-hibernate4",
      "jackson-datatype-hibernate5", "jackson-datatype-hibernate5-jakarta", "jackson-datatype-hibernate6", "jackson-datatype-hibernate7",
      "jackson-datatype-hppc", "jackson-datatype-javax-money", "jackson-datatype-jakarta-jsonp", "jackson-datatype-jaxrs",
      "jackson-datatype-joda", "jackson-datatype-joda-money", "jackson-datatype-json-org", "jackson-datatype-jsr353",
      "jackson-datatype-moneta", "jackson-datatype-pcollections"})
    void datatypeMigrated(String artifact) {
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
                          <groupId>com.fasterxml.jackson.datatype</groupId>
                          <artifactId>%s</artifactId>
                          <version>2.20.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(artifact),
            spec -> spec.after(pom ->
              assertThat(pom)
                .doesNotContain(">com.fasterxml.jackson.datatype<")
                .contains(">tools.jackson.datatype<")
                .containsPattern("3\\.\\d+\\.\\d+")
                .actual())
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"jackson-jaxrs-base", "jackson-jaxrs-cbor-provider", "jackson-jaxrs-json-provider",
      "jackson-jaxrs-smile-provider", "jackson-jaxrs-xml-provider", "jackson-jaxrs-yaml-provider"})
    void jaxrsMigrated(String artifact) {
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
                          <groupId>com.fasterxml.jackson.jaxrs</groupId>
                          <artifactId>%s</artifactId>
                          <version>2.20.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(artifact),
            spec -> spec.after(pom ->
              assertThat(pom)
                .doesNotContain(">com.fasterxml.jackson.jaxrs<")
                .contains(">tools.jackson.jaxrs<")
                .containsPattern("3\\.\\d+\\.\\d+")
                .actual())
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"jackson-jakarta-rs-base", "jackson-jakarta-rs-cbor-provider", "jackson-jakarta-rs-json-provider",
      "jackson-jakarta-rs-smile-provider", "jackson-jakarta-rs-xml-provider", "jackson-jakarta-rs-yaml-provider"})
    void jakartaRsMigrated(String artifact) {
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
                          <groupId>com.fasterxml.jackson.jakarta.rs</groupId>
                          <artifactId>%s</artifactId>
                          <version>2.20.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(artifact),
            spec -> spec.after(pom ->
              assertThat(pom)
                .doesNotContain(">com.fasterxml.jackson.jakarta.rs<")
                .contains(">tools.jackson.jakarta.rs<")
                .containsPattern("3\\.\\d+\\.\\d+")
                .actual())
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"jackson-jr-all", "jackson-jr-annotation-support", "jackson-jr-extension-javatime",
      "jackson-jr-objects", "jackson-jr-retrofit2", "jackson-jr-stree"})
    void jrMigrated(String artifact) {
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
                          <groupId>com.fasterxml.jackson.jr</groupId>
                          <artifactId>%s</artifactId>
                          <version>2.20.0</version>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(artifact),
            spec -> spec.after(pom ->
              assertThat(pom)
                .doesNotContain(">com.fasterxml.jackson.jr<")
                .contains(">tools.jackson.jr<")
                .containsPattern("3\\.\\d+\\.\\d+")
                .actual())
          )
        );
    }
}
