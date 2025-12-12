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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class SimplifyJacksonExceptionCatch extends Recipe {

    private static final String RUNTIME_EXCEPTION = "java.lang.RuntimeException";

    // Jackson 3 exception types that extend RuntimeException
    private static final Set<String> JACKSON_RUNTIME_EXCEPTIONS = new HashSet<>(Arrays.asList(
            "tools.jackson.core.JacksonException",
            "tools.jackson.core.StreamReadException",
            "tools.jackson.core.StreamWriteException",
            "tools.jackson.core.exc.UnexpectedEndOfInputException",
            "tools.jackson.databind.DatabindException"
    ));

    @Override
    public String getDisplayName() {
        return "Simplify catch clauses for Jackson exceptions";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `JacksonException` and its subtypes extend `RuntimeException`. " +
                "This recipe simplifies multi-catch clauses by removing Jackson exception types " +
                "when `RuntimeException` is also caught, since catching both is redundant. " +
                "For example, `catch (JacksonException | RuntimeException e)` becomes `catch (RuntimeException e)`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("tools.jackson.core.JacksonException", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.@Nullable MultiCatch visitMultiCatch(J.MultiCatch multiCatch, ExecutionContext ctx) {
                        J.MultiCatch mc = super.visitMultiCatch(multiCatch, ctx);

                        // Check if RuntimeException is in the multi-catch
                        boolean hasRuntimeException = mc.getAlternatives().stream()
                                .anyMatch(alt -> TypeUtils.isOfClassType(alt.getType(), RUNTIME_EXCEPTION));

                        if (!hasRuntimeException) {
                            return mc;
                        }

                        // Find Jackson exception types to remove
                        List<NameTree> toKeep = new ArrayList<>();
                        List<String> removedTypes = new ArrayList<>();

                        for (NameTree alt : mc.getAlternatives()) {
                            boolean isJacksonException = JACKSON_RUNTIME_EXCEPTIONS.stream()
                                    .anyMatch(jacksonType -> TypeUtils.isAssignableTo(jacksonType, alt.getType()));

                            if (isJacksonException) {
                                String typeName = TypeUtils.asFullyQualified(alt.getType()) != null
                                        ? TypeUtils.asFullyQualified(alt.getType()).getFullyQualifiedName()
                                        : null;
                                if (typeName != null) {
                                    removedTypes.add(typeName);
                                }
                            } else {
                                toKeep.add(alt);
                            }
                        }

                        // If nothing was removed, return unchanged
                        if (toKeep.size() == mc.getAlternatives().size()) {
                            return mc;
                        }

                        // Remove imports for the removed types
                        for (String removedType : removedTypes) {
                            maybeRemoveImport(removedType);
                        }

                        // Normalize prefixes - first element should have empty prefix
                        // (matching original first element) and subsequent elements keep their prefixes
                        List<NameTree> normalized = new ArrayList<>();
                        for (int i = 0; i < toKeep.size(); i++) {
                            NameTree nt = toKeep.get(i);
                            if (i == 0) {
                                normalized.add(nt.withPrefix(mc.getAlternatives().get(0).getPrefix()));
                            } else {
                                normalized.add(nt);
                            }
                        }

                        return mc.withAlternatives(normalized);
                    }
                }
        );
    }
}
