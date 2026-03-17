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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class RelocateJacksonFeature extends Recipe {

    @Option(displayName = "Feature name",
            description = "The enum constant name to relocate (e.g., `READ_ENUMS_USING_TO_STRING`).",
            example = "READ_ENUMS_USING_TO_STRING")
    String featureName;

    @Option(displayName = "Old feature type",
            description = "The fully qualified name of the original feature enum (e.g., `com.fasterxml.jackson.databind.DeserializationFeature`).",
            example = "com.fasterxml.jackson.databind.DeserializationFeature")
    String oldTypeFqn;

    @Option(displayName = "New feature type",
            description = "The fully qualified name of the target feature enum (e.g., `com.fasterxml.jackson.databind.EnumFeature`).",
            example = "com.fasterxml.jackson.databind.EnumFeature")
    String newTypeFqn;

    String displayName = "Relocate Jackson feature flag to new enum";

    String description = "In Jackson 3, certain feature flags were moved to new enums " +
               "(`EnumFeature`, `DateTimeFeature`). This recipe relocates a single feature constant.";

    Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String newSimpleName = newTypeFqn.substring(newTypeFqn.lastIndexOf('.') + 1);
        return Preconditions.check(
                new UsesType<>(oldTypeFqn, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                        if (!featureName.equals(fa.getName().getSimpleName())) {
                            return fa;
                        }
                        String targetFqn = fqn(fa.getTarget().getType());
                        if (!oldTypeFqn.equals(targetFqn)) {
                            return fa;
                        }

                        JavaType.FullyQualified newType = JavaType.ShallowClass.build(newTypeFqn);
                        maybeAddImport(newTypeFqn);
                        maybeRemoveImport(oldTypeFqn);

                        Expression target = fa.getTarget();
                        Expression newTarget;
                        if (target instanceof J.Identifier) {
                            newTarget = ((J.Identifier) target)
                                    .withSimpleName(newSimpleName)
                                    .withType(newType);
                        } else {
                            newTarget = new J.Identifier(
                                    target.getId(),
                                    target.getPrefix(),
                                    target.getMarkers(),
                                    Collections.emptyList(),
                                    newSimpleName,
                                    newType,
                                    null
                            );
                        }

                        JavaType.Variable fieldType = fa.getName().getFieldType();
                        if (fieldType != null) {
                            fieldType = fieldType.withOwner(newType);
                        }

                        return fa
                                .withTarget(newTarget)
                                .withName(fa.getName().withFieldType(fieldType).withType(newType))
                                .withType(newType);
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        JavaType.Method methodType = mi.getMethodType();
                        if (methodType == null) {
                            return mi;
                        }
                        boolean hasRelocatedArg = false;
                        for (Expression arg : mi.getArguments()) {
                            if (newTypeFqn.equals(fqn(arg.getType()))) {
                                hasRelocatedArg = true;
                                break;
                            }
                        }
                        if (!hasRelocatedArg) {
                            return mi;
                        }
                        JavaType.FullyQualified newType = JavaType.ShallowClass.build(newTypeFqn);
                        List<JavaType> newParamTypes = new ArrayList<>(methodType.getParameterTypes().size());
                        for (JavaType pt : methodType.getParameterTypes()) {
                            if (oldTypeFqn.equals(fqn(pt))) {
                                newParamTypes.add(newType);
                            } else {
                                newParamTypes.add(pt);
                            }
                        }
                        JavaType.Method newMethodType = methodType.withParameterTypes(newParamTypes);
                        return mi.withMethodType(newMethodType)
                                .withName(mi.getName().withType(newMethodType));
                    }

                    private @Nullable String fqn(@Nullable JavaType type) {
                        return type instanceof JavaType.FullyQualified
                                ? ((JavaType.FullyQualified) type).getFullyQualifiedName()
                                : null;
                    }
                }
        );
    }
}
