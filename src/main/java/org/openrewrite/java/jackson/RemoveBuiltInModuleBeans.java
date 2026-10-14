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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveBuiltInModuleBeans extends ScanningRecipe<Set<String>> {
    private static final String BEAN = "org.springframework.context.annotation.Bean";
    private static final AnnotationMatcher BEAN_ANNOTATION = new AnnotationMatcher("@" + BEAN);
    private static final List<String> BUILT_IN_MODULES = Arrays.asList(
            "com.fasterxml.jackson.module.paramnames.ParameterNamesModule",
            "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
            "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
    );

    @Getter
    final String displayName = "Remove Spring beans for modules built-in to Jackson 3";

    @Getter
    final String description = "Jackson 3 includes Java time, JDK 8 and parameter name support in databind and removes their module classes. " +
            "Remove Spring `@Bean` methods that only return a new default instance of one of these modules. " +
            "Customized, overridden or directly referenced factories are left for manual migration.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> referencedMethods) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                for (JavaType.Method overridden = TypeUtils.findOverriddenMethod(method.getMethodType()).orElse(null);
                     overridden != null;
                     overridden = TypeUtils.findOverriddenMethod(overridden).orElse(null)) {
                    referencedMethods.add(methodKey(overridden));
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getMethodType() != null) {
                    referencedMethods.add(methodKey(method.getMethodType()));
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
                if (reference.getMethodType() != null) {
                    referencedMethods.add(methodKey(reference.getMethodType()));
                }
                return super.visitMemberReference(reference, ctx);
            }
        };
    }

    // Preserve every overload when the same declaring type and method name are referenced.
    private static String methodKey(JavaType.Method method) {
        return method.getDeclaringType().getFullyQualifiedName() + "#" + method.getName();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> referencedMethods) {
        return Preconditions.check(new UsesType<>(BEAN, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getMethodType() != null && !TypeUtils.isOverride(method.getMethodType()) &&
                        !referencedMethods.contains(methodKey(method.getMethodType())) &&
                        method.getLeadingAnnotations().stream().anyMatch(BEAN_ANNOTATION::matches) &&
                        method.getBody() != null && method.getBody().getStatements().size() == 1 &&
                        method.getBody().getStatements().get(0) instanceof J.Return) {
                    J.Return statement = (J.Return) method.getBody().getStatements().get(0);
                    if (statement.getExpression() instanceof J.NewClass) {
                        J.NewClass constructor = (J.NewClass) statement.getExpression();
                        if (constructor.getBody() == null &&
                                constructor.getArguments().stream().allMatch(J.Empty.class::isInstance) &&
                                BUILT_IN_MODULES.stream().anyMatch(type -> TypeUtils.isOfClassType(constructor.getType(), type))) {
                            BUILT_IN_MODULES.forEach(this::maybeRemoveImport);
                            maybeRemoveImport(BEAN);
                            return null;
                        }
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        });
    }
}
