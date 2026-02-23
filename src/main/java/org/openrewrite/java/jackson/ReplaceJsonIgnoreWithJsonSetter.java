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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class ReplaceJsonIgnoreWithJsonSetter extends Recipe {

    private static final String JACKSON_JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JACKSON_JSON_SETTER = "com.fasterxml.jackson.annotation.JsonSetter";
    private static final String JACKSON_NULLS = "com.fasterxml.jackson.annotation.Nulls";
    private static final AnnotationMatcher JSON_IGNORE_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_IGNORE, true);
    private static final AnnotationMatcher JSON_SETTER_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_SETTER, true);

    @Getter
    final String displayName = "Replace `@JsonIgnore` with `@JsonSetter` on empty collection fields";

    @Getter
    final String description = "In Jackson 3, `@JsonIgnore` on fields initialized with empty collections " +
            "causes the field value to become `null` instead of maintaining the empty collection. " +
            "This recipe replaces `@JsonIgnore` with `@JsonSetter(nulls = Nulls.AS_EMPTY)` on `Map` and `Collection` " +
            "fields that have an empty collection initializer.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JACKSON_JSON_IGNORE, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        // Check: field type must be Map or Collection
                        if (vd.getType() == null || !isMapOrCollectionType(vd.getType())) {
                            return vd;
                        }

                        // Check: must have @JsonIgnore annotation (not @JsonIgnore(false))
                        J.Annotation jsonIgnoreAnnotation = null;
                        for (J.Annotation annotation : vd.getLeadingAnnotations()) {
                            if (JSON_SETTER_MATCHER.matches(annotation)) {
                                return vd; // Check: must not already have @JsonSetter
                            }
                            if (JSON_IGNORE_MATCHER.matches(annotation) && !isJsonIgnoreFalse(annotation)) {
                                jsonIgnoreAnnotation = annotation;
                            }
                        }
                        if (jsonIgnoreAnnotation == null) {
                            return vd;
                        }

                        // Check: must have initializer that is a new empty collection
                        if (vd.getVariables().isEmpty()) {
                            return vd;
                        }
                        J.VariableDeclarations.NamedVariable variable = vd.getVariables().get(0);
                        if (variable.getInitializer() == null || !isEmptyCollectionConstructor(variable.getInitializer())) {
                            return vd;
                        }

                        // Remove @JsonIgnore
                        final J.Annotation toRemove = jsonIgnoreAnnotation;
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + JACKSON_JSON_IGNORE) {
                            @Override
                            public boolean matches(J.Annotation ann) {
                                return ann == toRemove;
                            }
                        }));
                        maybeRemoveImport(JACKSON_JSON_IGNORE);

                        // Add @JsonSetter(nulls = Nulls.AS_EMPTY)
                        maybeAddImport(JACKSON_JSON_SETTER);
                        maybeAddImport(JACKSON_NULLS);

                        return JavaTemplate
                                .builder("@JsonSetter(nulls = Nulls.AS_EMPTY)")
                                .imports(JACKSON_JSON_SETTER, JACKSON_NULLS)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-annotations-2"))
                                .build()
                                .apply(
                                        getCursor(),
                                        vd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                                );
                    }

                    private boolean isMapOrCollectionType(JavaType type) {
                        if (type instanceof JavaType.FullyQualified) {
                            JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                            return fq.isAssignableTo("java.util.Map") ||
                                    fq.isAssignableTo("java.util.Collection");
                        }
                        return false;
                    }

                    private boolean isJsonIgnoreFalse(J.Annotation annotation) {
                        List<Expression> args = annotation.getArguments();
                        if (args == null || args.isEmpty() || args.get(0) instanceof J.Empty) {
                            return false;
                        }
                        // @JsonIgnore(false) or @JsonIgnore(value = false)
                        Expression arg = args.get(0);
                        if (J.Literal.isLiteralValue(arg, false)) {
                            return true;
                        }
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            return J.Literal.isLiteralValue(assignment.getAssignment(), false);
                        }
                        return false;
                    }

                    private boolean isEmptyCollectionConstructor(Expression init) {
                        if (init instanceof J.NewClass) {
                            J.NewClass newClass = (J.NewClass) init;
                            List<Expression> args = newClass.getArguments();
                            return args == null || args.isEmpty() ||
                                    (args.size() == 1 && args.get(0) instanceof J.Empty);
                        }
                        return false;
                    }
                }
        );
    }
}
