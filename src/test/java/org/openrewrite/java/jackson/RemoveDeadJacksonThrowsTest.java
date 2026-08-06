/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class RemoveDeadJacksonThrowsTest implements RewriteTest {

    private static final String JACKSON_EXCEPTION_J3_STUB =
            "package tools.jackson.core.exc;\n" +
            "public class JacksonException extends RuntimeException {}\n";

    private static final String JSON_PROCESSING_J3_STUB =
            "package tools.jackson.core.exc;\n" +
            "public class JsonProcessingException extends JacksonException {}\n";

    private static final String JSON_MAPPING_J3_STUB =
            "package tools.jackson.databind.exc;\n" +
            "public class JsonMappingException extends tools.jackson.core.exc.JsonProcessingException {}\n";

    private static final String DATABIND_EXCEPTION_J3_STUB =
            "package tools.jackson.databind;\n" +
            "public class DatabindException extends tools.jackson.core.exc.JacksonException {}\n";

    private static final String JSON_PROCESSING_J2_STUB =
            "package com.fasterxml.jackson.core;\n" +
            "public class JsonProcessingException extends Exception {}\n";

    private static final String JSON_MAPPING_J2_STUB =
            "package com.fasterxml.jackson.databind;\n" +
            "public class JsonMappingException extends com.fasterxml.jackson.core.JsonProcessingException {}\n";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDeadJacksonThrows())
                .parser(JavaParser.fromJavaVersion()
                        .dependsOn(
                                JACKSON_EXCEPTION_J3_STUB,
                                JSON_PROCESSING_J3_STUB,
                                JSON_MAPPING_J3_STUB,
                                DATABIND_EXCEPTION_J3_STUB,
                                JSON_PROCESSING_J2_STUB,
                                JSON_MAPPING_J2_STUB))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void removesToolsJacksonJsonProcessingException() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JsonProcessingException;
                
                class JsonUtil {
                    String toJson(Object obj) throws JsonProcessingException {
                        return obj.toString();
                    }
                }
                """,
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void removesToolsJacksonJacksonException() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JacksonException;
                
                class JsonUtil {
                    String toJson(Object obj) throws JacksonException {
                        return obj.toString();
                    }
                }
                """,
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void removesToolsJacksonJsonMappingException() {
        rewriteRun(java(
                """
                import tools.jackson.databind.exc.JsonMappingException;
                
                class JsonUtil {
                    String toJson(Object obj) throws JsonMappingException {
                        return obj.toString();
                    }
                }
                """,
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void removesToolsJacksonDatabindException() {
        rewriteRun(java(
                """
                import tools.jackson.databind.DatabindException;
                
                class JsonUtil {
                    String toJson(Object obj) throws DatabindException {
                        return obj.toString();
                    }
                }
                """,
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void removesFasterxmlJsonProcessingException() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.core.JsonProcessingException;
                
                class JsonUtil {
                    String toJson(Object obj) throws JsonProcessingException {
                        return obj.toString();
                    }
                }
                """,
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void removesFasterxmlJsonMappingException() {
        rewriteRun(java(
                """
                import com.fasterxml.jackson.databind.JsonMappingException;
                
                class JsonUtil {
                    void parse(String json) throws JsonMappingException {
                    }
                }
                """,
                """
                class JsonUtil {
                    void parse(String json) {
                    }
                }
                """
        ));
    }

    @Test
    void removesJacksonExceptionLeavingIoException() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JsonProcessingException;
                import java.io.IOException;
                
                class JsonUtil {
                    void writeToFile(Object obj) throws IOException, JsonProcessingException {
                    }
                }
                """,
                """
                import java.io.IOException;
                
                class JsonUtil {
                    void writeToFile(Object obj) throws IOException {
                    }
                }
                """
        ));
    }

    @Test
    void removesMultipleJacksonExceptions() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JacksonException;
                import tools.jackson.core.exc.JsonProcessingException;
                
                class JsonUtil {
                    void process() throws JacksonException, JsonProcessingException {
                    }
                }
                """,
                """
                class JsonUtil {
                    void process() {
                    }
                }
                """
        ));
    }

    @Test
    void noOpWhenNoThrowsClause() {
        rewriteRun(java(
                """
                class JsonUtil {
                    String toJson(Object obj) {
                        return obj.toString();
                    }
                }
                """
        ));
    }

    @Test
    void noOpWhenOnlyIoException() {
        rewriteRun(java(
                """
                import java.io.IOException;
                
                class JsonUtil {
                    void writeToFile() throws IOException {
                    }
                }
                """
        ));
    }

    @Test
    void noOpWhenOnlyRuntimeException() {
        rewriteRun(java(
                """
                class JsonUtil {
                    void compute() throws RuntimeException {
                    }
                }
                """
        ));
    }

    @Test
    void removesJacksonExceptionFromConstructorThrows() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JsonProcessingException;
                
                class JsonFactory {
                    JsonFactory() throws JsonProcessingException {
                    }
                }
                """,
                """
                class JsonFactory {
                    JsonFactory() {
                    }
                }
                """
        ));
    }

    @Test
    void removesJacksonExceptionFromInterfaceMethodThrows() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JsonProcessingException;
                
                interface JsonProcessor {
                    String process(byte[] data) throws JsonProcessingException;
                }
                """,
                """
                interface JsonProcessor {
                    String process(byte[] data);
                }
                """
        ));
    }

    @Test
    void removesJacksonThrowsFromMultipleMethodsInSameClass() {
        rewriteRun(java(
                """
                import tools.jackson.core.exc.JsonProcessingException;
                import tools.jackson.core.exc.JacksonException;
                import java.io.IOException;
                
                class JsonService {
                    void serializeAll() throws JsonProcessingException, JacksonException {
                    }
                    void readFile() throws IOException, JsonProcessingException {
                    }
                    void compute() throws RuntimeException {
                    }
                    void plain() {
                    }
                }
                """,
                """
                import java.io.IOException;
                
                class JsonService {
                    void serializeAll() {
                    }
                    void readFile() throws IOException {
                    }
                    void compute() throws RuntimeException {
                    }
                    void plain() {
                    }
                }
                """
        ));
    }
}
