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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveRedundantJsonPropertyOnRecordsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantJsonPropertyOnRecords())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void removeRedundantJsonPropertyOnRecord() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty("name") String name, @JsonProperty("age") int age) {
              }
              """,
            """
              record Person(String name, int age) {
              }
              """
          )
        );
    }

    @Test
    void removeRedundantJsonPropertyWithValueAttribute() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty(value = "name") String name) {
              }
              """,
            """
              record Person(String name) {
              }
              """
          )
        );
    }

    @Test
    void keepNonRedundantJsonProperty() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty("full_name") String name, @JsonProperty("user_age") int age) {
              }
              """
          )
        );
    }

    @Test
    void removeValueAttributeWithAdditionalAttributes() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty(value = "name", required = true) String name) {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty(required = true) String name) {
              }
              """
          )
        );
    }

    @Test
    void removeValueAttributeButKeepOthers() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty(value = "name", required = true, defaultValue = "John") String name) {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty(required = true, defaultValue = "John") String name) {
              }
              """
          )
        );
    }

    @Test
    void mixRedundantAndNonRedundant() {
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

    @Test
    void doNotAffectRegularClasses() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Person {
                  @JsonProperty("name")
                  private String name;

                  @JsonProperty("age")
                  private int age;

                  public Person(String name, int age) {
                      this.name = name;
                      this.age = age;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepEmptyJsonProperty() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              record Person(@JsonProperty String name) {
              }
              """
          )
        );
    }
}
