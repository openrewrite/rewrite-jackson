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

class UseModernDateTimeSerializationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseModernDateTimeSerialization())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void removeIso8601FormatOnLocalDateTime() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDateTime;

              class Event {
                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
                  private LocalDateTime timestamp;
              }
              """,
            """
              import java.time.LocalDateTime;

              class Event {
                  private LocalDateTime timestamp;
              }
              """
          )
        );
    }

    @Test
    void removeIso8601FormatOnLocalDate() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDate;

              class Person {
                  @JsonFormat(pattern = "yyyy-MM-dd")
                  private LocalDate birthDate;
              }
              """,
            """
              import java.time.LocalDate;

              class Person {
                  private LocalDate birthDate;
              }
              """
          )
        );
    }

    @Test
    void removeIso8601FormatWithMilliseconds() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.Instant;

              class Event {
                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
                  private Instant createdAt;
              }
              """,
            """
              import java.time.Instant;

              class Event {
                  private Instant createdAt;
              }
              """
          )
        );
    }

    @Test
    void keepCustomNonIso8601Format() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDate;

              class Person {
                  @JsonFormat(pattern = "MM/dd/yyyy")
                  private LocalDate birthDate;
              }
              """
          )
        );
    }

    @Test
    void keepFormatWithAdditionalAttributes() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDateTime;

              class Event {
                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
                  private LocalDateTime timestamp;
              }
              """
          )
        );
    }

    @Test
    void doNotAffectNonJavaTimeTypes() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.util.Date;

              class Event {
                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
                  private Date timestamp;
              }
              """
          )
        );
    }

    @Test
    void removeIso8601FormatOnMultipleFields() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDate;
              import java.time.LocalDateTime;

              class Event {
                  @JsonFormat(pattern = "yyyy-MM-dd")
                  private LocalDate date;

                  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
                  private LocalDateTime timestamp;

                  @JsonFormat(pattern = "MM/dd/yyyy")
                  private LocalDate customDate;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonFormat;
              import java.time.LocalDate;
              import java.time.LocalDateTime;

              class Event {
                  private LocalDate date;

                  private LocalDateTime timestamp;

                  @JsonFormat(pattern = "MM/dd/yyyy")
                  private LocalDate customDate;
              }
              """
          )
        );
    }
}
