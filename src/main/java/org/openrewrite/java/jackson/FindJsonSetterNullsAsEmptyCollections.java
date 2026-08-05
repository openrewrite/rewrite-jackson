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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

public class FindJsonSetterNullsAsEmptyCollections extends Recipe {

    private static final String JACKSON_JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JACKSON_JSON_IGNORE_PROPERTIES = "com.fasterxml.jackson.annotation.JsonIgnoreProperties";
    private static final String JACKSON_JSON_SETTER = "com.fasterxml.jackson.annotation.JsonSetter";
    private static final String JACKSON_NULLS = "com.fasterxml.jackson.annotation.Nulls";
    private static final AnnotationMatcher JSON_IGNORE_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_IGNORE, true);
    private static final AnnotationMatcher JSON_IGNORE_PROPERTIES_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_IGNORE_PROPERTIES, true);
    private static final AnnotationMatcher JSON_SETTER_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_SETTER, true);

    @Getter
    final String displayName = "Find `@JsonSetter(nulls = Nulls.AS_EMPTY)` on empty collection fields";

    @Getter
    final String description = "Find `Map` and `Collection` fields that carry `@JsonSetter(nulls = Nulls.AS_EMPTY)`, are initialized " +
            "with an empty collection, and are not annotated with `@JsonIgnore`. In rewrite-jackson 1.17.0 through 1.28.0 the " +
            "Jackson 2 to 3 migration replaced `@JsonIgnore` with `@JsonSetter(nulls = Nulls.AS_EMPTY)` on exactly these fields, " +
            "which starts serializing properties that were deliberately hidden wherever the field is otherwise visible, such as " +
            "through a getter. Run this recipe to audit repositories migrated with those versions; every match that should stay " +
            "out of the JSON output needs `@JsonIgnore` restored.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JACKSON_JSON_SETTER, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

                        if (vd.getType() == null || !isMapOrCollectionType(vd.getType())) {
                            return vd;
                        }

                        boolean nullsAsEmpty = false;
                        for (J.Annotation annotation : vd.getLeadingAnnotations()) {
                            if (JSON_IGNORE_MATCHER.matches(annotation)) {
                                return vd;
                            }
                            if (JSON_SETTER_MATCHER.matches(annotation) && isNullsAsEmpty(annotation)) {
                                nullsAsEmpty = true;
                            }
                        }
                        if (!nullsAsEmpty) {
                            return vd;
                        }

                        Set<String> classIgnored = enclosingClassIgnoredProperties();
                        boolean anyFlaggable = false;
                        for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                            if (classIgnored.contains(variable.getSimpleName())) {
                                continue;
                            }
                            if (isEmptyCollectionConstructor(variable.getInitializer())) {
                                anyFlaggable = true;
                                break;
                            }
                        }
                        if (!anyFlaggable) {
                            return vd;
                        }

                        return SearchResult.found(vd, "Verify this field should be serialized; `@JsonIgnore` may have been removed here");
                    }

                    private boolean isMapOrCollectionType(JavaType type) {
                        if (type instanceof JavaType.FullyQualified) {
                            JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                            return fq.isAssignableTo("java.util.Map") ||
                                    fq.isAssignableTo("java.util.Collection");
                        }
                        return false;
                    }

                    private boolean isNullsAsEmpty(J.Annotation annotation) {
                        List<Expression> args = annotation.getArguments();
                        if (args == null) {
                            return false;
                        }
                        for (Expression arg : args) {
                            if (arg instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) arg;
                                if (assignment.getVariable() instanceof J.Identifier &&
                                        "nulls".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                                    return isNullsAsEmptyValue(assignment.getAssignment());
                                }
                            }
                        }
                        return false;
                    }

                    private boolean isNullsAsEmptyValue(Expression value) {
                        if (!TypeUtils.isOfClassType(value.getType(), JACKSON_NULLS)) {
                            return false;
                        }
                        if (value instanceof J.FieldAccess) {
                            return "AS_EMPTY".equals(((J.FieldAccess) value).getSimpleName());
                        }
                        return value instanceof J.Identifier && "AS_EMPTY".equals(((J.Identifier) value).getSimpleName());
                    }

                    private Set<String> enclosingClassIgnoredProperties() {
                        J.ClassDeclaration cls = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (cls == null) {
                            return emptySet();
                        }
                        Set<String> ignored = new HashSet<>();
                        for (J.Annotation ann : cls.getLeadingAnnotations()) {
                            if (!JSON_IGNORE_PROPERTIES_MATCHER.matches(ann) || ann.getArguments() == null) {
                                continue;
                            }
                            for (Expression arg : ann.getArguments()) {
                                Expression value = arg;
                                if (arg instanceof J.Assignment) {
                                    J.Assignment a = (J.Assignment) arg;
                                    if (!(a.getVariable() instanceof J.Identifier) ||
                                            !"value".equals(((J.Identifier) a.getVariable()).getSimpleName())) {
                                        continue;
                                    }
                                    value = a.getAssignment();
                                }
                                collectStrings(value, ignored);
                            }
                        }
                        return ignored;
                    }

                    private void collectStrings(Expression value, Set<String> into) {
                        if (value instanceof J.Literal) {
                            Object v = ((J.Literal) value).getValue();
                            if (v instanceof String) {
                                into.add((String) v);
                            }
                        } else if (value instanceof J.NewArray && ((J.NewArray) value).getInitializer() != null) {
                            for (Expression e : ((J.NewArray) value).getInitializer()) {
                                collectStrings(e, into);
                            }
                        }
                    }

                    private boolean isEmptyCollectionConstructor(@Nullable Expression init) {
                        if (init instanceof J.NewClass) {
                            List<Expression> args = ((J.NewClass) init).getArguments();
                            return args == null || args.isEmpty() ||
                                    (args.size() == 1 && args.get(0) instanceof J.Empty);
                        }
                        return false;
                    }
                }
        );
    }
}
