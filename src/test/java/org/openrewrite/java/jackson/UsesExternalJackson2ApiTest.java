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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

class UsesExternalJackson2ApiTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UsesExternalJackson2Api())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "azure-core-serializer-json-jackson", "jackson-databind-2", "jackson-core-2", "jackson-annotations"));
    }

    @Test
    void actualAzureBoundaryMatchesBuildFilesToo() {
        rewriteRun(
          java(
            """
              import com.azure.core.serializer.json.jackson.JacksonJsonSerializerBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;
              class AzureConfig {
                  JacksonJsonSerializerBuilder serializer(ObjectMapper mapper) {
                      return new JacksonJsonSerializerBuilder().serializer(mapper);
                  }
              }
              """,
            """
              /*~~>*/import com.azure.core.serializer.json.jackson.JacksonJsonSerializerBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;
              class AzureConfig {
                  JacksonJsonSerializerBuilder serializer(ObjectMapper mapper) {
                      return new JacksonJsonSerializerBuilder().serializer(mapper);
                  }
              }
              """),
          text("build file", "~~>build file", s -> s.path("build.gradle"))
        );
    }

    @Test
    void ownedApisAndAnonymousSubclassesDoNotPin() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Example {
                  ObjectMapper mapper() { return new ObjectMapper(); }
                  ObjectMapper call() { return mapper(); }
                  TypeReference<String> type = new TypeReference<String>() {};
              }
              """),
          text("build file", s -> s.path("build.gradle"))
        );
    }

    @Test
    void inferredGenericSignatureDoesNotPin() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.util.Collections;
              import java.util.List;
              class Example {
                  List<ObjectMapper> mappers = Collections.singletonList(new ObjectMapper());
              }
              """),
          text("build file", s -> s.path("build.gradle"))
        );
    }
    @Test
    void declarativePreconditionRoutesBuildFileFromJavaEvidence() {
        rewriteRun(
          spec -> spec.recipeFromYaml("""
            type: specs.openrewrite.org/v1beta/recipe
            name: test.RetainJackson2
            displayName: Test retention routing
            description: Test repository-wide scanning preconditions.
            preconditions:
              - org.openrewrite.java.jackson.UsesExternalJackson2Api
            recipeList:
              - org.openrewrite.text.FindAndReplace:
                  find: build file
                  replace: pinned build file
            """, "test.RetainJackson2"),
          text("build file", "pinned build file", source -> source.path("build.gradle")),
          java(
            """
              import com.azure.core.serializer.json.jackson.JacksonJsonSerializerBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;
              class AzureConfig {
                  JacksonJsonSerializerBuilder serializer(ObjectMapper mapper) {
                      return new JacksonJsonSerializerBuilder().serializer(mapper);
                  }
              }
              """)
        );
    }

    @Test
    void negativePreconditionMatchesBuildFilesWithoutBoundary() {
        rewriteRun(
          spec -> spec.recipe(new DoesNotUseExternalJackson2Api()),
          text("build file", "~~>build file", source -> source.path("build.gradle"))
        );
    }

}
