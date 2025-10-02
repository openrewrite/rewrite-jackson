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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RemoveBuiltInModuleRegistrations extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher REGISTER_MODULE = new MethodMatcher(OBJECT_MAPPER_TYPE + " registerModule*(..)");

    private static final Set<String> BUILT_IN_MODULES = new HashSet<>(Arrays.asList(
            "com.fasterxml.jackson.module.paramnames.ParameterNamesModule",
            "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
            "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
    ));

    @Override
    public String getDisplayName() {
        return "Remove registrations of modules built-in to Jackson 3";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `ParameterNamesModule`, `Jdk8Module`, and `JavaTimeModule` are built into `jackson-databind` " +
                "and no longer need to be registered manually. This recipe removes `ObjectMapper.registerModule()` calls " +
                "for these modules.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(REGISTER_MODULE), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (REGISTER_MODULE.matches(method) &&
                                method.getArguments().stream().anyMatch(this::isBuiltInModuleInstantiation)) {
                            for (String module : BUILT_IN_MODULES) {
                                maybeRemoveImport(module);
                            }
                            // If it's part of a chain, return the select; otherwise remove the statement
                            return method.getSelect() instanceof J.MethodInvocation ? (Statement) method.getSelect() : null;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }

                    private boolean isBuiltInModuleInstantiation(Expression expr) {
                        if (expr instanceof J.NewClass) {
                            J.NewClass newClass = (J.NewClass) expr;
                            if (newClass.getClazz() != null) {
                                return BUILT_IN_MODULES.contains(TypeUtils.toString(newClass.getClazz().getType()));
                            }
                        }
                        return false;
                    }
                }
        );
    }
}
