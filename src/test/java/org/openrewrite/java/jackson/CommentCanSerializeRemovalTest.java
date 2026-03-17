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

class CommentCanSerializeRemovalTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.CommentCanSerializeRemoval")
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    @DocumentExample
    @Test
    void addCommentToCanSerialize() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      boolean canDo = mapper.canSerialize(String.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      boolean canDo = /* TODO canSerialize was removed in Jackson 3 with no replacement (see https://github.com/FasterXML/jackson-databind/issues/1917). Attempt serialization/deserialization and catch exceptions instead. */ mapper.canSerialize(String.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void addCommentToCanDeserialize() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper, JavaType type) {
                      boolean canDo = mapper.canDeserialize(type);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper, JavaType type) {
                      boolean canDo = /* TODO canDeserialize was removed in Jackson 3 with no replacement (see https://github.com/FasterXML/jackson-databind/issues/1917). Attempt serialization/deserialization and catch exceptions instead. */ mapper.canDeserialize(type);
                  }
              }
              """
          )
        );
    }

    @Test
    void canSerializeInIfCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      if (mapper.canSerialize(String.class)) {
                          System.out.println("yes");
                      }
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      if (/* TODO canSerialize was removed in Jackson 3 with no replacement (see https://github.com/FasterXML/jackson-databind/issues/1917). Attempt serialization/deserialization and catch exceptions instead. */mapper.canSerialize(String.class)) {
                          System.out.println("yes");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithoutMatchingMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      mapper.getTypeFactory();
                  }
              }
              """
          )
        );
    }

    @Test
    void idempotent() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void method(ObjectMapper mapper) {
                      boolean canDo = /* TODO canSerialize was removed in Jackson 3 with no replacement (see https://github.com/FasterXML/jackson-databind/issues/1917). Attempt serialization/deserialization and catch exceptions instead. */ mapper.canSerialize(String.class);
                  }
              }
              """
          )
        );
    }
}
