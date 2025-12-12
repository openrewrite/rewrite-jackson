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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyJacksonExceptionCatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyJacksonExceptionCatch())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-3"));
    }

    @DocumentExample
    @Test
    void simplifiesJacksonExceptionWithRuntimeException() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.JacksonException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (JacksonException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """,
            """
              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifiesStreamReadException() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.exc.StreamReadException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (StreamReadException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """,
            """
              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifiesMultipleJacksonExceptions() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.exc.StreamReadException;
              import tools.jackson.core.exc.StreamWriteException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (StreamReadException | StreamWriteException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """,
            """
              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesOtherExceptionsInMultiCatch() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.JacksonException;
              import java.io.IOException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (IOException | JacksonException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (IOException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForSingleExceptionCatch() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.JacksonException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (JacksonException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNoJacksonException() {
        rewriteRun(
          java(
            """
              import java.io.IOException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (IOException | RuntimeException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }
}
