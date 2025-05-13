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
package org.openrewrite.jackson;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

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
            .activateRecipes("org.openrewrite.jackson.UpgradeJackson_2_3")
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
                                     <groupId>com.fasterxml.jackson.core</groupId>
                                     <artifactId>jackson-annotations</artifactId>
                                     <version>%s</version>
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
                  """.formatted(annotationsVersion, jacksonVersion, jacksonVersion);
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
              import tools.jackson.core.JsonFactory;
              import tools.jackson.core.JsonFactoryBuilder;
              import tools.jackson.databind.ObjectMapper;

              class Test {
                  public String foo(@JsonProperty("foo") String foo) {
                      return foo;
                  }

                  static void helloJackson() {
                      Object[] input = new Object[] { "one", "two" };
                      JsonFactory factory = new JsonFactoryBuilder().build();
                  }
              }
              """
          )
        );
    }
}
