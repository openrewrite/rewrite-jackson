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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveRedundantJsonPropertyValueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveRedundantJsonPropertyValue())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void removeRedundantJsonPropertyFromClass() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class CategorieRequest {
                  @JsonProperty(value = "name", required = true)
                  private String name;
                  @JsonProperty("color")
                  private String color;
                  @JsonProperty("parent_id")
                  private Long parentId;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class CategorieRequest {
                  @JsonProperty(required = true)
                  private String name;
                  private String color;
                  @JsonProperty("parent_id")
                  private Long parentId;
              }
              """
          )
        );
    }

    @Test
    void removeJsonPropertyWithValueAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty(value = "firstName")
                  private String firstName;
                  @JsonProperty(value = "lastName")
                  private String lastName;
              }
              """,
            """
              class Person {
                  private String firstName;
                  private String lastName;
              }
              """
          )
        );
    }

    @Test
    void preserveJsonPropertyWithDifferentName() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty("first_name")
                  private String firstName;
                  @JsonProperty("last_name")
                  private String lastName;
              }
              """
          )
        );
    }

    @Test
    void preserveJsonPropertyWithAdditionalAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty(value = "name", required = true)
                  private String name;
                  @JsonProperty(value = "age", defaultValue = "0")
                  private Integer age;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty(required = true)
                  private String name;
                  @JsonProperty(defaultValue = "0")
                  private Integer age;
              }
              """
          )
        );
    }

    @Test
    void notARecordWithRedundantAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty("name")
                  private String name;
              }
              """,
            """
              class Person {
                  private String name;
              }
              """
          )
        );
    }

    @Test
    void multipleAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;
              import com.fasterxml.jackson.annotation.JsonIgnore;

              class Person {
                  @JsonIgnore
                  @JsonProperty("name")
                  private String name;
                  @JsonProperty("email")
                  @JsonIgnore
                  private String email;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;

              class Person {
                  @JsonIgnore
                  private String name;
                  @JsonIgnore
                  private String email;
              }
              """
          )
        );
    }

    @Test
    void removeUnnamedArgumentMatchingFieldName() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Product {
                  @JsonProperty("id")
                  private Long id;
                  @JsonProperty("name")
                  private String name;
                  @JsonProperty("price")
                  private Double price;
              }
              """,
            """
              class Product {
                  private Long id;
                  private String name;
                  private Double price;
              }
              """
          )
        );
    }

    @Test
    void removeConstructorParameterAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  private String name;

                  Person(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """,
            """
              class Person {
                  private String name;

                  Person(String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void recordWithMixOfAnnotations() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(
                  @JsonProperty("name") String name,
                  @JsonProperty("user_age") int age,
                  @JsonProperty("email") String email
              ) {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(
                  String name,
                  @JsonProperty("user_age") int age,
                  String email
              ) {
              }
              """
          )
        );
    }
}
