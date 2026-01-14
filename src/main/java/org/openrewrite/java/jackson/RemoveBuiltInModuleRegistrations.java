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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveBuiltInModuleRegistrations extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher REGISTER_MODULE = new MethodMatcher(OBJECT_MAPPER_TYPE + " registerModule*(..)");
    private static final String OBJECT_MAPPER_BUILDER_TYPE = "com.fasterxml.jackson.databind.cfg.MapperBuilder";
    private static final MethodMatcher ADD_MODULE = new MethodMatcher(OBJECT_MAPPER_BUILDER_TYPE + " addModule*(..)");

    private static final Set<String> BUILT_IN_MODULES = new HashSet<>(Arrays.asList(
            "com.fasterxml.jackson.module.paramnames.ParameterNamesModule",
            "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
            "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
    ));

    @Getter
    final String displayName = "Remove registrations of modules built-in to Jackson 3";

    @Getter
    final String description = "In Jackson 3, `ParameterNamesModule`, `Jdk8Module`, and `JavaTimeModule` are built into `jackson-databind` " +
            "and no longer need to be registered manually. This recipe removes `ObjectMapper.registerModule()` and `MapperBuilder.addModule()` calls " +
            "for these modules.";

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(REGISTER_MODULE), new UsesMethod<>(ADD_MODULE)), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if ((REGISTER_MODULE.matches(method) || ADD_MODULE.matches(method)) &&
                                method.getArguments().stream().anyMatch(this::isBuiltInModuleInstantiation)) {
                            for (String module : BUILT_IN_MODULES) {
                                maybeRemoveImport(module);
                            }
                            // If it's part of a chain, return the select; otherwise remove the statement
                            if (method.getSelect() instanceof J.MethodInvocation || method.getSelect() instanceof J.NewClass) {
                                return method.getSelect().withPrefix(method.getPrefix());
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
