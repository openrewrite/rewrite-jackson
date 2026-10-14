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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RestAssuredJackson3Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion()
            .classpath("jackson-annotations", "jackson-core", "jackson-databind")
            //language=java
            .dependsOn(
              """
                package io.restassured.common.mapper.factory;
                public interface ObjectMapperFactory<T> { T create(java.lang.reflect.Type cls, String charset); }
                """,
              """
                package io.restassured.path.json.mapper.factory;
                public interface Jackson2ObjectMapperFactory extends io.restassured.common.mapper.factory.ObjectMapperFactory<com.fasterxml.jackson.databind.ObjectMapper> {}
                """,
              """
                package io.restassured.path.json.mapper.factory;
                public interface Jackson3ObjectMapperFactory extends io.restassured.common.mapper.factory.ObjectMapperFactory<Object> {}
                """,
              """
                package io.restassured.mapper;
                public enum ObjectMapperType { JACKSON_3, JACKSON_2, JACKSON_1, GSON }
                """,
              """
                package io.restassured.config;
                import io.restassured.mapper.ObjectMapperType;
                import io.restassured.path.json.mapper.factory.*;
                public class ObjectMapperConfig {
                    public ObjectMapperConfig defaultObjectMapperType(ObjectMapperType type) { return this; }
                    public ObjectMapperConfig jackson2ObjectMapperFactory(Jackson2ObjectMapperFactory factory) { return this; }
                    public ObjectMapperConfig jackson3ObjectMapperFactory(Jackson3ObjectMapperFactory factory) { return this; }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void jackson3ObjectMapperFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import io.restassured.config.ObjectMapperConfig;

              class PersonResourceTest {
                  ObjectMapperConfig config() {
                      return new ObjectMapperConfig().jackson2ObjectMapperFactory((type, s) -> new ObjectMapper());
                  }
              }
              """,
            """
              import tools.jackson.databind.json.JsonMapper;
              import io.restassured.config.ObjectMapperConfig;

              class PersonResourceTest {
                  ObjectMapperConfig config() {
                      return new ObjectMapperConfig().jackson3ObjectMapperFactory((type, s) -> new JsonMapper());
                  }
              }
              """
          )
        );
    }

    @Test
    void jackson3ObjectMapperType() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import io.restassured.config.ObjectMapperConfig;
              import io.restassured.mapper.ObjectMapperType;

              class PersonResourceTest {
                  ObjectMapper mapper;

                  ObjectMapperConfig config() {
                      return new ObjectMapperConfig().defaultObjectMapperType(ObjectMapperType.JACKSON_2);
                  }
              }
              """,
            """
              import tools.jackson.databind.ObjectMapper;
              import io.restassured.config.ObjectMapperConfig;
              import io.restassured.mapper.ObjectMapperType;

              class PersonResourceTest {
                  ObjectMapper mapper;

                  ObjectMapperConfig config() {
                      return new ObjectMapperConfig().defaultObjectMapperType(ObjectMapperType.JACKSON_3);
                  }
              }
              """
          )
        );
    }
}
