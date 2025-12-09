package org.openrewrite.java.jackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UpdateSerializationInclusionConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpdateSerializationInclusionConfiguration())
          .parser(JavaParser.fromJavaVersion().classpath(
            "jackson-core",
            "jackson-databind")
          );
    }

    @DocumentExample
    @Test
    void updateSerializationInclusionOnBuilder() {
        rewriteRun(
          // language=java
          java(
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.databind.json.JsonMapper;

              class Test {
                  void configure() {
                      JsonMapper.builder()
                        .serializationInclusion(JsonInclude.Include.NON_NULL)
                        .build();
                  }
              }
              """,
            """
              import com.fasterxml.jackson.annotation.JsonInclude;
              import tools.jackson.databind.json.JsonMapper;

              class Test {
                  void configure() {
                      JsonMapper.builder()
                        .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                        .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                        .build();
                  }
              }
              """
          )
        );
    }

}
