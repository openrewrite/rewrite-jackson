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

class ReplaceLaissezFaireSubTypeValidatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceLaissezFaireSubTypeValidator())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"));
    }

    @DocumentExample
    @Test
    void instanceField() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

              class Test {
                  void configure(ObjectMapper mapper) {
                      mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
                  }
              }
              """,
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

              class Test {
                  void configure(ObjectMapper mapper) {
                      mapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(), ObjectMapper.DefaultTyping.NON_FINAL);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImportAndConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
              import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

              import static com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance;

              class Test {
                  PolymorphicTypeValidator a = instance;
                  PolymorphicTypeValidator b = new LaissezFaireSubTypeValidator();
              }
              """,
            """
              import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
              import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

              class Test {
                  PolymorphicTypeValidator a = BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();
                  PolymorphicTypeValidator b = BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();
              }
              """
          )
        );
    }
}
