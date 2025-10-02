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
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.Assertions.java;

class RemoveRedundantJackson3FeatureFlagsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        List<Recipe> recipes = new ArrayList<>();

        // Features enabled by default in Jackson 3
        recipes.add(new RemoveRedundantJackson3FeatureFlags("MapperFeature.SORT_PROPERTIES_ALPHABETICALLY", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("DeserializationFeature.READ_ENUMS_USING_TO_STRING", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("DeserializationFeature.FAIL_ON_TRAILING_TOKENS", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("SerializationFeature.WRITE_ENUMS_USING_TO_STRING", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("CBORReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("CBORWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("XmlWriteFeature.AUTO_DETECT_XSI_TYPE", true));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS", true));

        // Features disabled by default in Jackson 3
        recipes.add(new RemoveRedundantJackson3FeatureFlags("MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("MapperFeature.DEFAULT_VIEW_INCLUSION", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("MapperFeature.USE_GETTERS_AS_SETTERS", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("SerializationFeature.FAIL_ON_EMPTY_BEANS", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS", false));
        recipes.add(new RemoveRedundantJackson3FeatureFlags("SerializationFeature.WRITE_DATES_AS_TIMESTAMPS", false));

        spec.recipes(recipes.toArray(new Recipe[0]))
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

    @Test
    void removeEnableWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.enable(SORT_PROPERTIES_ALPHABETICALLY);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;

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
    void removeDisableWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

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
    void removeConfigureWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(WRITE_DATES_AS_TIMESTAMPS, false);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

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
