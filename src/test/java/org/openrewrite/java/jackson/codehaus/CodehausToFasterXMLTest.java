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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
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
              import com.fasterxml.jackson.annotation.JsonInclude.Include;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  private static ObjectMapper initializeObjectMapper() {
                      ObjectMapper mapper = new ObjectMapper();
                      return mapper.setSerializationInclusion(Include.NON_NULL);
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

              import static org.codehaus.jackson.map.SerializationConfig.Feature.WRAP_ROOT_VALUE;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(WRAP_ROOT_VALUE, true);
                      mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.SerializationFeature.WRAP_ROOT_VALUE;

              class Test {
                  void foo(){
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(WRAP_ROOT_VALUE, true);
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

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/17")
    @Nested
    class AnnotationIntrospector {

        @Test
        void replaceWithSetConfigCallGeneric() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.AnnotationIntrospector;
                  import org.codehaus.jackson.map.ObjectMapper;

                  class Test {
                      void method(ObjectMapper mapper, AnnotationIntrospector introspector) {
                          mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.AnnotationIntrospector;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void method(ObjectMapper mapper, AnnotationIntrospector introspector) {
                          mapper.setConfig(mapper.getSerializationConfig().with(introspector));
                      }
                  }
                  """
              )
            );
        }

        @CsvSource(textBlock = """
          org.codehaus.jackson.xc.JaxbAnnotationIntrospector, com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector
          org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector, com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
          org.codehaus.jackson.map.introspect.NopAnnotationIntrospector, com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
          """)
        @ParameterizedTest // org.codehaus.jackson.map.AnnotationIntrospector.Pair, com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair
        void replaceWithSetConfigCallJaxB(String codehausClass, String fasterXmlClass) {
            rewriteRun(
              //language=java
              java(
                """
                  import org.codehaus.jackson.map.ObjectMapper;
                  import %s;

                  class Test {
                      void method(ObjectMapper mapper, %s introspector) {
                          mapper.getSerializationConfig().setAnnotationIntrospector(introspector);
                      }
                  }
                  """.formatted(codehausClass, codehausClass.substring(codehausClass.lastIndexOf('.') + 1)),
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import %s;

                  class Test {
                      void method(ObjectMapper mapper, %s introspector) {
                          mapper.setConfig(mapper.getSerializationConfig().with(introspector));
                      }
                  }
                  """.formatted(fasterXmlClass, fasterXmlClass.substring(fasterXmlClass.lastIndexOf('.') + 1))
              )
            );
        }
    }
}
