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

class FindJsonSetterNullsAsEmptyCollectionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJsonSetterNullsAsEmptyCollections())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void findMapField() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  /*~~(Verify this field should be serialized; `@JsonIgnore` may have been removed here)~~>*/@JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """
          )
        );
    }

    @Test
    void findCollectionField() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.HashSet;
              import java.util.Set;

              class Rule {}

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Set<Rule> rules = new HashSet<>();
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.HashSet;
              import java.util.Set;

              class Rule {}

              class Model {
                  /*~~(Verify this field should be serialized; `@JsonIgnore` may have been removed here)~~>*/@JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Set<Rule> rules = new HashSet<>();
              }
              """
          )
        );
    }

    @Test
    void doNotFindFieldStillCarryingJsonIgnore() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonIgnore
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """
          )
        );
    }

    @Test
    void doNotFindOtherNullsHandling() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonSetter(nulls = Nulls.SKIP)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """
          )
        );
    }

    @Test
    void doNotFindFieldWithoutEmptyCollectionInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;
              import java.util.HashMap;
              import java.util.Map;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> withCapacity = new HashMap<>(16);

                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> withoutInitializer;
              }
              """
          )
        );
    }

    @Test
    void doNotFindNonCollectionField() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private String name = new String();
              }
              """
          )
        );
    }
}
