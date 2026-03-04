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

class AddJsonCreatorToPrivateConstructorsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddJsonCreatorToPrivateConstructors())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void privateConstructorWithJsonPropertyParams() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;
                  private final int age;

                  private Model(@JsonProperty("name") String name, @JsonProperty("age") int age) {
                      this.name = name;
                      this.age = age;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonCreator;
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;
                  private final int age;

                  @JsonCreator
                  private Model(@JsonProperty("name") String name, @JsonProperty("age") int age) {
                      this.name = name;
                      this.age = age;
                  }
              }
              """
          )
        );
    }

    @Test
    void packagePrivateConstructorWithJsonPropertyParams() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonCreator;
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  @JsonCreator
                  Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void protectedConstructorWithJsonPropertyParams() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  protected Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonCreator;
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  @JsonCreator
                  protected Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangePublicConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  public Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeAlreadyAnnotatedWithJsonCreator() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonCreator;
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  private final String name;

                  @JsonCreator
                  private Model(@JsonProperty("name") String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNoArgConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  @JsonProperty("name")
                  private String name;

                  private Model() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeConstructorWithoutJsonPropertyOnParams() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonProperty;

              class Model {
                  @JsonProperty("name")
                  private String name;

                  private Model(String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }
}
