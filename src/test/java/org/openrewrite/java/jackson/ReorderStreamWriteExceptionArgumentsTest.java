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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/moderneinc/customer-requests/issues/2084")
class ReorderStreamWriteExceptionArgumentsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind"))
          .recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3");
    }

    @DocumentExample
    @Test
    void reorderStringAndGenerator() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerationException;
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void example(JsonGenerator gen) {
                      throw new JsonGenerationException("message", gen);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.core.exc.StreamWriteException;

              class Test {
                  void example(JsonGenerator gen) {
                      throw new StreamWriteException(gen, "message");
                  }
              }
              """
          )
        );
    }

    @Test
    void reorderThrowableAndGenerator() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerationException;
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void example(JsonGenerator gen, Throwable cause) {
                      throw new JsonGenerationException(cause, gen);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.core.exc.StreamWriteException;

              class Test {
                  void example(JsonGenerator gen, Throwable cause) {
                      throw new StreamWriteException(gen, cause);
                  }
              }
              """
          )
        );
    }

    @Test
    void reorderThreeArgs() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerationException;
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void example(JsonGenerator gen, Throwable cause) {
                      throw new JsonGenerationException("message", cause, gen);
                  }
              }
              """,
            """
              import tools.jackson.core.JsonGenerator;
              import tools.jackson.core.exc.StreamWriteException;

              class Test {
                  void example(JsonGenerator gen, Throwable cause) {
                      throw new StreamWriteException(gen, "message", cause);
                  }
              }
              """
          )
        );
    }
}
