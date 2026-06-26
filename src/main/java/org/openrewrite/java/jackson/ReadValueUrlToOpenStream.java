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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

/**
 * Migrates {@code ObjectMapper.readValue(URL, ...)} calls to feed
 * {@code URL.openStream()} into the surviving {@code readValue(InputStream, ...)}
 * overload. Jackson 3.x removed all three {@code URL}-accepting {@code readValue}
 * overloads ({@code Class}, {@code JavaType}, {@code TypeReference}) from
 * {@code ObjectMapper}; bare package renames downstream would leave call sites
 * uncompilable. {@code URL.openStream()} is what Jackson 2.x's {@code readValue(URL, ...)}
 * did internally, so the substitution is semantics-preserving for the default
 * {@code JsonParser.Feature.AUTO_CLOSE_SOURCE} (which is on by default and causes
 * Jackson to close the stream after reading).
 */
public class ReadValueUrlToOpenStream extends Recipe {
    private static final MethodMatcher READ_VALUE_URL = new MethodMatcher(
            "com.fasterxml.jackson.databind.ObjectMapper readValue(java.net.URL, ..)", true);

    @Getter
    final String displayName = "Migrate `ObjectMapper.readValue(URL, ...)` to use `openStream()`";

    @Getter
    final String description = "Jackson 3.x removed every `URL`-accepting `readValue` overload from " +
            "`ObjectMapper`. Rewrite call sites to feed `URL.openStream()` into the surviving " +
            "`readValue(InputStream, ...)` overload, which is what `readValue(URL, ...)` did " +
            "internally in Jackson 2.x. The caller's checked-exception story is unchanged: " +
            "`URL.openStream()` declares `IOException`, the same checked exception the removed " +
            "`readValue(URL, ...)` declared.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(READ_VALUE_URL),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!READ_VALUE_URL.matches(mi) || mi.getSelect() == null) {
                            return mi;
                        }
                        return JavaTemplate.builder("#{any(com.fasterxml.jackson.databind.ObjectMapper)}" +
                                        ".readValue(#{any(java.net.URL)}.openStream(), #{any()})")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "jackson-databind-2", "jackson-core-2", "jackson-annotations-2"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(),
                                        mi.getSelect(), mi.getArguments().get(0), mi.getArguments().get(1));
                    }
                });
    }
}
