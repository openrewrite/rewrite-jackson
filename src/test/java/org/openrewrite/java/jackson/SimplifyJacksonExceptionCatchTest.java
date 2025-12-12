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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyJacksonExceptionCatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyJacksonExceptionCatch());
    }

    @DocumentExample
    @Test
    void simplifiesJacksonExceptionWithRuntimeException() {
        rewriteRun(
          // Jackson 3 JacksonException stub
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
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
          // Jackson 3 exception stubs
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              package tools.jackson.core;
              public class StreamReadException extends JacksonException {
                  public StreamReadException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              import tools.jackson.core.StreamReadException;

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
    void simplifiesDatabindException() {
        rewriteRun(
          // Jackson 3 exception stubs
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              package tools.jackson.databind;
              import tools.jackson.core.JacksonException;
              public class DatabindException extends JacksonException {
                  public DatabindException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              import tools.jackson.databind.DatabindException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (DatabindException | RuntimeException e) {
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
          // Jackson 3 exception stubs
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              package tools.jackson.core;
              public class StreamReadException extends JacksonException {
                  public StreamReadException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              package tools.jackson.core;
              public class StreamWriteException extends JacksonException {
                  public StreamWriteException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              import tools.jackson.core.StreamReadException;
              import tools.jackson.core.StreamWriteException;

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
          // Jackson 3 JacksonException stub
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
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
    void noChangeWhenRuntimeExceptionNotPresent() {
        rewriteRun(
          // Jackson 3 JacksonException stub
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              package tools.jackson.core;
              public class StreamReadException extends JacksonException {
                  public StreamReadException(String msg) { super(msg); }
              }
              """
          ),
          java(
            """
              import tools.jackson.core.JacksonException;
              import tools.jackson.core.StreamReadException;

              class Test {
                  void doSomething() {
                      try {
                          // some code
                      } catch (JacksonException | StreamReadException e) {
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
          // Jackson 3 JacksonException stub
          java(
            """
              package tools.jackson.core;
              public class JacksonException extends RuntimeException {
                  public JacksonException(String msg) { super(msg); }
              }
              """
          ),
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
