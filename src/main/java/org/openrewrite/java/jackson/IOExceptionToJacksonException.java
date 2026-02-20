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
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

import static java.util.Collections.singleton;

public class IOExceptionToJacksonException extends Recipe {

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String JACKSON_EXCEPTION = "tools.jackson.core.JacksonException";

    private static final String OBJECT_MAPPER_PATTERN = "tools.jackson.databind.ObjectMapper *(..)";
    private static final String OBJECT_READER_PATTERN = "tools.jackson.databind.ObjectReader *(..)";
    private static final String OBJECT_WRITER_PATTERN = "tools.jackson.databind.ObjectWriter *(..)";

    @Getter
    final String displayName = "Replace `IOException` with `JacksonException` in catch clauses";

    @Getter
    final String description = "In Jackson 3, `ObjectMapper` and related classes no longer throw `IOException`. " +
            "This recipe replaces `catch (IOException e)` with `catch (JacksonException e)` " +
            "when the try block contains Jackson API calls.";

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
}
