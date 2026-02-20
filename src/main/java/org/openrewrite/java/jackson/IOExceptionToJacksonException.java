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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class IOExceptionToJacksonException extends Recipe {

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String JACKSON_EXCEPTION = "tools.jackson.core.JacksonException";

    private static final String OBJECT_MAPPER_PATTERN = "tools.jackson.databind.ObjectMapper *(..)";
    private static final String OBJECT_READER_PATTERN = "tools.jackson.databind.ObjectReader *(..)";
    private static final String OBJECT_WRITER_PATTERN = "tools.jackson.databind.ObjectWriter *(..)";

    private static final MethodMatcher OBJECT_MAPPER_MATCHER = new MethodMatcher(OBJECT_MAPPER_PATTERN, true);
    private static final MethodMatcher OBJECT_READER_MATCHER = new MethodMatcher(OBJECT_READER_PATTERN, true);
    private static final MethodMatcher OBJECT_WRITER_MATCHER = new MethodMatcher(OBJECT_WRITER_PATTERN, true);

    @Getter
    final String displayName = "Replace `IOException` with `JacksonException` in catch clauses";

    @Getter
    final String description = "In Jackson 3, `ObjectMapper` and related classes no longer throw `IOException`. " +
            "This recipe replaces `catch (IOException e)` with `catch (JacksonException e)` " +
            "when the try block only contains Jackson API calls that throw `IOException`.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(OBJECT_MAPPER_PATTERN),
                        new UsesMethod<>(OBJECT_READER_PATTERN),
                        new UsesMethod<>(OBJECT_WRITER_PATTERN)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Try visitTry(J.Try tryStatement, ExecutionContext ctx) {
                        J.Try try_ = super.visitTry(tryStatement, ctx);
                        if (FindMethods.find(try_, OBJECT_MAPPER_PATTERN).isEmpty() &&
                            FindMethods.find(try_, OBJECT_READER_PATTERN).isEmpty() &&
                            FindMethods.find(try_, OBJECT_WRITER_PATTERN).isEmpty()) {
                            return try_;
                        }
                        if (hasNonJacksonIOExceptionSource(try_)) {
                            return try_;
                        }
                        return try_.withCatches(ListUtils.map(try_.getCatches(), catch_ -> {
                            if (TypeUtils.isOfClassType(catch_.getParameter().getType(), IO_EXCEPTION)) {
                                maybeRemoveImport(IO_EXCEPTION);
                                maybeAddImport(JACKSON_EXCEPTION);
                                return (J.Try.Catch) new ChangeType(IO_EXCEPTION, JACKSON_EXCEPTION, true)
                                        .getVisitor().visit(catch_, ctx);
                            }
                            return catch_;
                        }));
                    }
                }
        );
    }

    private static boolean hasNonJacksonIOExceptionSource(J.Try try_) {
        AtomicBoolean found = new AtomicBoolean(false);
        new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean result) {
                if (result.get()) {
                    return method;
                }
                if (OBJECT_MAPPER_MATCHER.matches(method) ||
                    OBJECT_READER_MATCHER.matches(method) ||
                    OBJECT_WRITER_MATCHER.matches(method)) {
                    return method;
                }
                JavaType.Method methodType = method.getMethodType();
                if (methodType != null &&
                    methodType.getThrownExceptions().stream()
                            .anyMatch(te -> TypeUtils.isAssignableTo(IO_EXCEPTION, te))) {
                    result.set(true);
                }
                return method;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean result) {
                if (result.get()) {
                    return newClass;
                }
                JavaType.Method methodType = newClass.getMethodType();
                if (methodType != null &&
                    methodType.getThrownExceptions().stream()
                            .anyMatch(te -> TypeUtils.isAssignableTo(IO_EXCEPTION, te))) {
                    result.set(true);
                }
                return newClass;
            }
        }.visit(try_.getBody(), found);
        return found.get();
    }
}
