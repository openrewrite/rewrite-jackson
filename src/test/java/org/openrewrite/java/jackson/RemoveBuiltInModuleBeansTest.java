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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.openrewrite.java.Assertions.java;

class RemoveBuiltInModuleBeansTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveBuiltInModuleBeans())
          .parser(JavaParser.fromJavaVersion().dependsOn(
            "package org.springframework.context.annotation; public @interface Bean {}",
            "package com.fasterxml.jackson.databind; public abstract class Module {}",
            "package com.fasterxml.jackson.databind.module; public class SimpleModule extends com.fasterxml.jackson.databind.Module {}",
            "package com.fasterxml.jackson.datatype.jsr310; public class JavaTimeModule extends com.fasterxml.jackson.databind.Module { public void configure() {} }",
            "package com.fasterxml.jackson.datatype.jdk8; public class Jdk8Module {}",
            "package com.fasterxml.jackson.module.paramnames; public class ParameterNamesModule { public ParameterNamesModule() {} public ParameterNamesModule(String mode) {} }"));
    }

    @DocumentExample
    @Test
    void removeBuiltInModuleBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
              import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

              class Config {
                  @Bean
                  JavaTimeModule javaTimeModule() {
                      return new JavaTimeModule();
                  }

                  @Bean
                  Jdk8Module jdk8Module() {
                      return new Jdk8Module();
                  }
              }
              """,
            """
              class Config {
              }
              """
          )
        );
    }


    @Test
    void preserveCustomizedBeansAndOrdinaryMethods() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
              import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

              class Config {
                  @Bean
                  JavaTimeModule customized() {
                      JavaTimeModule module = new JavaTimeModule();
                      module.configure();
                      return module;
                  }

                  @Bean
                  ParameterNamesModule withArguments() {
                      return new ParameterNamesModule("properties");
                  }

                  @Bean
                  JavaTimeModule anonymous() {
                      return new JavaTimeModule() {};
                  }

                  JavaTimeModule ordinary() {
                      return new JavaTimeModule();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeParameterNamesModuleBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

              class Config {
                  @Bean
                  ParameterNamesModule parameterNamesModule() {
                      return new ParameterNamesModule();
                  }
              }
              """,
            """
              class Config {
              }
              """
          )
        );
    }
    @Test
    void preserveInterfaceImplementations() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              interface Modules {
                  JavaTimeModule annotated();
                  JavaTimeModule unannotated();
              }

              class Config implements Modules {
                  @Bean
                  @Override
                  public JavaTimeModule annotated() {
                      return new JavaTimeModule();
                  }

                  @Bean
                  public JavaTimeModule unannotated() {
                      return new JavaTimeModule();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveFactoriesCalledFromAnotherFile() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class Config {
                  @Bean
                  JavaTimeModule direct() {
                      return new JavaTimeModule();
                  }

                  @Bean
                  JavaTimeModule reference() {
                      return new JavaTimeModule();
                  }
              }
              """
          ),
          java(
            """
              import java.util.function.Supplier;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class Caller {
                  JavaTimeModule direct = new Config().direct();
                  Supplier<JavaTimeModule> reference = new Config()::reference;
              }
              """
          )
        );
    }

    @Test
    void preserveBeanOverriddenByAnotherClass() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import com.fasterxml.jackson.databind.Module;
              import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

              class Base {
                  @Bean
                  public Module module() {
                      return new JavaTimeModule();
                  }
              }
              """
          ),
          java(
            """
              import com.fasterxml.jackson.databind.Module;
              import com.fasterxml.jackson.databind.module.SimpleModule;

              class Child extends Base {
                  @Override
                  public Module module() {
                      return new SimpleModule();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotHangOnACyclicTypeHierarchy() {
        // Java source cannot declare a cyclic hierarchy, but a type model assembled from incompletely
        // attributed sources, or from a language whose tree is built on the Java one, can hold one. There
        // `findOverriddenMethod` reports a method as its own ancestor, and walking the chain never ends.
        JavaType.Class parent = new JavaType.Class(null, 0L, "acme.Parent",
          JavaType.FullyQualified.Kind.Class, null, null, null, null, null, null, null);
        JavaType.Class child = new JavaType.Class(null, 0L, "acme.Child",
          JavaType.FullyQualified.Kind.Class, null, parent, null, null, null, null, null);
        JavaType.Method cyclic = new JavaType.Method(null, 1L, parent, "m",
          JavaType.Primitive.Void, (List<String>) null, (List<JavaType>) null, null, null, null, null);
        parent.unsafeSet((List<JavaType>) null, child, null, null, null, null, singletonList(cyclic));

        J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse("class A { void m() {} }")
          .findFirst()
          .orElseThrow(IllegalStateException::new);
        J.CompilationUnit withCyclicType = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                return method.withMethodType(cyclic);
            }
        }.visitNonNull(cu, new InMemoryExecutionContext());

        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
          new RemoveBuiltInModuleBeans().getScanner(new HashSet<>())
            .visit(withCyclicType, new InMemoryExecutionContext()));
    }
}
