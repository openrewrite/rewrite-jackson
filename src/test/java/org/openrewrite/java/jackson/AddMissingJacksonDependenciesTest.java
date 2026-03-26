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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class AddMissingJacksonDependenciesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.AddMissingJacksonDependencies")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-2", "jackson-databind-2", "jackson-dataformat-xml-2"));
    }

    @DocumentExample
    @Test
    void addXmlDependencyWhenXmlMapperUsed() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              java(
                """
                  import com.fasterxml.jackson.dataformat.xml.XmlMapper;

                  class A {
                      XmlMapper mapper = new XmlMapper();
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
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.17.3</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(pom ->
                assertThat(pom)
                  .contains(">jackson-dataformat-xml<")
                  .contains(">com.fasterxml.jackson.dataformat<")
                  .containsPattern("2\\.\\d+\\.?\\d*")
                  .actual())
            )
          )
        );
    }

    @Test
    void noChangeWhenNoDataformatTypesUsed() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class A {
                      ObjectMapper mapper = new ObjectMapper();
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
                            <groupId>com.fasterxml.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>2.17.3</version>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }
}
