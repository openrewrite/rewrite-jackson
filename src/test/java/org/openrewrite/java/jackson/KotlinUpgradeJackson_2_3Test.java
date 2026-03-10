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
import org.openrewrite.Issue;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

@Issue("https://github.com/openrewrite/rewrite-jackson/issues/89")
class KotlinUpgradeJackson_2_3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void removeRedundantFeatureFlagsFromChain() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3_RemoveRedundantFeatureFlags"),
          //language=kotlin
          kotlin(
            """
              import com.fasterxml.jackson.databind.DeserializationFeature
              import com.fasterxml.jackson.databind.ObjectMapper
              import com.fasterxml.jackson.databind.SerializationFeature
              import java.util.TimeZone

              class Test {
                  fun objectMapper(): ObjectMapper {
                      return ObjectMapper()
                              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                              .setTimeZone(TimeZone.getDefault())
                              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper
              import java.util.TimeZone

              class Test {
                  fun objectMapper(): ObjectMapper {
                      return ObjectMapper()
                              .setTimeZone(TimeZone.getDefault())
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedConfigurationOnObjectMapper() {
        rewriteRun(
          spec -> spec.recipes(
            new UpdateSerializationInclusionConfiguration(),
            new RemoveRedundantFeatureFlags("SerializationFeature.WRITE_DATES_AS_TIMESTAMPS", false),
            new RemoveRedundantFeatureFlags("DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES", false)
          ),
          //language=kotlin
          kotlin(
            """
              import com.fasterxml.jackson.annotation.JsonInclude
              import com.fasterxml.jackson.databind.DeserializationFeature
              import com.fasterxml.jackson.databind.ObjectMapper
              import com.fasterxml.jackson.databind.SerializationFeature
              import java.util.TimeZone

              class Test {
                  fun objectMapper(): ObjectMapper {
                      return ObjectMapper()
                              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                              .setTimeZone(TimeZone.getDefault())
                              .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude
              import com.fasterxml.jackson.databind.ObjectMapper
              import java.util.TimeZone

              class Test {
                  fun objectMapper(): ObjectMapper {
                      return ObjectMapper()
                              .setTimeZone(TimeZone.getDefault())
                              .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                  }
              }
              """
          )
        );
    }

    @Test
    void updateSerializationInclusionOnBuilder() {
        rewriteRun(
          spec -> spec.recipe(new UpdateSerializationInclusionConfiguration()),
          //language=kotlin
          kotlin(
            """
              import com.fasterxml.jackson.annotation.JsonInclude
              import com.fasterxml.jackson.databind.json.JsonMapper

              fun configure(): JsonMapper {
                  return JsonMapper.builder()
                      .serializationInclusion(JsonInclude.Include.NON_NULL)
                      .build()
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude
              import com.fasterxml.jackson.databind.json.JsonMapper

              fun configure(): JsonMapper {
                  return JsonMapper.builder()
                      .changeDefaultPropertyInclusion({ incl ->incl.withContentInclusion(JsonInclude.Include.NON_NULL).withValueInclusion(JsonInclude.Include.NON_NULL)})
                      .build()
              }
              """
          )
        );
    }
}
