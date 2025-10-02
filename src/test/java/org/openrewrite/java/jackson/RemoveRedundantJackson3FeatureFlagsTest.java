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

class RemoveRedundantJackson3FeatureFlagsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantJackson3FeatureFlags())
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations", "jackson-core", "jackson-databind"));
    }

    @DocumentExample
    @Test
    void removeEnableSortPropertiesAlphabetically() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDisableFailOnUnknownProperties() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeEnableReadEnumsUsingToString() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDisableFailOnEmptyBeans() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNonDefaultConfiguration() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      // This disables a feature that is enabled by default in v3, so keep it
                      mapper.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConfigureWithTrueForEnabledByDefault() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConfigureWithFalseForDisabledByDefault() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepConfigureWithNonDefaultValue() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.MapperFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      // This disables a feature that is enabled by default in v3, so keep it
                      mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConfigureReadEnumsUsingToString() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.DeserializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConfigureWriteDatesAsTimestamps() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.SerializationFeature;
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

}
