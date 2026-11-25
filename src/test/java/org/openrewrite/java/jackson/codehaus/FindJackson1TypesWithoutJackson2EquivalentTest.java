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
package org.openrewrite.java.jackson.codehaus;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class FindJackson1TypesWithoutJackson2EquivalentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new FindJackson1TypesWithoutJackson2Equivalent())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-asl", "jackson-mapper-asl", "jackson-databind-2", "jackson-core-2"));
    }

    @DocumentExample
    @Test
    void marksRemainingJackson1Types() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.codehaus.jackson.map.ext.JodaSerializers;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class Test {
                  ObjectMapper mapper = new ObjectMapper();
                  CustomSerializerFactory factory = new CustomSerializerFactory();
                  Object serializers = new JodaSerializers().provide();
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.codehaus.jackson.map.ext.JodaSerializers;
              import org.codehaus.jackson.map.ser.CustomSerializerFactory;

              class Test {
                  ObjectMapper mapper = new ObjectMapper();
                  /*~~(Jackson 2 has no CustomSerializerFactory: add the serializers to a com.fasterxml.jackson.databind.module.SimpleModule with addSerializer and register it with ObjectMapper.registerModule)~~>*/CustomSerializerFactory factory = new /*~~(Jackson 2 has no CustomSerializerFactory: add the serializers to a com.fasterxml.jackson.databind.module.SimpleModule with addSerializer and register it with ObjectMapper.registerModule)~~>*/CustomSerializerFactory();
                  Object serializers = new /*~~(Jackson 2 handles Joda types in jackson-datatype-joda: register com.fasterxml.jackson.datatype.joda.JodaModule with ObjectMapper.registerModule, or name its serializers in com.fasterxml.jackson.datatype.joda.ser and .deser)~~>*/JodaSerializers().provide();
              }
              """
          )
        );
    }

    @Test
    void noJackson1TypesNoMarkers() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  ObjectMapper mapper = new ObjectMapper();
              }
              """
          )
        );
    }

    @Test
    void marksDependencyClassBuiltOnJackson1() {
        rewriteRun(
          // a Jackson 2 SimpleModule takes no Jackson 1 deserializer, which is the compile error the marker explains
          spec -> spec.typeValidationOptions(TypeValidation.none()).parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jackson-core-asl", "jackson-mapper-asl", "jackson-databind-2", "jackson-core-2")
            //language=java
            .dependsOn(
              """
                package com.acme.lib;
                public class Route {}
                """,
              """
                package com.acme.lib;
                import org.codehaus.jackson.JsonParser;
                import org.codehaus.jackson.map.DeserializationContext;
                import org.codehaus.jackson.map.JsonDeserializer;
                public class RouteDeserializer extends JsonDeserializer<Route> {
                    @Override
                    public Route deserialize(JsonParser p, DeserializationContext ctxt) {
                        return null;
                    }
                }
                """
            )),
          //language=java
          java(
            """
              import com.acme.lib.Route;
              import com.acme.lib.RouteDeserializer;
              import com.fasterxml.jackson.databind.module.SimpleModule;

              class Test {
                  void configure(SimpleModule module) {
                      module.addDeserializer(Route.class, new RouteDeserializer());
                  }
              }
              """,
            """
              import com.acme.lib.Route;
              import com.acme.lib.RouteDeserializer;
              import com.fasterxml.jackson.databind.module.SimpleModule;

              class Test {
                  void configure(SimpleModule module) {
                      module.addDeserializer(Route.class, /*~~(com.acme.lib.RouteDeserializer is built on Jackson 1 (it extends org.codehaus.jackson.map.JsonDeserializer), and Jackson 2 can't use it: upgrade the dependency that declares it to a version built on Jackson 2, or replace it with a class of your own)~~>*/new RouteDeserializer());
                  }
              }
              """
          )
        );
    }
}
