/*
 * Copyright 2026 the original author or authors.
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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("DefaultAnnotationParam")
class JsonSerializeIncludeToJsonIncludeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new JsonSerializeIncludeToJsonInclude())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-annotations-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void retainOriginalAnnotationWhenOtherArgumentsRemain() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import com.fasterxml.jackson.databind.JsonSerializer.None;
              import static com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, using = None.class)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.JsonSerializer.None;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              @JsonSerialize(using = None.class)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void removeJsonSerializeEntirelyWhenIncludeWasOnlyArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;

              @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void inclusionImport() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion;

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
    void replaceFieldAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import static com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion.NON_NULL;

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
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import static com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion.NON_NULL;

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
    void leaveAnnotationsWithoutIncludeAlone() {
        // No `include` argument → nothing for this recipe to do; `@JsonSerialize(using = ...)` is
        // not deprecated and stays as-is in Jackson 3 (the package will be renamed by a later step).
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              import com.fasterxml.jackson.databind.JsonSerializer.None;

              @JsonSerialize(using = None.class)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void skipWhenSiblingJsonIncludeAlreadyHasSameValue() {
        // Strip the `include` from @JsonSerialize but don't add a duplicate @JsonInclude — that
        // would be a compile error since @JsonInclude is not @Repeatable.
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_NULL)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void deferToExistingJsonIncludeWhenValuesDiffer() {
        // Existing @JsonInclude is user-authored and likely intentional; defer to it and strip
        // the deprecated `include` from @JsonSerialize rather than introducing a conflict.
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;

              @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
              @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;

              @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
              class Test {
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
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
