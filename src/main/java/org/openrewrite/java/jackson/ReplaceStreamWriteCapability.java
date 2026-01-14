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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceStreamWriteCapability extends Recipe {

    private static final MethodMatcher CAN_WRITE_BINARY_NATIVELY = new MethodMatcher("com.fasterxml.jackson.core.JsonGenerator canWriteBinaryNatively()");
    private static final MethodMatcher CAN_WRITE_FORMATTED_NUMBERS = new MethodMatcher("com.fasterxml.jackson.core.JsonGenerator canWriteFormattedNumbers()");

    @Getter
    final String displayName = "Replace removed `JsonGenerator` capability methods with `StreamWriteCapability`";

    @Getter
    final String description = "In Jackson 3, `JsonGenerator.canWriteBinaryNatively()` and `canWriteFormattedNumbers()` were removed " +
            "and replaced with the `StreamWriteCapability` enum. This recipe updates these method calls to use " +
            "`getWriteCapabilities().isEnabled(StreamWriteCapability.*)` instead.";

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(CAN_WRITE_BINARY_NATIVELY),
                        new UsesMethod<>(CAN_WRITE_FORMATTED_NUMBERS)
                ),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        if (CAN_WRITE_BINARY_NATIVELY.matches(mi)) {
                            maybeAddImport("com.fasterxml.jackson.core.StreamWriteCapability");
                            return JavaTemplate.builder("#{any(com.fasterxml.jackson.core.JsonGenerator)}.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_BINARY_NATIVELY)")
                                    .imports("com.fasterxml.jackson.core.StreamWriteCapability")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-2.+"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                        }

                        if (CAN_WRITE_FORMATTED_NUMBERS.matches(mi)) {
                            maybeAddImport("com.fasterxml.jackson.core.StreamWriteCapability");
                            return JavaTemplate.builder("#{any(com.fasterxml.jackson.core.JsonGenerator)}.getWriteCapabilities().isEnabled(StreamWriteCapability.CAN_WRITE_FORMATTED_NUMBERS)")
                                    .imports("com.fasterxml.jackson.core.StreamWriteCapability")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-core-2.+"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                        }

                        return mi;
                    }
                }
        );
    }
}
