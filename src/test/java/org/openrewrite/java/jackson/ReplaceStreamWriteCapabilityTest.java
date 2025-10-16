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

class ReplaceStreamWriteCapabilityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceStreamWriteCapability())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-core"));
    }

    @DocumentExample
    @Test
    void replaceCanWriteBinaryNatively() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  boolean checkCapability(JsonGenerator generator) {
                      return generator.canWriteBinaryNatively();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.StreamWriteCapability;

              class Test {
                  boolean checkCapability(JsonGenerator generator) {
                      return generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCanWriteFormattedNumbers() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  boolean checkCapability(JsonGenerator generator) {
                      return generator.canWriteFormattedNumbers();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.StreamWriteCapability;

              class Test {
                  boolean checkCapability(JsonGenerator generator) {
                      return generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_FORMATTED_NUMBERS);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceBothCapabilityMethods() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void checkCapabilities(JsonGenerator generator) {
                      if (generator.canWriteBinaryNatively()) {
                          System.out.println("Can write binary");
                      }
                      if (generator.canWriteFormattedNumbers()) {
                          System.out.println("Can write formatted numbers");
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.StreamWriteCapability;

              class Test {
                  void checkCapabilities(JsonGenerator generator) {
                      if (generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY)) {
                          System.out.println("Can write binary");
                      }
                      if (generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_FORMATTED_NUMBERS)) {
                          System.out.println("Can write formatted numbers");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceInConditionalExpression() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void writeData(JsonGenerator generator, byte[] data) {
                      String message = generator.canWriteBinaryNatively() ? "native" : "base64";
                      System.out.println(message);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.StreamWriteCapability;

              class Test {
                  void writeData(JsonGenerator generator, byte[] data) {
                      String message = generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY) ? "native" : "base64";
                      System.out.println(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNegatedCapabilityCheck() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.JsonGenerator;

              class Test {
                  void checkCapability(JsonGenerator generator) {
                      if (!generator.canWriteBinaryNatively()) {
                          System.out.println("Cannot write binary natively");
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonGenerator;
              import com.fasterxml.jackson.core.StreamWriteCapability;

              class Test {
                  void checkCapability(JsonGenerator generator) {
                      if (!generator.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY)) {
                          System.out.println("Cannot write binary natively");
                      }
                  }
              }
              """
          )
        );
    }
}
