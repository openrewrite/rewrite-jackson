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

class RelocatedFeatureConstantsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3_RelocatedFeatureConstants")
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"));
    }

    @DocumentExample
    @Test
    void serializationFeatureToDateTimeFeature() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializationFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.databind.cfg.DateTimeFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
                  }
              }
              """
          )
        );
    }

    @Test
    void deserializationFeatureToDateTimeFeature() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.databind.cfg.DateTimeFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
                  }
              }
              """
          )
        );
    }

    @Test
    void serializationFeatureToEnumFeature() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializationFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.databind.cfg.EnumFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(EnumFeature.WRITE_ENUMS_USING_INDEX);
                  }
              }
              """
          )
        );
    }

    @Test
    void deserializationFeatureToEnumFeature() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import tools.jackson.databind.cfg.EnumFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedNonRelocatedConstant() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.SerializationFeature;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(SerializationFeature.INDENT_OUTPUT);
                  }
              }
              """
          )
        );
    }
}
