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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseFormatAlignedObjectMappersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseFormatAlignedObjectMappers())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-2",
              "jackson-databind-2",
              "jackson-dataformat-xml-2"));
    }

    @DocumentExample
    @Test
    void jsonMapper() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.core.JsonFactory;

              class A {
                  ObjectMapper mapper = new ObjectMapper(new JsonFactory());
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class A {
                  ObjectMapper mapper = new JsonMapper();
              }
              """
          )
        );
    }

    @Test
    void removeObjectMapperImportOnceUnused() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class A {
                  String serialize(Object o) throws Exception {
                      return new ObjectMapper().writeValueAsString(o);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class A {
                  String serialize(Object o) throws Exception {
                      return new JsonMapper().writeValueAsString(o);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      "avro.AvroFactory, avro.AvroMapper",
      "cbor.CBORFactory, cbor.databind.CBORMapper",
      "csv.CsvFactory, csv.CsvMapper",
      "ion.IonFactory, ion.IonObjectMapper",
      "smile.SmileFactory, smile.databind.SmileMapper",
      "xml.XmlFactory, xml.XmlMapper",
      "yaml.YAMLFactory, yaml.YAMLMapper"
    })
    void formatFactoryReplacedByFormatMapper(String factory, String mapper) {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-core-2", "jackson-databind-2", "jackson-dataformat-" + factory.substring(0, factory.indexOf('.')) + "-2")),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.dataformat.%s;

              class A {
                  ObjectMapper mapper = new ObjectMapper(new %s());
              }
              """.formatted(factory, factory.substring(factory.lastIndexOf('.') + 1)),
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.dataformat.%s;

              class A {
                  ObjectMapper mapper = new %s();
              }
              """.formatted(mapper, mapper.substring(mapper.lastIndexOf('.') + 1))
          )
        );
    }

    @Test
    void configuredFactoryPassedToFormatMapper() {
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "jackson-core-2", "jackson-databind-2", "jackson-dataformat-yaml-2"))
            .recipeQualityValidation(RecipeQualityValidation.builder().compilableOutput(true).build()),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
              import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

              class A {
                  ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
              import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
              import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

              class A {
                  ObjectMapper mapper = new YAMLMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
              }
              """
          )
        );
    }

    @Test
    void noArgObjectMapperToJsonMapper() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class A {
                  ObjectMapper mapper = new ObjectMapper();
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class A {
                  ObjectMapper mapper = new JsonMapper();
              }
              """
          )
        );
    }
}
