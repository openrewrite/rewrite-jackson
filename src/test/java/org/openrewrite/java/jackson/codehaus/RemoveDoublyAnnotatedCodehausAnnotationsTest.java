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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("DefaultAnnotationParam")
class RemoveDoublyAnnotatedCodehausAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new RemoveDoublyAnnotatedCodehausAnnotations())
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void removeCodehausAnnotationsIfDoublyAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.JsonSerializer.None;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @JsonSerialize(include = NON_NULL, using = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              
              @JsonSerialize(using = JsonSerializer.None.class)
              class Test {
              }
              """
          )
        );
    }

    @Test
    void removeCodehausAnnotationsDoublyAnnotatedAndRefactorImports() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.JsonSerializer.None;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @JsonSerialize(include = NON_NULL, using = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
                @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
                private String first;
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import com.fasterxml.jackson.databind.annotation.JsonSerialize;
              
              @JsonSerialize(using = JsonSerializer.None.class)
              class Test {
                @JsonSerialize(using = JsonSerializer.None.class)
                private String first;
              }
              """
          )
        );
    }

    @Test
    void preserveCodehausAnnotationsIfNotDoublyAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.JsonSerializer.None;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @JsonSerialize(using = None.class)
              class Test {
                @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
                private String first;
              
              }
              """
          )
        );
    }

    @Test
    void preserveCodehausAnnotationsIfNotDoublyAnnotatedCombined() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.JsonSerializer.None;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @JsonSerialize(include = NON_NULL, using = None.class)
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
              class Test {
                @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
                private String first;
              
                @JsonSerialize(include = NON_NULL, using = None.class)
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.JsonSerializer.None.class)
                private String second;
              }
              """,
            """
              import com.fasterxml.jackson.databind.JsonSerializer;
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = JsonSerializer.None.class)
              class Test {
                @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
                private String first;
              
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = JsonSerializer.None.class)
                private String second;
              }
              """
          )
        );
    }

    @Test
    void retainInPresenceOfV2JsonInclude() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import org.codehaus.jackson.map.ser.BeanSerializer;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.JsonSerializer;
              
              @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = JsonSerializer.None.class)
              class Test {
                  @JsonSerialize(using = BeanSerializer.class)
                  @JsonInclude(value = JsonInclude.Include.ALWAYS)
                  private String sixth;
              }
              """
          )
        );
    }
}
