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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class UseFormatAlignedObjectMappers extends Recipe {

    private static final MethodMatcher OBJECT_MAPPER_FACTORY = new MethodMatcher("com.fasterxml.jackson.databind.ObjectMapper <constructor>(com.fasterxml.jackson.core.JsonFactory)");

    @Override
    public String getDisplayName() {
        return "Use format alignment `ObjectMappers`";
    }

    @Override
    public String getDescription() {
        return "Replace wrapping `ObjectMapper` calls with their format aligned implementation.";
    }

    private static final Map<String, String> FACTORY_TO_MAPPER = new HashMap<String, String>() {{
        put("com.fasterxml.jackson.core.JsonFactory", "com.fasterxml.jackson.databind.json.JsonMapper");
        put("com.fasterxml.jackson.dataformat.avro.AvroFactory", "com.fasterxml.jackson.dataformat.avro.AvroMapper");
        put("com.fasterxml.jackson.dataformat.cbor.CBORFactory", "com.fasterxml.jackson.dataformat.cbor.CBORMapper");
        put("com.fasterxml.jackson.dataformat.csv.CsvFactory", "com.fasterxml.jackson.dataformat.csv.CsvMapper");
        put("com.fasterxml.jackson.dataformat.ion.IonFactory", "com.fasterxml.jackson.dataformat.ion.IonMapper");
        put("com.fasterxml.jackson.dataformat.smile.SmileFactory", "com.fasterxml.jackson.dataformat.smile.SmileMapper");
        put("com.fasterxml.jackson.dataformat.xml.XmlFactory", "com.fasterxml.jackson.dataformat.xml.XmlMapper");
        put("com.fasterxml.jackson.dataformat.yaml.YAMLFactory", "com.fasterxml.jackson.dataformat.yaml.YAMLMapper");
    }};

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(OBJECT_MAPPER_FACTORY), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);

                if (!OBJECT_MAPPER_FACTORY.matches(nc)) {
                    return nc;
                }

                String source = String.valueOf(nc.getArguments().get(0).getType());
                String target = FACTORY_TO_MAPPER.get(source);
                if (target != null) {
                    maybeRemoveImport(source);
                    maybeAddImport(target);
                    int lastDotIndex = target.lastIndexOf('.');
                    String packageName = target.substring(0, lastDotIndex);
                    String simpleName = target.substring(lastDotIndex + 1);
                    return JavaTemplate.builder("new " + target + "()")
                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                    "package " + packageName + ";\n" +
                                            "public class " + simpleName + " extends com.fasterxml.jackson.databind.ObjectMapper {\n" +
                                            "    public " + simpleName + "() {}\n" +
                                            "}\n"
                            ))
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace());
                }

                return nc; // unsupported factory type
            }
        });
    }
}
