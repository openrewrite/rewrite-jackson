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
}
