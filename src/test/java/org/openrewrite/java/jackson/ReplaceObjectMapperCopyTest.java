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

class ReplaceObjectMapperCopyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceObjectMapperCopy())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),"jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void replaceMapperCopy() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper copy(ObjectMapper mapper) {
                      return mapper.copy();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper copy(ObjectMapper mapper) {
                      return mapper.rebuild().build();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMapperCopyWithChaining() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper copy(ObjectMapper mapper) {
                      return mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper copy(ObjectMapper mapper) {
                      return mapper.rebuild().build().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMapperCopyAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure(ObjectMapper mapper) {
                      ObjectMapper copy = mapper.copy();
                      copy.findAndRegisterModules();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure(ObjectMapper mapper) {
                      ObjectMapper copy = mapper.rebuild().build();
                      copy.findAndRegisterModules();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForNewObjectMapper() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper create() {
                      return new ObjectMapper();
                  }
              }
              """
          )
        );
    }
}
