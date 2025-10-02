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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
}
