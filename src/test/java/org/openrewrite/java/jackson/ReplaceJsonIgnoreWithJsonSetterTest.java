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

class ReplaceJsonIgnoreWithJsonSetterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceJsonIgnoreWithJsonSetter())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-annotations"));
    }

    @DocumentExample
    @Test
    void replaceJsonIgnoreOnMapFieldWithEmptyLinkedHashMap() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonIgnore
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;

              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """
          )
        );
    }

    @Test
    void replaceJsonIgnoreOnListFieldWithEmptyArrayList() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.ArrayList;
              import java.util.List;

              class Model {
                  @JsonIgnore
                  private List<String> items = new ArrayList<>();
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;

              import java.util.ArrayList;
              import java.util.List;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private List<String> items = new ArrayList<>();
              }
              """
          )
        );
    }

    @Test
    void mixedFieldsOnlyTransformsMapWithInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonIgnore
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();

                  @JsonIgnore
                  private String secret;
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import com.fasterxml.jackson.annotation.JsonSetter;
              import com.fasterxml.jackson.annotation.Nulls;

              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonSetter(nulls = Nulls.AS_EMPTY)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();

                  @JsonIgnore
                  private String secret;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonCollectionField() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;

              class Model {
                  @JsonIgnore
                  private String password;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMapFieldWithoutInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.Map;

              class Model {
                  @JsonIgnore
                  private Map<String, Object> data;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMapFieldWithNonEmptyConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.HashMap;
              import java.util.Map;

              class Model {
                  @JsonIgnore
                  private Map<String, Object> data = new HashMap<>(16);
              }
              """
          )
        );
    }

    @Test
    void doNotChangeJsonIgnoreFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonIgnore;
              import java.util.LinkedHashMap;
              import java.util.Map;

              class Model {
                  @JsonIgnore(false)
                  private Map<String, Object> additionalProperties = new LinkedHashMap<>();
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFieldAlreadyHavingJsonSetter() {
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
}
