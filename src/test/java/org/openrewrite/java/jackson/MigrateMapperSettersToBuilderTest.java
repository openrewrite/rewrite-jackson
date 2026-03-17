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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateMapperSettersToBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMapperSettersToBuilder())
                .parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                        .classpath("jackson-core", "jackson-databind", "jackson-annotations"));
    }

    @Nested
    class BuilderMigration {

        @DocumentExample
        @Test
        void singleDisable() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    return JsonMapper.builder().disable(SerializationFeature.INDENT_OUTPUT).build();
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void multipleSetters() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.DeserializationFeature;
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.DeserializationFeature;
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    return JsonMapper.builder().disable(SerializationFeature.INDENT_OUTPUT).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void registerModuleRenamedToAddModule() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.Module;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create(Module module) {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.registerModule(module);
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.Module;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create(Module module) {
                                    return JsonMapper.builder().addModule(module).build();
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void setDateFormatRenamedToDefaultDateFormat() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            import java.text.SimpleDateFormat;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            import java.text.SimpleDateFormat;

                            class A {
                                JsonMapper create() {
                                    return JsonMapper.builder().defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd")).build();
                                }
                            }
                            """
                    )
            );
        }
    }

    @Nested
    class CommentFallback {

        @Test
        void mapperPassedToOtherMethod() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                void configure() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    doSomething(mapper);
                                }
                                void doSomething(JsonMapper mapper) {}
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                void configure() {
                                    JsonMapper mapper = JsonMapper.builder().disable(SerializationFeature.INDENT_OUTPUT).build();
                                    doSomething(mapper);
                                }
                                void doSomething(JsonMapper mapper) {}
                            }
                            """
                    )
            );
        }

        @Test
        void mapperFromParameter() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                void configure(JsonMapper mapper) {
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                void configure(JsonMapper mapper) {
                                    /* TODO disable was removed from JsonMapper in Jackson 3. Use mapper.rebuild().disable(...).build() or move to the mapper's instantiation site. */
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void knownSetterBeforeUnknownGoesToBuilder() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    mapper.setSerializationInclusion(null);
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = JsonMapper.builder().disable(SerializationFeature.INDENT_OUTPUT).build();
                                    mapper.setSerializationInclusion(null);
                                    return mapper;
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void knownSetterAfterUnknownGetsComment() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.setSerializationInclusion(null);
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    return mapper;
                                }
                            }
                            """,
                            """
                            import com.fasterxml.jackson.databind.SerializationFeature;
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    mapper.setSerializationInclusion(null);
                                    /* TODO disable was removed from JsonMapper in Jackson 3. Use mapper.rebuild().disable(...).build() or move to the mapper's instantiation site. */
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    return mapper;
                                }
                            }
                            """
                    )
            );
        }
    }

    @Nested
    class NoChange {

        @Test
        void noSetterCalls() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                JsonMapper create() {
                                    JsonMapper mapper = new JsonMapper();
                                    return mapper;
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void nonMapperMethodInvocation() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.json.JsonMapper;

                            class A {
                                String test(JsonMapper mapper) {
                                    String s = "hello";
                                    s.toUpperCase();
                                    return s;
                                }
                            }
                            """
                    )
            );
        }

        @Test
        void objectMapperNotAffected() {
            rewriteRun(
                    java(
                            """
                            import com.fasterxml.jackson.databind.ObjectMapper;
                            import com.fasterxml.jackson.databind.SerializationFeature;

                            class A {
                                ObjectMapper create() {
                                    ObjectMapper mapper = new ObjectMapper();
                                    mapper.disable(SerializationFeature.INDENT_OUTPUT);
                                    return mapper;
                                }
                            }
                            """
                    )
            );
        }
    }
}
