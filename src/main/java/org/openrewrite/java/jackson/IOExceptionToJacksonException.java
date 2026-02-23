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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class IOExceptionToJacksonException extends Recipe {

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String JACKSON_EXCEPTION = "tools.jackson.core.JacksonException";

    private static final String OBJECT_MAPPER_PATTERN = "com.fasterxml.jackson.databind.ObjectMapper *(..)";
    private static final String OBJECT_READER_PATTERN = "com.fasterxml.jackson.databind.ObjectReader *(..)";
    private static final String OBJECT_WRITER_PATTERN = "com.fasterxml.jackson.databind.ObjectWriter *(..)";

    private static final MethodMatcher OBJECT_MAPPER_MATCHER = new MethodMatcher(OBJECT_MAPPER_PATTERN, true);
    private static final MethodMatcher OBJECT_READER_MATCHER = new MethodMatcher(OBJECT_READER_PATTERN, true);
    private static final MethodMatcher OBJECT_WRITER_MATCHER = new MethodMatcher(OBJECT_WRITER_PATTERN, true);

    final String displayName = "Replace `IOException` with `JacksonException` in catch clauses";

    final String description = "In Jackson 3, `ObjectMapper` and related classes no longer throw `IOException`. " +
            "This recipe replaces `catch (IOException e)` with `catch (JacksonException e)` " +
            "when the try block contains Jackson API calls. When the try block also contains " +
            "non-Jackson code that throws `IOException`, the catch is changed to a multi-catch " +
            "`catch (JacksonException | IOException e)`.";

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
                            return addJacksonExceptionCatch(try_, ctx);
                        }
                        return try_.withCatches(ListUtils.map(try_.getCatches(), catch_ -> {
                            if (catchesIOException(catch_)) {
                                maybeRemoveImport(IO_EXCEPTION);
                                maybeAddImport(JACKSON_EXCEPTION);
                                return (J.Try.Catch) new ChangeType(IO_EXCEPTION, JACKSON_EXCEPTION, true)
                                        .getVisitor().visitNonNull(catch_, ctx, getCursor().getParentOrThrow());
                            }
                            return catch_;
                        }));
                    }

                    private J.Try addJacksonExceptionCatch(J.Try try_, ExecutionContext ctx) {
                        List<J.Try.Catch> catches = try_.getCatches();
                        if (catches.stream().anyMatch(IOExceptionToJacksonException::catchesJacksonException)) {
                            return try_;
                        }
                        return try_.withCatches(ListUtils.map(catches, catch_ -> {
                            if (!catchesIOException(catch_)) {
                                return catch_;
                            }
                            J.Try.Catch jacksonCatch = (J.Try.Catch) new ChangeType(IO_EXCEPTION, JACKSON_EXCEPTION, true)
                                    .getVisitor().visitNonNull(catch_, ctx, getCursor().getParentOrThrow());
                            J.VariableDeclarations ioParam = catch_.getParameter().getTree();

                            if (ioParam.getTypeExpression() instanceof J.MultiCatch) {
                                // Insert JacksonException before IOException in existing multi-catch
                                J.MultiCatch origMultiCatch = (J.MultiCatch) ioParam.getTypeExpression();
                                J.MultiCatch changedMultiCatch = (J.MultiCatch) jacksonCatch.getParameter().getTree().getTypeExpression();
                                NameTree jacksonAlt = null;
                                for (JRightPadded<NameTree> rp : changedMultiCatch.getPadding().getAlternatives()) {
                                    if (TypeUtils.isOfClassType(rp.getElement().getType(), JACKSON_EXCEPTION)) {
                                        jacksonAlt = rp.getElement();
                                        break;
                                    }
                                }
                                if (jacksonAlt == null) {
                                    return catch_;
                                }
                                NameTree finalJacksonAlt = jacksonAlt;
                                List<JRightPadded<NameTree>> newAlts = new ArrayList<>();
                                for (JRightPadded<NameTree> rp : origMultiCatch.getPadding().getAlternatives()) {
                                    if (TypeUtils.isOfClassType(rp.getElement().getType(), IO_EXCEPTION)) {
                                        newAlts.add(JRightPadded.build((NameTree) finalJacksonAlt.withPrefix(Space.SINGLE_SPACE)).withAfter(Space.SINGLE_SPACE));
                                    }
                                    newAlts.add(rp);
                                }
                                maybeAddImport(JACKSON_EXCEPTION);
                                return catch_.withParameter(catch_.getParameter().withTree(
                                        ioParam.withTypeExpression(origMultiCatch.getPadding().withAlternatives(newAlts))));
                            }

                            // Single type: create new multi-catch with JacksonException | IOException
                            NameTree jacksonType = jacksonCatch.getParameter().getTree().getTypeExpression();
                            J.MultiCatch multiCatch = new J.MultiCatch(
                                    Tree.randomId(),
                                    jacksonType.getPrefix(),
                                    Markers.EMPTY,
                                    Arrays.asList(
                                            JRightPadded.build((NameTree) jacksonType.withPrefix(Space.EMPTY)).withAfter(Space.SINGLE_SPACE),
                                            JRightPadded.build(ioParam.getTypeExpression().withPrefix(Space.SINGLE_SPACE))
                                    )
                            );
                            maybeAddImport(JACKSON_EXCEPTION);
                            return catch_.withParameter(catch_.getParameter().withTree(ioParam.withTypeExpression(multiCatch)));
                        }));
                    }
                }
        );
    }

    private static boolean catchesIOException(J.Try.Catch catch_) {
        J.VariableDeclarations param = catch_.getParameter().getTree();
        if (param.getTypeExpression() instanceof J.MultiCatch) {
            return ((J.MultiCatch) param.getTypeExpression()).getAlternatives().stream()
                    .anyMatch(alt -> TypeUtils.isOfClassType(alt.getType(), IO_EXCEPTION));
        }
        return TypeUtils.isOfClassType(catch_.getParameter().getType(), IO_EXCEPTION);
    }

    private static boolean catchesJacksonException(J.Try.Catch catch_) {
        J.VariableDeclarations param = catch_.getParameter().getTree();
        if (param.getTypeExpression() instanceof J.MultiCatch) {
            return ((J.MultiCatch) param.getTypeExpression()).getAlternatives().stream()
                    .anyMatch(alt -> TypeUtils.isAssignableTo(JACKSON_EXCEPTION, alt.getType()));
        }
        return TypeUtils.isAssignableTo(JACKSON_EXCEPTION, catch_.getParameter().getType());
    }

    private static boolean hasNonJacksonIOExceptionSource(J.Try try_) {
        return new JavaIsoVisitor<AtomicBoolean>() {
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

            @Override
            public J.Throw visitThrow(J.Throw thrown, AtomicBoolean result) {
                if (result.get()) {
                    return thrown;
                }
                JavaType type = thrown.getException().getType();
                if (type != null && TypeUtils.isAssignableTo(IO_EXCEPTION, type)) {
                    result.set(true);
                }
                return thrown;
            }
        }.reduce(try_.getBody(), new AtomicBoolean(false)).get();
    }
}
