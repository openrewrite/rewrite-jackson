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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashMap;
import java.util.Map;

public class UseFormatAlignedObjectMappers extends Recipe {

    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String JSON_MAPPER = "com.fasterxml.jackson.databind.json.JsonMapper";

    private static final MethodMatcher OBJECT_MAPPER_FACTORY = new MethodMatcher(OBJECT_MAPPER + " <constructor>(com.fasterxml.jackson.core.JsonFactory)");
    private static final MethodMatcher OBJECT_MAPPER_NO_ARG = new MethodMatcher(OBJECT_MAPPER + " <constructor>()");

    @Getter
    final String displayName = "Use format alignment `ObjectMappers`";

    @Getter
    final String description = "Replace wrapping `ObjectMapper` calls with their format aligned implementation.";

    private static final Map<String, String> FACTORY_TO_MAPPER = new HashMap<String, String>() {{
        put("com.fasterxml.jackson.core.JsonFactory", JSON_MAPPER);
        put("com.fasterxml.jackson.dataformat.avro.AvroFactory", "com.fasterxml.jackson.dataformat.avro.AvroMapper");
        put("com.fasterxml.jackson.dataformat.cbor.CBORFactory", "com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper");
        put("com.fasterxml.jackson.dataformat.csv.CsvFactory", "com.fasterxml.jackson.dataformat.csv.CsvMapper");
        put("com.fasterxml.jackson.dataformat.ion.IonFactory", "com.fasterxml.jackson.dataformat.ion.IonObjectMapper");
        put("com.fasterxml.jackson.dataformat.smile.SmileFactory", "com.fasterxml.jackson.dataformat.smile.databind.SmileMapper");
        put("com.fasterxml.jackson.dataformat.xml.XmlFactory", "com.fasterxml.jackson.dataformat.xml.XmlMapper");
        put("com.fasterxml.jackson.dataformat.yaml.YAMLFactory", "com.fasterxml.jackson.dataformat.yaml.YAMLMapper");
    }};

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OBJECT_MAPPER, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);

                if (OBJECT_MAPPER_NO_ARG.matches(nc)) {
                    maybeRemoveImport(OBJECT_MAPPER);
                    maybeAddImport(JSON_MAPPER);
                    return replaceWithMapper(nc, JSON_MAPPER, null, ctx);
                }

                if (!OBJECT_MAPPER_FACTORY.matches(nc)) {
                    return nc;
                }

                Expression factory = nc.getArguments().get(0);
                String source = String.valueOf(factory.getType());
                String target = FACTORY_TO_MAPPER.get(source);
                if (target != null) {
                    maybeRemoveImport(OBJECT_MAPPER);
                    maybeRemoveImport(source);
                    maybeAddImport(target);
                    return replaceWithMapper(nc, target, isBareConstruction(factory) ? null : factory, ctx);
                }

                return nc; // unsupported factory type
            }

            private J.NewClass replaceWithMapper(J.NewClass nc, String target, @Nullable Expression factory,
                                                 ExecutionContext ctx) {
                JavaTemplate template = JavaTemplate.builder("new " + target + (factory == null ? "()" : "(#{any()})"))
                        .javaParser(MigrateMapperSettersToBuilder.templateParser(target, false, ctx))
                        .build();
                return factory == null ?
                        template.apply(getCursor(), nc.getCoordinates().replace()) :
                        template.apply(getCursor(), nc.getCoordinates().replace(), factory);
            }
        });
    }

    /**
     * A factory constructed without arguments carries no settings for the mapper to keep.
     */
    private static boolean isBareConstruction(Expression factory) {
        if (!(factory instanceof J.NewClass)) {
            return false;
        }
        J.NewClass construction = (J.NewClass) factory;
        return construction.getBody() == null &&
               construction.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }
}
