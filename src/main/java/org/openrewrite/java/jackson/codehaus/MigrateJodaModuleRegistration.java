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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singletonList;

/**
 * Jackson 2 has no counterpart of the Jackson 1 {@code JodaSerializers} and {@code JodaDeserializers}; its
 * {@code jackson-datatype-joda} module registers a serializer and a deserializer for every Joda type with
 * {@code JodaModule}. Where a {@code SimpleModule} is given Jackson 1 Joda serializers and registered on a mapper in the
 * same block, this registers {@code JodaModule} on that mapper instead, and drops the module when nothing else is left
 * in it. Runs before the types move to Jackson 2, while the serializers still have their Jackson 1 names.
 */
public class MigrateJodaModuleRegistration extends Recipe {

    private static final String JODA_SERIALIZERS = "org.codehaus.jackson.map.ext.JodaSerializers";
    private static final String JODA_DESERIALIZERS = "org.codehaus.jackson.map.ext.JodaDeserializers";
    private static final String SIMPLE_MODULE = "org.codehaus.jackson.map.module.SimpleModule";
    private static final String JODA_MODULE = "com.fasterxml.jackson.datatype.joda.JodaModule";

    private static final MethodMatcher ADD_SERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addSerializer(..)", true);
    private static final MethodMatcher ADD_DESERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addDeserializer(..)", true);
    private static final MethodMatcher NEW_SIMPLE_MODULE = new MethodMatcher(SIMPLE_MODULE + " <constructor>(..)");
    private static final MethodMatcher REGISTER_MODULE = new MethodMatcher("org.codehaus.jackson.map.ObjectMapper registerModule(..)", true);

    @Getter
    final String displayName = "Register `JodaModule` in place of Jackson Codehaus (legacy) Joda serializers";

    @Getter
    final String description = "Jackson 2 moved Joda support out of databind into `jackson-datatype-joda`, whose " +
                               "`JodaModule` registers a serializer and deserializer for every Joda type. Where the " +
                               "Jackson 1 `JodaSerializers` and `JodaDeserializers` are added to a `SimpleModule` that " +
                               "is registered on an `ObjectMapper` in the same block, registers `JodaModule` on that " +
                               "mapper instead, and removes the module when the Joda serializers were all it held.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(JODA_SERIALIZERS + "$*", false),
                        new UsesType<>(JODA_DESERIALIZERS + "$*", false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        Set<String> modules = new LinkedHashSet<>();
                        for (Statement statement : b.getStatements()) {
                            String module = jodaAddedTo(statement);
                            if (module != null) {
                                modules.add(module);
                            }
                        }
                        Set<String> mappersWithJodaModule = new HashSet<>();
                        for (String module : modules) {
                            J.Block migrated = migrate(b, module, mappersWithJodaModule, ctx);
                            if (migrated != null) {
                                b = migrated;
                            }
                        }
                        return b;
                    }

                    private J.@Nullable Block migrate(J.Block b, String module, Set<String> mappersWithJodaModule, ExecutionContext ctx) {
                        List<Statement> statements = b.getStatements();
                        J.MethodInvocation registration = null;
                        int jodaAdds = 0;
                        int registrations = 0;
                        J.VariableDeclarations declaration = null;
                        for (Statement statement : statements) {
                            if (module.equals(jodaAddedTo(statement))) {
                                jodaAdds++;
                            } else if (isRegistration(statement, module)) {
                                registrations++;
                                if (registration == null) {
                                    registration = (J.MethodInvocation) statement;
                                }
                            } else if (declaration == null && declares(statement, module)) {
                                declaration = (J.VariableDeclarations) statement;
                            }
                        }
                        if (registration == null) {
                            return null;
                        }

                        // The module can go when the Joda serializers and its registration are all that use it
                        Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                        boolean dropModule = false;
                        if (declaration != null && registrations == 1) {
                            AtomicInteger references = new AtomicInteger();
                            JavaIsoVisitor<AtomicInteger> referenceCounter = new JavaIsoVisitor<AtomicInteger>() {
                                @Override
                                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger count) {
                                    if (isModule(identifier, module)) {
                                        count.incrementAndGet();
                                    }
                                    return identifier;
                                }
                            };
                            for (Statement statement : statements) {
                                if (statement != declaration) {
                                    referenceCounter.visit(statement, references, blockCursor);
                                }
                            }
                            dropModule = references.get() == jodaAdds + registrations;
                        }

                        Expression argument = registration.getArguments().get(0);
                        J.NewClass jodaModule = JavaTemplate.builder("new JodaModule()")
                                .imports(JODA_MODULE)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "jackson-datatype-joda", "jackson-databind-2", "jackson-core-2", "jackson-annotations-2"))
                                .build()
                                .apply(new Cursor(new Cursor(blockCursor, registration), argument), argument.getCoordinates().replace());
                        jodaModule = jodaModule.withPrefix(argument.getPrefix());
                        J.MethodInvocation registerJodaModule = registration.withArguments(singletonList(jodaModule));
                        String mapper = registration.getSelect() == null ? "this" :
                                registration.getSelect().printTrimmed(new Cursor(blockCursor, registration));

                        List<Statement> newStatements = new ArrayList<>(statements.size());
                        for (Statement statement : statements) {
                            if (statement == declaration && dropModule || module.equals(jodaAddedTo(statement))) {
                                removeImportsOf(statement);
                                continue;
                            }
                            if (statement == registration) {
                                if (dropModule) {
                                    if (mappersWithJodaModule.add(mapper)) {
                                        newStatements.add(registerJodaModule);
                                    }
                                    continue;
                                }
                                newStatements.add(statement);
                                if (mappersWithJodaModule.add(mapper)) {
                                    newStatements.add(registerJodaModule.withId(Tree.randomId()));
                                }
                                continue;
                            }
                            newStatements.add(statement);
                        }
                        maybeAddImport(JODA_MODULE);
                        return b.withStatements(newStatements);
                    }

                    private void removeImportsOf(Statement statement) {
                        Set<JavaType.FullyQualified> types = new HashSet<>();
                        new JavaIsoVisitor<Set<JavaType.FullyQualified>>() {
                            @Override
                            public @Nullable J visit(@Nullable Tree tree, Set<JavaType.FullyQualified> found) {
                                if (tree instanceof TypedTree) {
                                    for (JavaType.FullyQualified type = TypeUtils.asFullyQualified(((TypedTree) tree).getType());
                                         type != null; type = type.getOwningClass()) {
                                        found.add(type);
                                    }
                                }
                                return super.visit(tree, found);
                            }
                        }.visit(statement, types);
                        for (JavaType.FullyQualified type : types) {
                            maybeRemoveImport(type);
                        }
                    }
                });
    }

    /**
     * The name of the module the statement adds a Jackson 1 Joda serializer or deserializer to, if that is what it does.
     */
    private static @Nullable String jodaAddedTo(Statement statement) {
        if (!(statement instanceof J.MethodInvocation)) {
            return null;
        }
        J.MethodInvocation method = (J.MethodInvocation) statement;
        if (!(ADD_SERIALIZER.matches(method) || ADD_DESERIALIZER.matches(method)) ||
            !(method.getSelect() instanceof J.Identifier) ||
            !TypeUtils.isOfClassType(method.getSelect().getType(), SIMPLE_MODULE) ||
            ((J.Identifier) method.getSelect()).getFieldType() == null) {
            return null;
        }
        List<Expression> arguments = method.getArguments();
        if (!isJodaSerializer(arguments.get(arguments.size() - 1))) {
            return null;
        }
        return ((J.Identifier) method.getSelect()).getSimpleName();
    }

    private static boolean isJodaSerializer(Expression expression) {
        if (!(expression instanceof J.NewClass)) {
            return false;
        }
        J.NewClass newClass = (J.NewClass) expression;
        if (newClass.getBody() != null ||
            newClass.getArguments().stream().anyMatch(argument -> !(argument instanceof J.Empty))) {
            return false;
        }
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getType());
        JavaType.FullyQualified owner = type == null ? null : type.getOwningClass();
        return owner != null &&
               (JODA_SERIALIZERS.equals(owner.getFullyQualifiedName()) ||
                JODA_DESERIALIZERS.equals(owner.getFullyQualifiedName()));
    }

    private static boolean isRegistration(Statement statement, String module) {
        if (!(statement instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation method = (J.MethodInvocation) statement;
        return REGISTER_MODULE.matches(method) && method.getArguments().size() == 1 &&
               isModule(method.getArguments().get(0), module);
    }

    private static boolean declares(Statement statement, String module) {
        if (!(statement instanceof J.VariableDeclarations)) {
            return false;
        }
        J.VariableDeclarations declaration = (J.VariableDeclarations) statement;
        if (declaration.getVariables().size() != 1) {
            return false;
        }
        J.VariableDeclarations.NamedVariable variable = declaration.getVariables().get(0);
        return module.equals(variable.getSimpleName()) &&
               variable.getInitializer() instanceof J.NewClass &&
               NEW_SIMPLE_MODULE.matches(variable.getInitializer()) &&
               ((J.NewClass) variable.getInitializer()).getBody() == null;
    }

    private static boolean isModule(@Nullable Expression expression, String module) {
        return expression instanceof J.Identifier &&
               module.equals(((J.Identifier) expression).getSimpleName()) &&
               ((J.Identifier) expression).getFieldType() != null &&
               TypeUtils.isOfClassType(expression.getType(), SIMPLE_MODULE);
    }
}
