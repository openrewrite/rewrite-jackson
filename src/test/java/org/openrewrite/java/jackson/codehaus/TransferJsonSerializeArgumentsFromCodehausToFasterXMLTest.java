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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
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
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @ParameterizedTest
    @ValueSource(
      strings = {"using", "contentUsing", "keyUsing", "nullUsing"}
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

    @ParameterizedTest
    @CsvSource(
      {"using,contentUsing", "keyUsing,nullUsing"}
    )
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
      strings = {"using", "contentUsing", "keyUsing", "nullUsing"}
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

    @ParameterizedTest
    @CsvSource(
      {"using,contentUsing", "keyUsing,nullUsing"}
    )
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
}
