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

class UpgradeJackson_2_3_ModernizeJacksonCoreFeaturesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3_ModernizeJacksonCoreFeatures")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(),
            "jackson-annotations-2",
            "jackson-core-2",
            "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void allowCommentsRenamedToAllowJavaComments() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonParser;

              class A {
                  JsonFactory factory = new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS);
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.json.JsonReadFeature;

              class A {
                  JsonFactory factory = new JsonFactory().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS);
              }
              """
          )
        );
    }

    @Test
    void quoteFieldNamesStaysJackson2ModernName() {
        // Modernize step rewrites JsonGenerator.Feature.QUOTE_FIELD_NAMES to the Jackson 2
        // *modern* JsonWriteFeature.QUOTE_FIELD_NAMES (same constant name on the new enum).
        // The Jackson 3 rename to QUOTE_PROPERTY_NAMES happens later, in
        // UpgradeJackson_2_3_RelocatedFeatureConstants.
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonGenerator;

              class A {
                  JsonFactory factory = new JsonFactory().enable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.json.JsonWriteFeature;

              class A {
                  JsonFactory factory = new JsonFactory().enable(JsonWriteFeature.QUOTE_FIELD_NAMES);
              }
              """
          )
        );
    }

    @Test
    void streamFeatureRelocation() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.JsonGenerator;

              class A {
                  JsonFactory factory = new JsonFactory()
                          .enable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                          .enable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
              }
              """,
            """
              import com.fasterxml.jackson.core.JsonFactory;
              import com.fasterxml.jackson.core.StreamReadFeature;
              import com.fasterxml.jackson.core.StreamWriteFeature;

              class A {
                  JsonFactory factory = new JsonFactory()
                          .enable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                          .enable(StreamWriteFeature.AUTO_CLOSE_TARGET);
              }
              """
          )
        );
    }
}
