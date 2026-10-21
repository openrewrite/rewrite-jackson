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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Collections.singleton;

public class RemoveBuiltInModuleRegistrations extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher REGISTER_MODULE = new MethodMatcher(OBJECT_MAPPER_TYPE + " registerModule*(..)");
    private static final String OBJECT_MAPPER_BUILDER_TYPE = "com.fasterxml.jackson.databind.cfg.MapperBuilder";
    private static final MethodMatcher ADD_MODULE = new MethodMatcher(OBJECT_MAPPER_BUILDER_TYPE + " addModule*(..)");

    private static final String PARAMETER_NAMES_MODULE = "com.fasterxml.jackson.module.paramnames.ParameterNamesModule";
    private static final String JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";
    private static final String JSON_CREATOR_MODE = JSON_CREATOR + "$Mode";
    private static final String CONSTRUCTOR_DETECTOR = "com.fasterxml.jackson.databind.cfg.ConstructorDetector";
    private static final Map<String, String> CONSTRUCTOR_DETECTORS = new HashMap<>();

    static {
        CONSTRUCTOR_DETECTORS.put("PROPERTIES", "USE_PROPERTIES_BASED");
        CONSTRUCTOR_DETECTORS.put("DELEGATING", "USE_DELEGATING");
    }

    private static final Set<String> BUILT_IN_MODULES = new HashSet<>(Arrays.asList(
            PARAMETER_NAMES_MODULE,
            "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
            "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
    ));

    @Getter
    final String displayName = "Remove registrations of modules built-in to Jackson 3";

    @Getter
    final String description = "In Jackson 3, `ParameterNamesModule`, `Jdk8Module`, and `JavaTimeModule` are built into `jackson-databind` " +
            "and no longer need to be registered manually. This recipe removes `ObjectMapper.registerModule()` and `MapperBuilder.addModule()` calls " +
            "for these modules. A `ParameterNamesModule` created with `JsonCreator.Mode.PROPERTIES` or `DELEGATING` is replaced " +
            "with the matching `ConstructorDetector` instead, as that mode is not the Jackson 3 default.";

    @Getter
    final Set<String> tags = singleton( "jackson-3" );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(REGISTER_MODULE), new UsesMethod<>(ADD_MODULE)), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if ((REGISTER_MODULE.matches(method) || ADD_MODULE.matches(method)) &&
                                method.getArguments().stream().anyMatch(this::isBuiltInModuleInstantiation)) {
                            if (method.getArguments().size() == 1 && !isRemovable(method.getArguments().get(0))) {
                                return replaceWithConstructorDetector(method, ctx);
                            }
                            if (method.getArguments().stream().anyMatch(arg -> !isRemovable(arg))) {
                                return withoutRemovableModules(method, ctx);
                            }
                            for (String module : BUILT_IN_MODULES) {
                                maybeRemoveImport(module);
                            }
                            maybeRemoveImport(JSON_CREATOR);
                            maybeRemoveImport(JSON_CREATOR_MODE);
                            // If it's part of a chain, return the select; otherwise remove the statement
                            if (method.getSelect() instanceof J.MethodInvocation || method.getSelect() instanceof J.NewClass) {
                                J visited = visit(method.getSelect(), ctx);
                                return visited != null ? visited.withPrefix(method.getPrefix()) : null;
                            }
                            return null;
                        }

                        // Remove methods called on built-in module instances
                        if (method.getSelect() != null) {
                            for (String module : BUILT_IN_MODULES) {
                                if (TypeUtils.isAssignableTo(module, method.getSelect().getType())) {
                                    // Remove any imports associated with the method arguments
                                    for (JavaType.FullyQualified type : new JavaIsoVisitor<Set<JavaType.FullyQualified>>() {
                                        @Override
                                        public @Nullable JavaType visitType(@Nullable JavaType javaType, Set<JavaType.FullyQualified> types) {
                                            if (javaType instanceof JavaType.FullyQualified) {
                                                types.add((JavaType.FullyQualified) javaType);
                                            }
                                            return super.visitType(javaType, types);
                                        }
                                    }.reduce(method, new HashSet<>())) {
                                        maybeRemoveImport(TypeUtils.toString(type));
                                    }
                                    // Remove the entire method invocation
                                    return null;
                                }
                            }
                        }

                        return super.visitMethodInvocation(method, ctx);
                    }

                    @Override
                    public @Nullable J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                        JavaType type = mv.getType();
                        if (type != null && BUILT_IN_MODULES.contains(TypeUtils.toString(type))) {
                            return null;
                        }
                        return mv;
                    }

                    private boolean isRemovable(Expression arg) {
                        if (!isBuiltInModuleInstantiation(arg)) {
                            return false;
                        }
                        String mode = parameterNamesModuleMode(arg);
                        return mode == null || "DEFAULT".equals(mode);
                    }

                    private J withoutRemovableModules(J.MethodInvocation method, ExecutionContext ctx) {
                        for (String module : BUILT_IN_MODULES) {
                            maybeRemoveImport(module);
                        }
                        Space firstPrefix = method.getArguments().get(0).getPrefix();
                        List<Expression> kept = ListUtils.filter(method.getArguments(), arg -> !isRemovable(arg));
                        return super.visitMethodInvocation(method.withArguments(
                                ListUtils.mapFirst(kept, arg -> arg.withPrefix(firstPrefix))), ctx);
                    }

                    private J replaceWithConstructorDetector(J.MethodInvocation method, ExecutionContext ctx) {
                        String detector = method.getSelect() != null ?
                                CONSTRUCTOR_DETECTORS.get(parameterNamesModuleMode(method.getArguments().get(0))) : null;
                        if (detector == null) {
                            return super.visitMethodInvocation(method, ctx);
                        }
                        maybeRemoveImport(PARAMETER_NAMES_MODULE);
                        maybeRemoveImport(JSON_CREATOR);
                        maybeRemoveImport(JSON_CREATOR_MODE);
                        maybeAddImport(CONSTRUCTOR_DETECTOR);
                        boolean onBuilder = ADD_MODULE.matches(method);
                        String template = onBuilder ?
                                "#{any(" + OBJECT_MAPPER_BUILDER_TYPE + ")}.constructorDetector(ConstructorDetector." + detector + ")" :
                                "#{any(" + OBJECT_MAPPER_TYPE + ")}.setConstructorDetector(ConstructorDetector." + detector + ")";
                        J.MethodInvocation replaced = JavaTemplate.builder(template)
                                .imports(CONSTRUCTOR_DETECTOR)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), method.getSelect());
                        return replaced.getPadding().withSelect(replaced.getPadding().getSelect()
                                .withAfter(method.getPadding().getSelect().getAfter()));
                    }

                    private @Nullable String parameterNamesModuleMode(Expression arg) {
                        if (!(arg instanceof J.NewClass) || !TypeUtils.isOfClassType(arg.getType(), PARAMETER_NAMES_MODULE)) {
                            return null;
                        }
                        List<Expression> args = ((J.NewClass) arg).getArguments();
                        if (args.isEmpty() || args.get(0) instanceof J.Empty) {
                            return null;
                        }
                        Expression mode = args.get(0);
                        J.Identifier name = mode instanceof J.FieldAccess ? ((J.FieldAccess) mode).getName() :
                                mode instanceof J.Identifier ? (J.Identifier) mode : null;
                        if (name != null && name.getFieldType() != null &&
                                TypeUtils.isOfClassType(name.getFieldType().getOwner(), JSON_CREATOR_MODE)) {
                            return name.getSimpleName();
                        }
                        return "";
                    }

                    private boolean isBuiltInModuleInstantiation(Expression expr) {
                        if (expr instanceof J.NewClass) {
                            J.NewClass newClass = (J.NewClass) expr;
                            if (newClass.getClazz() != null) {
                                JavaType type = newClass.getClazz().getType();
                                return type != null && BUILT_IN_MODULES.contains(TypeUtils.toString(type));
                            }
                        }
                        JavaType type = expr.getType();
                        return type != null && BUILT_IN_MODULES.contains(TypeUtils.toString(type));
                    }
                }
        );
    }
}
