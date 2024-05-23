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
package org.openrewrite.java.jackson;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Pattern;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class CodehausToFasterXMLTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.jackson.CodehausToFasterXML")
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void onlyJsonSerializeInclusion() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              
              class Test {
                  private static ObjectMapper initializeObjectMapper() {
                      ObjectMapper mapper = new ObjectMapper();
                      return mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.annotation.JsonInclude.Include;
              import com.fasterxml.jackson.databind.ObjectMapper;
              
              class Test {
                  private static ObjectMapper initializeObjectMapper() {
                      ObjectMapper mapper = new ObjectMapper();
                      return mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                  }
              }
              """
          )
        );
    }


    @Nested
    class ClassAnnotations {
        @Test
        void retainOriginalAnnotationToo() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.JsonSerializer.None;
                  import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
                  
                  @JsonSerialize(include = NON_NULL, using = None.class)
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.JsonSerializer.None;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;
                  
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void retainOtherAnnotationArguments() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.JsonSerializer.None;
                  
                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.JsonSerializer.None;
                  import com.fasterxml.jackson.databind.annotation.JsonSerialize;
                  
                  @JsonSerialize(using = None.class)
                  class Test {
                  }
                  """
              )
            );
        }

        @Test
        void staticImport() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
                  
                  @JsonSerialize(include = NON_NULL)
                  class StaticImport {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  class StaticImport {
                  }
                  """
              )
            );
        }

        @Test
        void inclusionImport() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
                  
                  @JsonSerialize(include = Inclusion.NON_NULL)
                  class ViaInclusion {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  class ViaInclusion {
                  }
                  """
              )
            );
        }

        @Test
        void annotationImport() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.codehaus.jackson.map.annotate.JsonSerialize;
                  
                  @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
                  class ViaAnnotation {
                  }
                  """,
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  class ViaAnnotation {
                  }
                  """
              )
            );
        }
    }

    @Test
    void replaceFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              class Test {
                  @JsonSerialize(include = NON_NULL)
                  Object field;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              
              class Test {
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  Object field;
              }
              """
          )
        );
    }

    @Test
    void replaceMethodAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              class Test {
                  @JsonSerialize(include = NON_NULL)
                  void method() {}
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              
              class Test {
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  void method() {}
              }
              """
          )
        );
    }

    @Nested
    class Dependencies {
        @Test
        void changeDependencies() {
            rewriteRun(
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
    }
}
