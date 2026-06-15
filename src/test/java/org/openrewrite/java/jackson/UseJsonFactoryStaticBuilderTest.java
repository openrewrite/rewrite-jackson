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

class UseJsonFactoryStaticBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseJsonFactoryStaticBuilder())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-3"));
    }

    @DocumentExample
    @Test
    void bareBuilderCtorReplacedWithStaticBuilder() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.json.JsonFactory;
              import tools.jackson.core.json.JsonFactoryBuilder;

              class A {
                  JsonFactory factory = new JsonFactoryBuilder().build();
              }
              """,
            """
              import tools.jackson.core.json.JsonFactory;

              class A {
                  JsonFactory factory = JsonFactory.builder().build();
              }
              """
          )
        );
    }

    @Test
    void preservesFluentChain() {
        rewriteRun(
          java(
            """
              import tools.jackson.core.StreamReadConstraints;
              import tools.jackson.core.json.JsonFactory;
              import tools.jackson.core.json.JsonFactoryBuilder;

              class A {
                  JsonFactory create(StreamReadConstraints c) {
                      return new JsonFactoryBuilder()
                              .streamReadConstraints(c)
                              .build();
                  }
              }
              """,
            """
              import tools.jackson.core.StreamReadConstraints;
              import tools.jackson.core.json.JsonFactory;

              class A {
                  JsonFactory create(StreamReadConstraints c) {
                      return JsonFactory.builder()
                              .streamReadConstraints(c)
                              .build();
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesSingleArgCtorAlone() {
        // The one-arg `new JsonFactoryBuilder(existingFactory)` is equivalent to
        // `existingFactory.rebuild()`; rewriting it would change which factory's baseline is
        // carried into the new chain, so we deliberately leave it to a separate recipe.
        rewriteRun(
          java(
            """
              import tools.jackson.core.json.JsonFactory;
              import tools.jackson.core.json.JsonFactoryBuilder;

              class A {
                  JsonFactory recustomize(JsonFactory base) {
                      return new JsonFactoryBuilder(base).build();
                  }
              }
              """
          )
        );
    }
}
