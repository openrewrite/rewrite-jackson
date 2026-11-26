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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CommentMutatorsInObjectMapperSubclassesTest implements RewriteTest {

    private static final String TODO = "// TODO Jackson 3's ObjectMapper is immutable, so a subclass can no longer configure itself. " +
      "Build a configured `JsonMapper.builder()` and pass it to the `super(..)` constructor, " +
      "see https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md#6-immutability-of-objectmapper-jsonfactory";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CommentMutatorsInObjectMapperSubclasses())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-annotations-2",
            "jackson-core-2",
            "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void commentMutatorsCalledInSubclassConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class TestMapper extends ObjectMapper {
                  TestMapper() {
                      setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                      this.disable(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_GETTERS);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonAutoDetect;
              import com.fasterxml.jackson.annotation.PropertyAccessor;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class TestMapper extends ObjectMapper {
                  TestMapper() {
                      %1$s
                      setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                      %1$s
                      this.disable(com.fasterxml.jackson.databind.MapperFeature.AUTO_DETECT_GETTERS);
                  }
              }
              """.formatted(TODO)
          )
        );
    }

    @Test
    void commentMutatorsInAnonymousSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper mapper = new ObjectMapper() {{
                      setSerializationInclusion(JsonInclude.Include.NON_NULL);
                  }};
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper mapper = new ObjectMapper() {{
                      %s
                      setSerializationInclusion(JsonInclude.Include.NON_NULL);
                  }};
              }
              """.formatted(TODO)
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void leaveMapperConfiguredFromOutside() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      ObjectMapper mapper() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                          return mapper;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void leaveSubclassConfiguringAnotherMapper() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.annotation.JsonInclude;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class TestMapper extends ObjectMapper {
                      static ObjectMapper other() {
                          ObjectMapper other = new ObjectMapper();
                          other.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                          return other;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void leaveSubclassReadingValues() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class TestMapper extends ObjectMapper {
                      String write(Object value) throws Exception {
                          return writeValueAsString(value);
                      }
                  }
                  """
              )
            );
        }
    }
}
