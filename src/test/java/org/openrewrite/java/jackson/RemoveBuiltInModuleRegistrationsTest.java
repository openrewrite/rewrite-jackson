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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveBuiltInModuleRegistrationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveBuiltInModuleRegistrations())
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-core",
            "jackson-databind",
            "jackson-datatype-jsr310",
            "jackson-datatype-jdk8",
            "jackson-module-parameter-names"));
    }

    @DocumentExample
    @Test
    void removeJavaTimeModuleRegistration() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new JavaTimeModule());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeJdk8ModuleRegistration() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new Jdk8Module());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeParameterNamesModuleRegistration() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new ParameterNamesModule());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultipleBuiltInModules() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
              import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new ParameterNamesModule());
                      mapper.registerModule(new Jdk8Module());
                      mapper.registerModule(new JavaTimeModule());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCustomModuleRegistration() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.Module;

              class CustomModule extends Module {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }

                  @Override
                  public com.fasterxml.jackson.core.Version version() {
                      return com.fasterxml.jackson.core.Version.unknownVersion();
                  }

                  @Override
                  public void setupModule(SetupContext context) {
                  }
              }

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new CustomModule());
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMixedModulesKeepCustom() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.Module;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class CustomModule extends Module {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }

                  @Override
                  public com.fasterxml.jackson.core.Version version() {
                      return com.fasterxml.jackson.core.Version.unknownVersion();
                  }

                  @Override
                  public void setupModule(SetupContext context) {
                  }
              }

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new JavaTimeModule());
                      mapper.registerModule(new CustomModule());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.Module;

              class CustomModule extends Module {
                  @Override
                  public String getModuleName() {
                      return "custom";
                  }

                  @Override
                  public com.fasterxml.jackson.core.Version version() {
                      return com.fasterxml.jackson.core.Version.unknownVersion();
                  }

                  @Override
                  public void setupModule(SetupContext context) {
                  }
              }

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.registerModule(new CustomModule());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-jackson/issues/38")
    @Test
    void assignmentWithChainedRegistration() {
        rewriteRun(
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;

              class Test {
                  void configure() {
                      ObjectMapper mapper = new ObjectMapper();
                  }
              }
              """
          )
        );
    }
}
