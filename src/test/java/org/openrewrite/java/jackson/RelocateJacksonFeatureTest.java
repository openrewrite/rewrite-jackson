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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RelocateJacksonFeatureTest {

    @Nested
    class EnumFeatures implements RewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3_RelocateFeatures")
              .parser(JavaParser.fromJavaVersion().classpathFromResources(
                new InMemoryExecutionContext(), "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
        }

        @DocumentExample
        @Test
        void configureDeserializationFeatureReadEnumsUsingToString() {
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
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(EnumFeature.READ_ENUMS_USING_TO_STRING, true);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void enableSerializationFeatureWriteEnumsUsingToString() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void disableDeserializationFeatureFailOnNumbersForEnums() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeForNonRelocatedFeature() {
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
                  """
              )
            );
        }

        @Test
        void mixedRelocatedAndNonRelocatedFeatures() {
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
                          mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                          mapper.configure(EnumFeature.READ_ENUMS_USING_TO_STRING, true);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeEnumsUsingIndex() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(EnumFeature.WRITE_ENUMS_USING_INDEX);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void readUnknownEnumValuesAsNull() {
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
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

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
        void readUnknownEnumValuesUsingDefaultValue() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeEnumKeysUsingIndex() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_ENUM_KEYS_USING_INDEX);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(EnumFeature.WRITE_ENUM_KEYS_USING_INDEX);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fullyQualifiedReference() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.READ_ENUMS_USING_TO_STRING, true);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.EnumFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(EnumFeature.READ_ENUMS_USING_TO_STRING, true);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class DateTimeFeatures implements RewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3_RelocateFeatures")
              .parser(JavaParser.fromJavaVersion().classpathFromResources(
                new InMemoryExecutionContext(), "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
        }

        @DocumentExample
        @Test
        void configureWriteDatesAsTimestamps() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void disableWriteDurationsAsTimestamps() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void enableAdjustDatesToContextTimeZone() {
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
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

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
        void readDateTimestampsAsNanoseconds() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.DeserializationFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, true);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, true);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeForNonRelocatedFeature() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void mixedRelocatedAndNonRelocatedFeatures() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                          mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                          mapper.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeDatesWithZoneId() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeDateKeysAsTimestamps() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(DateTimeFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeDateTimestampsAsNanoseconds() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void writeDatesWithContextTimeZone() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;
                  import com.fasterxml.jackson.databind.SerializationFeature;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.enable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fullyQualifiedReference() {
            rewriteRun(
              //language=java
              java(
                """
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """,
                """
                  import com.fasterxml.jackson.databind.DateTimeFeature;
                  import com.fasterxml.jackson.databind.ObjectMapper;

                  class Test {
                      void configure() {
                          ObjectMapper mapper = new ObjectMapper();
                          mapper.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                      }
                  }
                  """
              )
            );
        }
    }
}
