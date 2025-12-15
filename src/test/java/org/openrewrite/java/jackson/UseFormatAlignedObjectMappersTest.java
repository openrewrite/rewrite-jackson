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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
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
              "jackson-core-2", "jackson-databind-2", "jackson-dataformat-yaml-2", "jackson-dataformat-xml-2"));
    }

    @Test
    void keepObjectMapper() {
        rewriteRun(
          java(
            """
            import com.fasterxml.jackson.databind.ObjectMapper;

            class A {
                ObjectMapper mapper = new ObjectMapper();
            }
            """
          )
        );
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
    void xmlMapper() {
        rewriteRun(
          java(
            """
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlFactory;

            class A {
                ObjectMapper mapper = new ObjectMapper(new XmlFactory());
            }
            """,
            """
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.dataformat.xml.XmlMapper;

            class A {
                ObjectMapper mapper = new XmlMapper();
            }
            """
          )
        );
    }

    @Test
    void ymlMapper() {
        rewriteRun(
          java(
            """
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

            class A {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            }
            """,
            """
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

            class A {
                ObjectMapper mapper = new YAMLMapper();
            }
            """
          )
        );
    }
}
