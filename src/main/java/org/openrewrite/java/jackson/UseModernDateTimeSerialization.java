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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.*;

public class UseModernDateTimeSerialization extends Recipe {

    private static final String JACKSON_JSON_FORMAT = "com.fasterxml.jackson.annotation.JsonFormat";
    private static final AnnotationMatcher JSON_FORMAT_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_FORMAT, true);

    private static final Set<String> JAVA_TIME_TYPES = new HashSet<>(Arrays.asList(
            "java.time.LocalDate",
            "java.time.LocalDateTime",
            "java.time.LocalTime",
            "java.time.ZonedDateTime",
            "java.time.OffsetDateTime",
            "java.time.Instant"
    ));

    // Common ISO-8601 patterns that are now Jackson 3 defaults
    private static final Set<String> ISO_8601_PATTERNS = new HashSet<>(Arrays.asList(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd",
            "HH:mm:ss"
    ));

    @Override
    public String getDisplayName() {
        return "Use modern date/time serialization defaults";
    }

    @Override
    public String getDescription() {
        return "Remove redundant `@JsonFormat` annotations on `java.time` types that specify ISO-8601 patterns, " +
                "as Jackson 3 uses ISO-8601 as the default format (with `WRITE_DATES_AS_TIMESTAMPS` now disabled by default).";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JACKSON_JSON_FORMAT, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        // Check if this is a java.time type
                        if (vd.getType() == null || !isJavaTimeType(vd.getType())) {
                            return vd;
                        }

                        // Check for @JsonFormat annotations with ISO-8601 patterns
                        for (J.Annotation annotation : vd.getLeadingAnnotations()) {
                            if (JSON_FORMAT_MATCHER.matches(annotation) && isRedundantIso8601Format(annotation)) {
                                doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + JACKSON_JSON_FORMAT) {
                                    @Override
                                    public boolean matches(J.Annotation ann) {
                                        return ann == annotation;
                                    }
                                }));
                                maybeRemoveImport(JACKSON_JSON_FORMAT);
                            }
                        }

                        return vd;
                    }

                    private boolean isJavaTimeType(JavaType type) {
                        if (type instanceof JavaType.FullyQualified) {
                            String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                            return JAVA_TIME_TYPES.contains(fqn);
                        }
                        return false;
                    }

                    private boolean isRedundantIso8601Format(J.Annotation annotation) {
                        List<Expression> args = annotation.getArguments();
                        if (args == null || args.size() != 1) {
                            // Keep annotations with no arguments or multiple arguments
                            return false;
                        }

                        Expression arg = args.get(0);
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            if (assignment.getVariable() instanceof J.Identifier &&
                                    "pattern".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                                if (assignment.getAssignment() instanceof J.Literal) {
                                    J.Literal literal = (J.Literal) assignment.getAssignment();
                                    if (literal.getValue() instanceof String) {
                                        String pattern = (String) literal.getValue();
                                        return ISO_8601_PATTERNS.contains(pattern);
                                    }
                                }
                            }
                        }

                        return false;
                    }
                }
        );
    }
}
