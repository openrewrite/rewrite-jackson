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

class Jackson3TypeChangesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.jackson.UpgradeJackson_2_3")
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-annotations",
            "jackson-core",
            "jackson-databind",
            "jackson-dataformat-yaml",
            "jackson-dataformat-xml",
            "jackson-dataformat-csv",
            "jackson-dataformat-cbor",
            "jackson-dataformat-avro",
            "jackson-dataformat-smile",
            "jackson-dataformat-ion"));
    }

    @DocumentExample
    @Test
    void jsonFactory() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonFactory;

              class Test {
                  JsonFactory factory = new JsonFactory();
              }
              """,
            """
              import tools.jackson.core.TokenStreamFactory;

              class Test {
                  TokenStreamFactory factory = new TokenStreamFactory();
              }
              """
          )
        );
    }


    @Test
    void jsonDeserializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonDeserializer;

              class CustomDeserializer extends JsonDeserializer<String> {
                  @Override
                  public String deserialize(com.fasterxml.jackson.core.JsonParser p,
                                           com.fasterxml.jackson.databind.DeserializationContext ctxt) {
                      return null;
                  }
              }
              """,
            """
              import tools.jackson.databind.ValueDeserializer;

              class CustomDeserializer extends ValueDeserializer<String> {
                  @Override
                  public String deserialize(tools.jackson.core.JsonParser p,
                                           tools.jackson.databind.DeserializationContext ctxt) {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonSerializer() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializer;

              class CustomSerializer extends JsonSerializer<String> {
                  @Override
                  public void serialize(String value,
                                       com.fasterxml.jackson.core.JsonGenerator gen,
                                       com.fasterxml.jackson.databind.SerializerProvider provider) {
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationContext;
              import tools.jackson.databind.ValueSerializer;

              class CustomSerializer extends ValueSerializer<String> {
                  @Override
                  public void serialize(String value,
                                       tools.jackson.core.JsonGenerator gen,
                                       SerializationContext provider) {
                  }
              }
              """
          )
        );
    }

    @Test
    void jsonSerializable() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JsonSerializable;

              class CustomObject implements JsonSerializable {
                  @Override
                  public void serialize(com.fasterxml.jackson.core.JsonGenerator gen,
                                       com.fasterxml.jackson.databind.SerializerProvider serializers) {
                  }
              }
              """,
            """
              import tools.jackson.databind.JacksonSerializable;
              import tools.jackson.databind.SerializationContext;

              class CustomObject implements JacksonSerializable {
                  @Override
                  public void serialize(tools.jackson.core.JsonGenerator gen,
                                       SerializationContext serializers) {
                  }
              }
              """
          )
        );
    }

    @Test
    void serializerProvider() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.SerializerProvider;

              class Test {
                  void test(SerializerProvider provider) {
                      provider.getConfig();
                  }
              }
              """,
            """
              import tools.jackson.databind.SerializationContext;

              class Test {
                  void test(SerializationContext provider) {
                      provider.getConfig();
                  }
              }
              """
          )
        );
    }

    @Test
    void textNode() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.node.TextNode;

              class Test {
                  TextNode node = TextNode.valueOf("test");
              }
              """,
            """
              import tools.jackson.databind.node.StringNode;

              class Test {
                  StringNode node = StringNode.valueOf("test");
              }
              """
          )
        );
    }

    @Test
    void module() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.Module;

              class CustomModule extends Module {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }
              }
              """,
            """
              import tools.jackson.databind.JacksonModule;

              class CustomModule extends JacksonModule {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }
              }
              """
          )
        );
    }

    @Test
    void yaml() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.yaml.YAMLParser.Feature;
              import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;

              class YamlThings {
                  public final YAMLParser.Feature readFeature = YAMLParser.Feature.EMPTY_STRING_AS_NULL;
                  public final YAMLGenerator.Feature writeFeature = YAMLGenerator.Feature.MINIMIZE_QUOTES;
              }
              """,
            """
              import tools.jackson.dataformat.yaml.YAMLReadFeature;
              import tools.jackson.dataformat.yaml.YAMLWriteFeature;

              class YamlThings {
                  public final YAMLReadFeature readFeature = YAMLReadFeature.EMPTY_STRING_AS_NULL;
                  public final YAMLWriteFeature writeFeature = YAMLWriteFeature.MINIMIZE_QUOTES;
              }
              """
          )
        );
    }

    @Test
    void xml() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
              import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;

              class XmlThings {
                  public final FromXmlParser.Feature readFeature = FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL;
                  public final ToXmlGenerator.Feature writeFeature = ToXmlGenerator.Feature.WRITE_XML_DECLARATION;
              }
              """,
            """
              import tools.jackson.dataformat.xml.XmlReadFeature;
              import tools.jackson.dataformat.xml.XmlWriteFeature;

              class XmlThings {
                  public final XmlReadFeature readFeature = XmlReadFeature.EMPTY_ELEMENT_AS_NULL;
                  public final XmlWriteFeature writeFeature = XmlWriteFeature.WRITE_XML_DECLARATION;
              }
              """
          )
        );
    }

    @Test
    void csv() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.csv.CsvParser;
              import com.fasterxml.jackson.dataformat.csv.CsvGenerator;

              class CsvThings {
                  public final CsvParser.Feature readFeature = CsvParser.Feature.TRIM_SPACES;
                  public final CsvGenerator.Feature writeFeature = CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING;
              }
              """,
            """
              import tools.jackson.dataformat.csv.CsvReadFeature;
              import tools.jackson.dataformat.csv.CsvWriteFeature;

              class CsvThings {
                  public final CsvReadFeature readFeature = CsvReadFeature.TRIM_SPACES;
                  public final CsvWriteFeature writeFeature = CsvWriteFeature.STRICT_CHECK_FOR_QUOTING;
              }
              """
          )
        );
    }

    @Test
    void cbor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

              class CborThings {
                  public final CBORGenerator.Feature writeFeature = CBORGenerator.Feature.WRITE_MINIMAL_INTS;
              }
              """,
            """
              import tools.jackson.dataformat.cbor.CBORWriteFeature;

              class CborThings {
                  public final CBORWriteFeature writeFeature = CBORWriteFeature.WRITE_MINIMAL_INTS;
              }
              """
          )
        );
    }

    @Test
    void avro() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.avro.AvroParser;
              import com.fasterxml.jackson.dataformat.avro.AvroGenerator;

              class AvroThings {
                  public final AvroParser.Feature readFeature = AvroParser.Feature.AVRO_BUFFERING;
                  public final AvroGenerator.Feature writeFeature = AvroGenerator.Feature.AVRO_FILE_OUTPUT;
              }
              """,
            """
              import tools.jackson.dataformat.avro.AvroReadFeature;
              import tools.jackson.dataformat.avro.AvroWriteFeature;

              class AvroThings {
                  public final AvroReadFeature readFeature = AvroReadFeature.AVRO_BUFFERING;
                  public final AvroWriteFeature writeFeature = AvroWriteFeature.AVRO_FILE_OUTPUT;
              }
              """
          )
        );
    }

    @Test
    void smile() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.smile.SmileParser;
              import com.fasterxml.jackson.dataformat.smile.SmileGenerator;

              class SmileThings {
                  public final SmileParser.Feature readFeature = SmileParser.Feature.REQUIRE_HEADER;
                  public final SmileGenerator.Feature writeFeature = SmileGenerator.Feature.WRITE_HEADER;
              }
              """,
            """
              import tools.jackson.dataformat.smile.SmileReadFeature;
              import tools.jackson.dataformat.smile.SmileWriteFeature;

              class SmileThings {
                  public final SmileReadFeature readFeature = SmileReadFeature.REQUIRE_HEADER;
                  public final SmileWriteFeature writeFeature = SmileWriteFeature.WRITE_HEADER;
              }
              """
          )
        );
    }

    @Test
    void ion() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.dataformat.ion.IonParser;
              import com.fasterxml.jackson.dataformat.ion.IonGenerator;

              class IonThings {
                  public final IonParser.Feature readFeature = IonParser.Feature.USE_NATIVE_TYPE_ID;
                  public final IonGenerator.Feature writeFeature = IonGenerator.Feature.USE_NATIVE_TYPE_ID;
              }
              """,
            """
              import tools.jackson.dataformat.ion.IonReadFeature;
              import tools.jackson.dataformat.ion.IonWriteFeature;

              class IonThings {
                  public final IonReadFeature readFeature = IonReadFeature.USE_NATIVE_TYPE_ID;
                  public final IonWriteFeature writeFeature = IonWriteFeature.USE_NATIVE_TYPE_ID;
              }
              """
          )
        );
    }
}
