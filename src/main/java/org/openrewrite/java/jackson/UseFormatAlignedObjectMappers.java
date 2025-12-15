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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(OBJECT_MAPPER_FACTORY), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);

                if (!OBJECT_MAPPER_FACTORY.matches(nc)) {
                    return nc;
                }

                JavaType type = nc.getArguments().get(0).getType();
                if (TypeUtils.isAssignableTo("com.fasterxml.jackson.dataformat.yaml.YAMLFactory", type)) {
                    maybeRemoveImport("com.fasterxml.jackson.dataformat.yaml.YAMLFactory");
                    maybeAddImport("com.fasterxml.jackson.dataformat.yaml.YAMLMapper");
                    return JavaTemplate.builder("new YAMLMapper()")
                            .imports("com.fasterxml.jackson.dataformat.yaml.YAMLMapper")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-2", "jackson-databind-2", "jackson-dataformat-yaml-2"))
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace());
                }
                if (TypeUtils.isAssignableTo("com.fasterxml.jackson.dataformat.xml.XmlFactory", type)) {
                    maybeRemoveImport("com.fasterxml.jackson.dataformat.xml.XmlFactory");
                    maybeAddImport("com.fasterxml.jackson.dataformat.xml.XmlMapper");
                    return JavaTemplate.builder("new XmlMapper()")
                            .imports("com.fasterxml.jackson.dataformat.xml.XmlMapper")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-2", "jackson-databind-2", "jackson-dataformat-xml-2"))
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace());
                }
                if (TypeUtils.isAssignableTo("com.fasterxml.jackson.core.JsonFactory", type)) {
                    // we default back to JSON as it's the Jackson default
                    maybeRemoveImport("com.fasterxml.jackson.core.JsonFactory");
                    maybeAddImport("com.fasterxml.jackson.databind.json.JsonMapper");
                    return JavaTemplate.builder("new JsonMapper()")
                            .imports("com.fasterxml.jackson.databind.json.JsonMapper")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-2", "jackson-databind-2"))
                            .build()
                            .apply(getCursor(), nc.getCoordinates().replace());
                }

                return nc; // unsupported factory type
            }
        });
    }
}
