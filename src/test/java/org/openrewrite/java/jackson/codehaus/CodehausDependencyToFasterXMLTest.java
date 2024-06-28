/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.jackson.org/licenses/LICENSE-2.0
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
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.maven.Assertions.pomXml;

class CodehausDependencyToFasterXMLTest implements RewriteTest {
    @Test
    @DocumentExample
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
                String version = Pattern.compile("<version>(2\\.\\d+\\.\\d+)</version>").matcher(pomXml).results().findFirst().get().group(1);
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
                              <version>%1$s</version>
                          </dependency>
                      </dependencies>
                  </project>
                  """.formatted(version);
            })
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      "2.12.x,2.12.7",
      "2.14.x,2.14.3"
    })
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
}
