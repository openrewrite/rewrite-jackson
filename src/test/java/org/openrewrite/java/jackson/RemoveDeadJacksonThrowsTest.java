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

class RemoveDeadJacksonThrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.jackson.RemoveDeadJacksonThrows")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2", "jackson-core-2", "jackson-databind-2",
              "jackson-core-3", "jackson-databind-3"));
    }

    @DocumentExample
    @Test
    void removesJackson3JacksonException() {
        rewriteRun(
          //language=java
          java(
            """
              import tools.jackson.core.JacksonException;

              class JsonUtil {
                  String toJson(Object obj) throws JacksonException {
                      return obj.toString();
                  }
              }
              """,
            """
              class JsonUtil {
                  String toJson(Object obj) {
                      return obj.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesJackson3DatabindException() {
        rewriteRun(
          //language=java
          java(
            """
              import tools.jackson.databind.DatabindException;

              class JsonUtil {
                  void write(Object obj) throws DatabindException {
                  }
              }
              """,
            """
              class JsonUtil {
                  void write(Object obj) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesJackson2ProcessingException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonProcessingException;

              class JsonUtil {
                  String toJson(Object obj) throws JsonProcessingException {
                      return obj.toString();
                  }
              }
              """,
            """
              class JsonUtil {
                  String toJson(Object obj) {
                      return obj.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesJackson2MappingException() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonMappingException;

              class JsonUtil {
                  void parse(String json) throws JsonMappingException {
                  }
              }
              """,
            """
              class JsonUtil {
                  void parse(String json) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDatabindExceptionFromConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import tools.jackson.databind.DatabindException;

              class JsonFactory {
                  JsonFactory() throws DatabindException {
                  }
              }
              """,
            """
              class JsonFactory {
                  JsonFactory() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesJackson2ProcessingExceptionFromInterfaceMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonProcessingException;

              interface JsonProcessor {
                  String process(byte[] data) throws JsonProcessingException;
              }
              """,
            """
              interface JsonProcessor {
                  String process(byte[] data);
              }
              """
          )
        );
    }

    @Test
    void removesJacksonThrowsButKeepsIoExceptionAndCustom() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;
              public class BusinessException extends Exception {}
              """
          ),
          //language=java
          java(
            """
              import tools.jackson.databind.DatabindException;
              import com.example.BusinessException;
              import java.io.IOException;

              class JsonUtil {
                  void writeToFile(Object obj) throws IOException, DatabindException, BusinessException {
                  }
              }
              """,
            """
              import com.example.BusinessException;
              import java.io.IOException;

              class JsonUtil {
                  void writeToFile(Object obj) throws IOException, BusinessException {
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesNonJacksonThrowsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class JsonUtil {
                  void writeToFile() throws IOException {
                  }
                  void compute() throws RuntimeException {
                  }
                  void plain() {
                  }
              }
              """
          )
        );
    }

    @Test
    void userDefinedExceptionSharingSimpleNameNotRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.exc;
              public class DatabindException extends Exception {}
              """
          ),
          //language=java
          java(
            """
              import com.example.exc.DatabindException;

              class MyService {
                  void handle() throws DatabindException {
                  }
              }
              """
          )
        );
    }
}
