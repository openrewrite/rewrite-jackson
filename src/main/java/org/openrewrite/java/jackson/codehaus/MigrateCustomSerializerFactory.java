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
package org.openrewrite.java.jackson.codehaus;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singletonList;

/**
 * Jackson 2 removed {@code CustomSerializerFactory}. The way to add serializers in both Jackson 1.7+ and Jackson 2 is a
 * {@code SimpleModule}, so this rewrites to the Jackson 1 {@code SimpleModule}, which {@code CodehausClassesToFasterXML}
 * then moves to Jackson 2 along with the rest of the code. The code stays compilable after each step.
 */
public class MigrateCustomSerializerFactory extends Recipe {

    private static final String CUSTOM_SERIALIZER_FACTORY = "org.codehaus.jackson.map.ser.CustomSerializerFactory";
    private static final String SIMPLE_MODULE = "org.codehaus.jackson.map.module.SimpleModule";

    private static final MethodMatcher NEW_FACTORY = new MethodMatcher(CUSTOM_SERIALIZER_FACTORY + " <constructor>()");
    private static final MethodMatcher ADD_SPECIFIC_MAPPING = new MethodMatcher(CUSTOM_SERIALIZER_FACTORY + " addSpecificMapping(java.lang.Class, ..)");
    private static final MethodMatcher ADD_GENERIC_MAPPING = new MethodMatcher(CUSTOM_SERIALIZER_FACTORY + " addGenericMapping(java.lang.Class, ..)");
    private static final MethodMatcher SET_SERIALIZER_FACTORY = new MethodMatcher("org.codehaus.jackson.map.ObjectMapper setSerializerFactory(..)", true);

    @Getter
    final String displayName = "Migrate Jackson Codehaus (legacy) `CustomSerializerFactory` to `SimpleModule`";

    @Getter
    final String description = "Jackson 2 removed `CustomSerializerFactory`. Rewrites a `CustomSerializerFactory` that is " +
                               "created, given serializers with `addSpecificMapping` or `addGenericMapping`, and set on an " +
                               "`ObjectMapper` with `setSerializerFactory` to a `SimpleModule` that is given the same " +
                               "serializers with `addSerializer` and registered with `registerModule`, which works in " +
                               "Jackson 1.7 and later as well as in Jackson 2. Other uses of `CustomSerializerFactory` are " +
                               "left for `FindJackson1TypesWithoutJackson2Equivalent` to mark.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CUSTOM_SERIALIZER_FACTORY, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                for (int i = 0; i < b.getStatements().size(); i++) {
                    J.Block migrated = migrate(b, i, ctx);
                    if (migrated != null) {
                        b = migrated;
                        maybeAddImport(SIMPLE_MODULE);
                        maybeAddImport("org.codehaus.jackson.Version");
                        maybeRemoveImport(CUSTOM_SERIALIZER_FACTORY);
                    }
                }
                return b;
            }

            private J.@Nullable Block migrate(J.Block b, int index, ExecutionContext ctx) {
                List<Statement> statements = b.getStatements();
                if (!(statements.get(index) instanceof J.VariableDeclarations)) {
                    return null;
                }
                J.VariableDeclarations declaration = (J.VariableDeclarations) statements.get(index);
                if (declaration.getVariables().size() != 1 ||
                    !TypeUtils.isOfClassType(declaration.getType(), CUSTOM_SERIALIZER_FACTORY)) {
                    return null;
                }
                J.VariableDeclarations.NamedVariable variable = declaration.getVariables().get(0);
                if (!(variable.getInitializer() instanceof J.NewClass) ||
                    !NEW_FACTORY.matches(variable.getInitializer()) ||
                    ((J.NewClass) variable.getInitializer()).getBody() != null) {
                    return null;
                }

                // Every later use of the variable is to add a mapping or to set the factory on a mapper
                String name = variable.getSimpleName();
                int rewritable = 0;
                boolean registered = false;
                List<Statement> rest = statements.subList(index + 1, statements.size());
                for (Statement statement : rest) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation method = (J.MethodInvocation) statement;
                        if (isVariable(method.getSelect(), name) &&
                            (ADD_SPECIFIC_MAPPING.matches(method) || ADD_GENERIC_MAPPING.matches(method))) {
                            rewritable++;
                        } else if (isRegistration(method, name)) {
                            rewritable++;
                            registered = true;
                        }
                    }
                }
                AtomicInteger references = new AtomicInteger();
                JavaIsoVisitor<AtomicInteger> referenceCounter = new JavaIsoVisitor<AtomicInteger>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger count) {
                        if (isVariable(identifier, name)) {
                            count.incrementAndGet();
                        }
                        return identifier;
                    }
                };
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                for (Statement statement : rest) {
                    referenceCounter.visit(statement, references, blockCursor);
                }
                if (!registered || references.get() != rewritable) {
                    return null;
                }

                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                String moduleName = enclosingClass == null ? name : enclosingClass.getSimpleName();
                J.VariableDeclarations module = JavaTemplate.builder(
                                "SimpleModule " + name + " = new SimpleModule(\"" + moduleName + "\", Version.unknownVersion());")
                        .imports(SIMPLE_MODULE, "org.codehaus.jackson.Version")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-asl", "jackson-mapper-asl"))
                        .build()
                        .apply(new Cursor(blockCursor, declaration), declaration.getCoordinates().replace());
                module = module.withPrefix(declaration.getPrefix());
                JavaType.FullyQualified simpleModule = module.getTypeAsFullyQualified();
                JavaType.Variable moduleVariable = module.getVariables().get(0).getVariableType();
                if (simpleModule == null || moduleVariable == null) {
                    return null;
                }

                List<Statement> newStatements = new ArrayList<>(statements.subList(0, index));
                newStatements.add(module);
                for (Statement statement : rest) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation method = (J.MethodInvocation) statement;
                        if (isVariable(method.getSelect(), name) && method.getMethodType() != null) {
                            JavaType.Method addSerializer = method.getMethodType()
                                    .withName("addSerializer")
                                    .withDeclaringType(simpleModule)
                                    .withReturnType(simpleModule);
                            statement = method
                                    .withSelect(retype((J.Identifier) method.getSelect(), simpleModule, moduleVariable))
                                    .withName(method.getName().withSimpleName("addSerializer").withType(addSerializer))
                                    .withMethodType(addSerializer);
                        } else if (isRegistration(method, name)) {
                            JavaType.Method registerModule = registerModule(method.getMethodType());
                            if (registerModule == null) {
                                return null;
                            }
                            statement = method
                                    .withArguments(singletonList(
                                            retype((J.Identifier) method.getArguments().get(0), simpleModule, moduleVariable)))
                                    .withName(method.getName().withSimpleName("registerModule").withType(registerModule))
                                    .withMethodType(registerModule);
                        }
                    }
                    newStatements.add(statement);
                }
                return b.withStatements(newStatements);
            }
        });
    }

    private static boolean isRegistration(J.MethodInvocation method, String name) {
        return SET_SERIALIZER_FACTORY.matches(method) && method.getArguments().size() == 1 &&
               isVariable(method.getArguments().get(0), name);
    }

    private static boolean isVariable(@Nullable Expression expression, String name) {
        return expression instanceof J.Identifier &&
               name.equals(((J.Identifier) expression).getSimpleName()) &&
               ((J.Identifier) expression).getFieldType() != null &&
               TypeUtils.isOfClassType(expression.getType(), CUSTOM_SERIALIZER_FACTORY);
    }

    private static J.Identifier retype(J.Identifier identifier, JavaType.FullyQualified type, JavaType.Variable variable) {
        return identifier.withType(type).withFieldType(variable);
    }

    private static JavaType.@Nullable Method registerModule(JavaType.@Nullable Method setSerializerFactory) {
        if (setSerializerFactory == null) {
            return null;
        }
        for (JavaType.Method method : setSerializerFactory.getDeclaringType().getMethods()) {
            if ("registerModule".equals(method.getName()) && method.getParameterTypes().size() == 1) {
                return method;
            }
        }
        return null;
    }
}
