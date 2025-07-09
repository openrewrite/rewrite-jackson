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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("DefaultAnnotationParam")
class JsonIncludeAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new JsonIncludeAnnotation())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void retainOriginalAnnotation() {
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
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import org.codehaus.jackson.map.JsonSerializer.None;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              @JsonSerialize(using = None.class)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void removeUnusedJsonSerializeImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationA {
              }

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationB {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationA {
              }

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationB {
              }
              """
          )
        );
    }

    @Test
    void replaceAnnotationAndRemoveImportInMixedCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
              class ViaAnnotationA {
              }

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationB {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationA {
              }

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class ViaAnnotationB {
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

    @Test
    void replaceDifferentInclusionFieldsAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;

              @JsonSerialize(include = JsonSerialize.Inclusion.ALWAYS)
              public class Person {
                  @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
                  private String name;

                  @JsonSerialize(include = JsonSerialize.Inclusion.NON_EMPTY)
                  private String address;

                  @JsonSerialize(include = JsonSerialize.Inclusion.NON_DEFAULT)
                  private int age;

                  public Person(String name, String address, int age) {
                      this.name = name;
                      this.address = address;
                      this.age = age;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.ALWAYS)
              public class Person {
                  @JsonInclude(value = JsonInclude.Include.NON_NULL)
                  private String name;

                  @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
                  private String address;

                  @JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
                  private int age;

                  public Person(String name, String address, int age) {
                      this.name = name;
                      this.address = address;
                      this.age = age;
                  }
              }
              """
          )
        );
    }
}
