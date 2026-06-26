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

class ReadValueUrlToOpenStreamTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReadValueUrlToOpenStream())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "jackson-annotations-2", "jackson-databind-2", "jackson-core-2"));
    }

    @DocumentExample
    @Test
    void readValueUrlClass() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper, URL url) throws IOException {
                      return mapper.readValue(url, Object.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper, URL url) throws IOException {
                      return mapper.readValue(url.openStream(), Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void readValueUrlJavaType() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper, URL url, JavaType type) throws IOException {
                      return mapper.readValue(url, type);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.JavaType;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper, URL url, JavaType type) throws IOException {
                      return mapper.readValue(url.openStream(), type);
                  }
              }
              """
          )
        );
    }

    @Test
    void readValueUrlTypeReference() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;
              import java.util.List;

              class Test {
                  List<String> load(ObjectMapper mapper, URL url) throws IOException {
                      return mapper.readValue(url, new TypeReference<List<String>>() {});
                  }
              }
              """,
            """
              import com.fasterxml.jackson.core.type.TypeReference;
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.URL;
              import java.util.List;

              class Test {
                  List<String> load(ObjectMapper mapper, URL url) throws IOException {
                      return mapper.readValue(url.openStream(), new TypeReference<List<String>>() {
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void readValueUrlFromInlineConstructor() {
        // First argument is `new URL(...)` rather than a variable — still a URL-typed expression,
        // so the recipe should wrap it just the same.
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.MalformedURLException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper) throws IOException, MalformedURLException {
                      return mapper.readValue(new URL("https://example.com/data.json"), Object.class);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.IOException;
              import java.net.MalformedURLException;
              import java.net.URL;

              class Test {
                  Object load(ObjectMapper mapper) throws IOException, MalformedURLException {
                      return mapper.readValue(new URL("https://example.com/data.json").openStream(), Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveOtherReadValueOverloadsAlone() {
        // None of the non-URL overloads should be touched.
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.io.File;
              import java.io.IOException;
              import java.io.InputStream;
              import java.io.Reader;

              class Test {
                  Object fromFile(ObjectMapper mapper, File f) throws IOException {
                      return mapper.readValue(f, Object.class);
                  }
                  Object fromString(ObjectMapper mapper, String s) throws IOException {
                      return mapper.readValue(s, Object.class);
                  }
                  Object fromInputStream(ObjectMapper mapper, InputStream in) throws IOException {
                      return mapper.readValue(in, Object.class);
                  }
                  Object fromReader(ObjectMapper mapper, Reader r) throws IOException {
                      return mapper.readValue(r, Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveReadValueOnNonObjectMapperTypeAlone() {
        // Method named `readValue` on an unrelated type happens to take a URL — recipe should
        // not match because the receiver is not ObjectMapper (or a subtype).
        rewriteRun(
          //language=java
          java(
            """
              import java.net.URL;

              class Other {
                  Object readValue(URL src, Class<?> type) {
                      return null;
                  }
              }
              class Test {
                  Object load(Other o, URL url) {
                      return o.readValue(url, Object.class);
                  }
              }
              """
          )
        );
    }
}
