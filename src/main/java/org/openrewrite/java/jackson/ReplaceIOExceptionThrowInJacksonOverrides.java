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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodThrows;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class ReplaceIOExceptionThrowInJacksonOverrides extends Recipe {

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String JSON_MAPPING_EXCEPTION = "com.fasterxml.jackson.databind.JsonMappingException";
    private static final String JSON_GENERATOR = "com.fasterxml.jackson.core.JsonGenerator";
    private static final String JSON_PARSER = "com.fasterxml.jackson.core.JsonParser";

    // `serialize*` / `deserialize*` also cover the `serializeWithType` / `deserializeWithType` overrides.
    private static final String SERIALIZE_PATTERN = "com.fasterxml.jackson.databind.JsonSerializer serialize*(..)";
    private static final String DESERIALIZE_PATTERN = "com.fasterxml.jackson.databind.JsonDeserializer deserialize*(..)";
    private static final MethodMatcher SERIALIZE = new MethodMatcher(SERIALIZE_PATTERN, true);
    private static final MethodMatcher DESERIALIZE = new MethodMatcher(DESERIALIZE_PATTERN, true);

    final String displayName = "Replace `throw new IOException(..)` inside Jackson serializer / deserializer overrides";

    final String description = "In Jackson 3, `serialize()` and `deserialize()` methods no longer declare " +
            "`throws IOException`. This recipe removes the `throws IOException` declaration from overrides of " +
            "`JsonSerializer.serialize`, `JsonSerializer.serializeWithType`, `JsonDeserializer.deserialize`, and " +
            "`JsonDeserializer.deserializeWithType`, and rewrites `throw new IOException(msg[, cause])` inside those " +
            "overrides to `JsonMappingException.from(<generator|parser>, msg[, cause])`. A companion type change later " +
            "migrates `JsonMappingException` to `tools.jackson.databind.DatabindException`.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new RemoveMethodThrows(SERIALIZE_PATTERN, true, IO_EXCEPTION),
                new RemoveMethodThrows(DESERIALIZE_PATTERN, true, IO_EXCEPTION));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(IO_EXCEPTION, false),
                        Preconditions.or(
                                new DeclaresMethod<>(SERIALIZE),
                                new DeclaresMethod<>(DESERIALIZE))),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                        J.Throw t = super.visitThrow(thrown, ctx);
                        if (!(t.getException() instanceof J.NewClass)) {
                            return t;
                        }
                        J.NewClass newClass = (J.NewClass) t.getException();
                        if (!TypeUtils.isOfClassType(newClass.getType(), IO_EXCEPTION)) {
                            return t;
                        }
                        List<Expression> args = newClass.getArguments();
                        if (args.isEmpty() || args.size() > 2) {
                            return t;
                        }

                        J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        JavaType.Method mt = enclosing == null ? null : enclosing.getMethodType();
                        if (mt == null) {
                            return t;
                        }
                        boolean serializer = SERIALIZE.matches(mt);
                        if (!serializer && !DESERIALIZE.matches(mt)) {
                            return t;
                        }

                        String targetType = serializer ? JSON_GENERATOR : JSON_PARSER;
                        J.Identifier paramRef = findParamIdentifier(enclosing, targetType);
                        if (paramRef == null) {
                            return t;
                        }

                        maybeRemoveImport(IO_EXCEPTION);
                        maybeAddImport(JSON_MAPPING_EXCEPTION);
                        String template = args.size() == 1
                                ? "throw JsonMappingException.from(#{any(" + targetType + ")}, #{any(java.lang.String)})"
                                : "throw JsonMappingException.from(#{any(" + targetType + ")}, #{any(java.lang.String)}, #{any(java.lang.Throwable)})";
                        Object[] templateArgs = args.size() == 1
                                ? new Object[]{paramRef, args.get(0)}
                                : new Object[]{paramRef, args.get(0), args.get(1)};
                        return JavaTemplate.builder(template)
                                .imports(JSON_MAPPING_EXCEPTION)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-core-2", "jackson-databind-2"))
                                .build()
                                .apply(getCursor(), t.getCoordinates().replace(), templateArgs);
                    }

                    private J.@Nullable Identifier findParamIdentifier(J.MethodDeclaration md, String targetTypeFqn) {
                        for (Statement param : md.getParameters()) {
                            if (!(param instanceof J.VariableDeclarations)) {
                                continue;
                            }
                            J.VariableDeclarations vd = (J.VariableDeclarations) param;
                            if (TypeUtils.isOfClassType(vd.getType(), targetTypeFqn) &&
                                    !vd.getVariables().isEmpty()) {
                                return vd.getVariables().get(0).getName();
                            }
                        }
                        return null;
                    }
                }
        );
    }
}
