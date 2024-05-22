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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JsonIncludeAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JsonIncludeAnnotation());
    }

    @Test
    @DocumentExample
    void swapAnnotationAndArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.codehaus.jackson.map.annotate.JsonSerialize;
              import static org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion.NON_NULL;
              
              @JsonSerialize(include = NON_NULL)
              class Test {
                @JsonSerialize(include = NON_NULL)
                Object field;
                @JsonSerialize(include = NON_NULL)
                void method() {}
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
              
              @JsonInclude(NON_NULL)
              class Test {
                @JsonInclude(NON_NULL)
                Object field;
                @JsonInclude(NON_NULL)
                void method() {}
              }
              """
          )
        );
    }

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
              import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
              
              @JsonSerialize(using = None.class)
              @JsonInclude(NON_NULL)
              class Test {
              }
              """
          )
        );
    }
}
