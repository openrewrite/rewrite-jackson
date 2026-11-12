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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("DefaultAnnotationParam")
class TransferJsonSerializeArgumentsFromCodehausToFasterXMLTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new TransferJsonSerializeArgumentsFromCodehausToFasterXML())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-asl",
            "jackson-mapper-asl",
            "jackson-xc",
            "jackson-annotations-2",
            "jackson-core-2",
            "jackson-databind-2"));
    }

    @ParameterizedTest
    @ValueSource(
      strings = {"using", "contentUsing", "keyUsing"}
    )
    void shouldTransferArgument(String argumentName) {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize
                private String first;
              }
              """.formatted(argumentName),
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = None.class)
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = None.class)
                private String first;
              }
              """.formatted(argumentName)
          )
        );
    }

    @CsvSource(
      {"using,contentUsing"}
    )
    @ParameterizedTest
    void shouldTransferArgumentMultiArguments(String firstArg, String secondArg) {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize
                private String first;
              }
              """.formatted(firstArg, secondArg),
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = None.class, %2$s = None.class)
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = None.class, %2$s = None.class)
                private String first;
              }
              """.formatted(firstArg, secondArg)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(
      strings = {"using", "contentUsing", "keyUsing"}
    )
    void doNotOverwriteExistingUsing(String argumentName) {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = com.fasterxml.jackson.databind.JsonSerializer.None.class)
                private String first;
              }
              """.formatted(argumentName)
          )
        );
    }

    @CsvSource(
      {"using,contentUsing"}
    )
    @ParameterizedTest
    void shouldTransferArgumentSome(String firstArg, String secondArg) {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = com.fasterxml.jackson.databind.JsonSerializer.None.class)
                private String first;
              }
              """.formatted(firstArg, secondArg),
            """
              import org.codehaus.jackson.map.JsonSerializer.None;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;

              @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = None.class, %2$s = None.class)
              class Test {
                @JsonSerialize(include = NON_NULL, %1$s = None.class, %2$s = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(%1$s = com.fasterxml.jackson.databind.JsonSerializer.None.class, %2$s = None.class)
                private String first;
              }
              """.formatted(firstArg, secondArg)
          )
        );
    }

    @Test
    void appendedArgumentFollowsTheLayoutOfExistingOnes() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;

              @JsonSerialize(as = String.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
                  @JsonSerialize(as = String.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                          keyUsing = com.fasterxml.jackson.databind.JsonSerializer.None.class, // no key serializer
                          using = com.fasterxml.jackson.databind.JsonSerializer.None.class
                  )
                  String field;

                  @JsonSerialize(as = String.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                          keyUsing = com.fasterxml.jackson.databind.JsonSerializer.None.class,
                          /* no serializer */ using = com.fasterxml.jackson.databind.JsonSerializer.None.class
                  )
                  String other;
              }
              """,
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;

              @JsonSerialize(as = String.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class, as = String.class)
              class Test {
                  @JsonSerialize(as = String.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                          keyUsing = com.fasterxml.jackson.databind.JsonSerializer.None.class, // no key serializer
                          using = com.fasterxml.jackson.databind.JsonSerializer.None.class,
                          as = String.class
                  )
                  String field;

                  @JsonSerialize(as = String.class)
                  @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                          keyUsing = com.fasterxml.jackson.databind.JsonSerializer.None.class,
                          /* no serializer */ using = com.fasterxml.jackson.databind.JsonSerializer.None.class,
                          as = String.class
                  )
                  String other;
              }
              """
          )
        );
    }

    @Test
    void codehausAnnotationWithoutArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;

              @JsonSerialize
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
              }
              """
          )
        );
    }
}
