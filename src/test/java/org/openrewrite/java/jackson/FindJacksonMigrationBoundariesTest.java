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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.jackson.table.JacksonMigrationBoundaries;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.Assertions.java;

class FindJacksonMigrationBoundariesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJacksonMigrationBoundaries())
          .parser(JavaParser.fromJavaVersion().classpath("jackson-databind", "jackson-core", "jackson-annotations"));
    }

    @DocumentExample
    @Test
    void externalApiRequiresJackson2() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn("""
                package partner;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class Client {
                    public Client(ObjectMapper mapper) {}
                    public ObjectMapper mapper() { return null; }
                    public void configure(ObjectMapper mapper) {}
                }
                """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
                assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_API"))
                  .hasSize(3).allSatisfy(r -> {
                      assertThat(r.getJacksonTypes()).contains("com.fasterxml.jackson.databind.ObjectMapper");
                      assertThat(r.getApi()).contains("partner.Client");
                      assertThat(r.getSourcePath()).isEqualTo("app/src/main/java/Example.java");
                      assertThat(r.getSourceSet()).isEqualTo("main");
                      assertThat(r.getProject()).isEqualTo("app");
                  });
                assertThat(rows).filteredOn(r -> r.getCategory().equals("MAPPER_CONSTRUCTION")).hasSize(1);
            }),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import partner.Client;
              class Example {
                  void use() {
                      Client client = new Client(new ObjectMapper());
                      client.configure(client.mapper());
                  }
              }
              """,
            source -> source.path("app/src/main/java/Example.java")
              .markers(new JavaProject(randomId(), "app", null), new JavaSourceSet(randomId(), "main", List.of(), Map.of()))
          )
        );
    }

    @Test
    void sourceApisAreNotExternalEvenWhenVisitedLater() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
              assertThat(rows).noneMatch(r -> r.getCategory().equals("EXTERNAL_API"));
              assertThat(rows).filteredOn(r -> r.getCategory().equals("SOURCE_API")).hasSize(1);
          }),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Example {
                  void use(Factory factory) { factory.configure(new ObjectMapper()); }
              }
              """
          ),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Factory {
                  void configure(ObjectMapper mapper) {}
              }
              """
          )
        );
    }

    @Test
    void reportsSpringWiringAndSessionSerializerBoundary() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn("""
                package org.springframework.data.redis.serializer;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class GenericJackson2JsonRedisSerializer {
                    public GenericJackson2JsonRedisSerializer(ObjectMapper mapper) {}
                }
                """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows ->
                assertThat(rows).filteredOn(r -> r.getCategory().equals("SPRING_API"))
                  .singleElement().satisfies(r -> assertThat(r.getApi()).contains("GenericJackson2JsonRedisSerializer"))),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
              class Config {
                  Object serializer(ObjectMapper mapper) {
                      return new GenericJackson2JsonRedisSerializer(mapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void genericArrayAndMethodReferenceBoundaries() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn("""
                package partner;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import java.util.List;
                public class Client {
                    public static List<ObjectMapper> mappers() { return null; }
                    public static void configure(ObjectMapper[] mappers) {}
                }
                """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows ->
                assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_API"))
                  .hasSize(2).allSatisfy(r -> assertThat(r.getJacksonTypes()).contains("ObjectMapper"))),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import java.util.List;
              import java.util.function.Supplier;
              import partner.Client;
              class Example {
                  Supplier<List<ObjectMapper>> factory = Client::mappers;
                  void use() { Client.configure(new ObjectMapper[0]); }
              }
              """
          )
        );
    }

    @Test
    void objectParameterIsNotEvidenceOfJacksonRequirement() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn("""
                package partner;
                public class Client {
                    public static void configure(Object value) {}
                }
                """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
                assertThat(rows).noneMatch(r -> r.getCategory().equals("EXTERNAL_API"));
                assertThat(rows).filteredOn(r -> r.getCategory().equals("MAPPER_CONSTRUCTION")).hasSize(1);
            }),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import partner.Client;
              class Example {
                  void use() { Client.configure(new ObjectMapper()); }
              }
              """
          )
        );
    }

    @Test
    void sharedAnnotationsDoNotRequireJackson2() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows ->
              assertThat(rows).hasSize(3).allSatisfy(r -> assertThat(r.getCategory()).isEqualTo("SHARED_ANNOTATION"))),
          java(
            """
              import com.fasterxml.jackson.annotation.JsonCreator;
              import com.fasterxml.jackson.annotation.JsonInclude;
              import com.fasterxml.jackson.annotation.JsonProperty;
              @JsonInclude(JsonInclude.Include.NON_NULL)
              class Model {
                  @JsonCreator
                  Model(@JsonProperty("name") String name) {}
              }
              """
          )
        );
    }

    @Test
    void mapperSubtypesAndCustomSerializersAreVersionBound() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
              assertThat(rows).filteredOn(r -> r.getCategory().equals("DECLARATION"))
                .anySatisfy(r -> assertThat(r.getJacksonTypes()).contains("com.fasterxml.jackson.databind.ObjectMapper"))
                .anySatisfy(r -> assertThat(r.getJacksonTypes()).contains("com.fasterxml.jackson.databind.JsonSerializer"));
          }),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import com.fasterxml.jackson.databind.JsonSerializer;
              abstract class CustomSerializer extends JsonSerializer<String> {}
              class CustomMapper extends ObjectMapper {}
              class Config {
                  CustomMapper mapper = new CustomMapper();
              }
              """
          )
        );
    }

    @Test
    void separatesBuildProjects() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows ->
              assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_API"))
                .singleElement().satisfies(r -> assertThat(r.getProject()).isEqualTo("consumer"))),
          java(
            """
              package partner;
              import com.fasterxml.jackson.databind.ObjectMapper;
              public class Client {
                  public static void configure(ObjectMapper mapper) {}
              }
              """,
            source -> source.markers(new JavaProject(randomId(), "library", null))
          ),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import partner.Client;
              class Example {
                  void use(ObjectMapper mapper) { Client.configure(mapper); }
              }
              """,
            source -> source.markers(new JavaProject(randomId(), "consumer", null))
          )
        );
    }

    @Test
    void distinguishesJackson2AndJackson3InSameModule() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn(
                """
                  package tools.jackson.databind;
                  public class ObjectMapper {}
                  """,
                """
                  package partner;
                  public class Client {
                      public static void legacy(com.fasterxml.jackson.databind.ObjectMapper mapper) {}
                      public static void modern(tools.jackson.databind.ObjectMapper mapper) {}
                  }
                  """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
                assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_API"))
                  .hasSize(2)
                  .anySatisfy(r -> assertThat(r.getJacksonTypes()).isEqualTo("com.fasterxml.jackson.databind.ObjectMapper"))
                  .anySatisfy(r -> assertThat(r.getJacksonTypes()).isEqualTo("tools.jackson.databind.ObjectMapper"));
            }),
          java(
            """
              import partner.Client;
              class Example {
                  void use(com.fasterxml.jackson.databind.ObjectMapper legacy,
                           tools.jackson.databind.ObjectMapper modern) {
                      Client.legacy(legacy);
                      Client.modern(modern);
                  }
              }
              """
          )
        );
    }

    @Test
    void reportsLostMethodAttributionAsUnresolved() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows ->
              assertThat(rows).filteredOn(r -> r.getCategory().equals("UNRESOLVED_CALL"))
                .singleElement().satisfies(r -> assertThat(r.getApi()).isEmpty())),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Example {
                  void use(ObjectMapper mapper) { mapper.copy(); }
              }
              """,
            source -> source.mapBeforeRecipe(cu -> (J.CompilationUnit) new JavaIsoVisitor<Void>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Void unused) {
                    return super.visitMethodInvocation(method, unused).withMethodType(null);
                }
            }.visitNonNull(cu, null))
          )
        );
    }

    @Test
    void inferredGenericTypeIsNotAFixedExternalRequirement() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .classpath("jackson-databind", "jackson-core", "jackson-annotations")
              .dependsOn("""
                package partner;
                public class Client {
                    public static <T> T identity(T value) { return value; }
                }
                """))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows -> {
                assertThat(rows).noneMatch(r -> r.getCategory().equals("EXTERNAL_API"));
                assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_GENERIC_API")).hasSize(1);
            }),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              import partner.Client;
              class Example {
                  ObjectMapper mapper = Client.identity(new ObjectMapper());
              }
              """
          )
        );
    }

    @Test
    void impactCompositeLoadsAndIncludesBoundaryAnalysis() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.jackson.FindJacksonMigrationImpact")
            .dataTable(JacksonMigrationBoundaries.Row.class, rows ->
                assertThat(rows).filteredOn(r -> r.getCategory().equals("MAPPER_CONSTRUCTION")).hasSize(1)),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Example {
                  ObjectMapper mapper = new ObjectMapper();
              }
              """
          )
        );
    }

    @Test
    void boundedTypeVariablesRetainJacksonEvidence() {
        rewriteRun(
          spec -> spec.dataTable(JacksonMigrationBoundaries.Row.class, rows ->
              assertThat(rows).filteredOn(r -> r.getCategory().equals("DECLARATION"))
                .anySatisfy(r -> assertThat(r.getJacksonTypes()).contains("com.fasterxml.jackson.databind.ObjectMapper"))),
          java(
            """
              import com.fasterxml.jackson.databind.ObjectMapper;
              class Config<T extends ObjectMapper> {
                  T mapper;
              }
              """
          )
        );
    }

    @Test
    void actualAzureSerializerRequiresJackson2Mapper() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
              "azure-core-serializer-json-jackson", "jackson-databind-2", "jackson-core-2", "jackson-annotations"))
            .dataTable(JacksonMigrationBoundaries.Row.class, rows ->
                assertThat(rows).filteredOn(r -> r.getCategory().equals("EXTERNAL_API"))
                  .singleElement().satisfies(r -> {
                      assertThat(r.getApi()).contains("JacksonJsonSerializerBuilder", "serializer");
                      assertThat(r.getJacksonTypes()).isEqualTo("com.fasterxml.jackson.databind.ObjectMapper");
                  })),
          java(
            """
              import com.azure.core.serializer.json.jackson.JacksonJsonSerializerBuilder;
              import com.fasterxml.jackson.databind.ObjectMapper;
              class AzureConfig {
                  JacksonJsonSerializerBuilder serializer(ObjectMapper mapper) {
                      return new JacksonJsonSerializerBuilder().serializer(mapper);
                  }
              }
              """
          )
        );
    }
}
