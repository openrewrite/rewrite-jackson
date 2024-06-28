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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

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

    @Test
    void serializationConfigEnums() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.DeserializationConfig;
              import org.codehaus.jackson.map.ObjectMapper;
              import org.codehaus.jackson.map.SerializationConfig;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(SerializationConfig.Feature.WRAP_ROOT_VALUE, true);
                      mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializationFeature;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
                      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
    void replaceJsonValueAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.annotate.JsonValue;

              public enum TypeEnumWithValue {
                  TYPE1(1, "Type A"), TYPE2(2, "Type 2");

                  private Integer id;
                  private String name;

                  public TypeEnumWithValue(Integer id, String name) {
                      this.id = id;
                      this.name = name;
                  }

                  @JsonValue
                  public String getName() {
                      return name;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonValue;

              public enum TypeEnumWithValue {
                  TYPE1(1, "Type A"), TYPE2(2, "Type 2");

                  private Integer id;
                  private String name;

                  public TypeEnumWithValue(Integer id, String name) {
                      this.id = id;
                      this.name = name;
                  }

                  @JsonValue
                  public String getName() {
                      return name;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceJsonSerializeAnnotation() {
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
}
