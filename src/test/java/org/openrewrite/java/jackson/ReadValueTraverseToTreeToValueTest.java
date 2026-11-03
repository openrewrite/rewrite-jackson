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

class ReadValueTraverseToTreeToValueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReadValueTraverseToTreeToValue())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-annotations-2",
            "jackson-databind-2",
            "jackson-core-2"));
    }

    @DocumentExample
    @Test
    void readValueOfTraverse() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.util.Map;

              class Test {
                  String load(ObjectMapper mapper, JsonNode node) throws IOException {
                      return mapper.readValue(node.traverse(), String.class);
                  }

                  Object load(ObjectMapper mapper, Map.Entry<String, JsonNode> entry) throws Exception {
                      return mapper.readValue(
                              entry.getValue().traverse(),
                              Class.forName(entry.getKey())
                      );
                  }

                  Object load(ObjectMapper mapper, JsonNode node, JavaType type) throws IOException {
                      return mapper.readValue(node.traverse(), type);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.util.Map;

              class Test {
                  String load(ObjectMapper mapper, JsonNode node) throws IOException {
                      return mapper.treeToValue(node, String.class);
                  }

                  Object load(ObjectMapper mapper, Map.Entry<String, JsonNode> entry) throws Exception {
                      return mapper.treeToValue(
                              entry.getValue(),
                              Class.forName(entry.getKey())
                      );
                  }

                  Object load(ObjectMapper mapper, JsonNode node, JavaType type) throws IOException {
                      return mapper.treeToValue(node, type);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForOtherParsers() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.JsonParser;
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.JsonNode;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.util.List;

              class Test {
                  String load(ObjectMapper mapper, JsonParser parser) throws IOException {
                      return mapper.readValue(parser, String.class);
                  }

                  String loadWithCodec(ObjectMapper mapper, JsonNode node) throws IOException {
                      return mapper.readValue(node.traverse(mapper), String.class);
                  }

                  List<String> loadList(ObjectMapper mapper, JsonNode node) throws IOException {
                      return mapper.readValue(node.traverse(), new TypeReference<List<String>>() {});
                  }
              }
              """
          )
        );
    }
}
