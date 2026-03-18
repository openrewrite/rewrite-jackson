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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.InlineVariable;

import java.util.*;

import static java.util.Collections.singleton;

@Getter
public class MigrateMapperSettersToBuilder extends Recipe {

    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String JSON_MAPPER = "com.fasterxml.jackson.databind.json.JsonMapper";

    private static final MethodMatcher JSON_MAPPER_NO_ARG_CTOR = new MethodMatcher(JSON_MAPPER + " <constructor>()");

    private static final String INVOCATIONS_TO_REMOVE = "INVOCATIONS_TO_REMOVE";

    @RequiredArgsConstructor
    enum SetterToBuilderMapping {
        SET_FILTER_PROVIDER("setFilterProvider", "filterProvider"),
        ADD_MIX_IN("addMixIn", "addMixIn"),
        SET_DATE_FORMAT("setDateFormat", "defaultDateFormat"),
        ADD_HANDLER("addHandler", "addHandler"),
        DISABLE("disable", "disable"),
        ENABLE("enable", "enable"),
        REGISTER_MODULE("registerModule", "addModule");

        final String setterName;
        final String builderName;

        static @Nullable SetterToBuilderMapping fromSetter(String name) {
            for (SetterToBuilderMapping m : values()) {
                if (m.setterName.equals(name)) {
                    return m;
                }
            }
            return null;
        }
    }

    final String displayName = "Migrate `JsonMapper` setter calls to builder pattern";
    final String description = "In Jackson 3, `JsonMapper` is immutable. " +
            "Configuration methods like `setFilterProvider`, `addMixIn`, `disable`, `enable`, etc. " +
            "must be called on the builder instead. This recipe migrates setter calls to the builder " +
            "pattern when safe, or adds TODO comments when automatic migration is not possible.";
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JSON_MAPPER, false),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);

                        if (!JSON_MAPPER_NO_ARG_CTOR.matches(nc)) {
                            return nc;
                        }

                        // Determine variable name from local declaration or field assignment
                        String varName;
                        J.VariableDeclarations.NamedVariable namedVar = getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
                        J.Assignment assignment = getCursor().firstEnclosing(J.Assignment.class);
                        if (namedVar != null) {
                            varName = namedVar.getSimpleName();
                        } else if (assignment != null && assignment.getVariable() instanceof J.Identifier) {
                            varName = ((J.Identifier) assignment.getVariable()).getSimpleName();
                        } else {
                            return nc;
                        }

                        J.Block block = getCursor().firstEnclosing(J.Block.class);
                        if (block == null) {
                            return nc;
                        }

                        // Collect known setter calls that appear before any unknown mapper
                        // usage (unknown call on the variable, or variable passed elsewhere).
                        List<J.MethodInvocation> builderSetters = new ArrayList<>();
                        boolean collecting = true;

                        for (Statement stmt : block.getStatements()) {
                            if (!collecting) {
                                break;
                            }

                            // Skip the declaration or assignment that contains the constructor
                            if (stmt instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                if (vd.getVariables().stream().anyMatch(v -> v.getSimpleName().equals(varName))) {
                                    continue;
                                }
                            }
                            if (stmt instanceof J.Assignment) {
                                J.Assignment a = (J.Assignment) stmt;
                                if (a.getVariable() instanceof J.Identifier &&
                                        varName.equals(((J.Identifier) a.getVariable()).getSimpleName())) {
                                    continue;
                                }
                            }

                            if (stmt instanceof J.MethodInvocation) {
                                J.MethodInvocation mi = (J.MethodInvocation) stmt;
                                if (isCallOnVariable(mi, varName)) {
                                    SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                                    if (mapping != null) {
                                        builderSetters.add(mi);
                                        continue;
                                    }
                                    collecting = false;
                                    continue;
                                }
                            }

                            if (referencesVariable(stmt, varName)) {
                                collecting = false;
                            }
                        }

                        if (builderSetters.isEmpty()) {
                            return nc;
                        }

                        // Build template with #{any()} placeholders
                        StringBuilder templateCode = new StringBuilder("JsonMapper.builder()");
                        List<Expression> templateArgs = new ArrayList<>();

                        for (J.MethodInvocation mi : builderSetters) {
                            SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                            assert mapping != null;
                            templateCode.append("\n.").append(mapping.builderName).append("(");
                            for (int i = 0; i < mi.getArguments().size(); i++) {
                                if (i > 0) {
                                    templateCode.append(", ");
                                }
                                templateCode.append("#{any()}");
                                templateArgs.add(mi.getArguments().get(i));
                            }
                            templateCode.append(")");
                        }
                        templateCode.append("\n.build()");

                        // Mark setter invocations for removal
                        Cursor blockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
                        Set<UUID> toRemove = blockCursor.getMessage(INVOCATIONS_TO_REMOVE);
                        if (toRemove == null) {
                            toRemove = new HashSet<>();
                            blockCursor.putMessage(INVOCATIONS_TO_REMOVE, toRemove);
                        }
                        for (J.MethodInvocation mi : builderSetters) {
                            toRemove.add(mi.getId());
                        }

                        maybeAddImport(JSON_MAPPER);

                        doAfterVisit(new InlineVariable().getVisitor());

                        return JavaTemplate.builder(templateCode.toString())
                                .imports(JSON_MAPPER)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-core-2", "jackson-databind-2"))
                                .build()
                                .apply(getCursor(), nc.getCoordinates().replace(), templateArgs.toArray());
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        if (!(mi.getSelect() instanceof J.Identifier) ||
                                !TypeUtils.isAssignableTo(JSON_MAPPER, mi.getSelect().getType())) {
                            return mi;
                        }

                        SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                        if (mapping == null) {
                            return mi;
                        }

                        // Check if this invocation should be removed (builder migration case)
                        Set<UUID> toRemove = getCursor().getNearestMessage(INVOCATIONS_TO_REMOVE);
                        if (toRemove != null && toRemove.contains(mi.getId())) {
                            return null;
                        }

                        // Not eligible for builder migration - add a TODO comment
                        String commentText = String.format(
                                " TODO %s was removed from JsonMapper in Jackson 3. " +
                                        "Use mapper.rebuild().%s(...).build() or move to the mapper's instantiation site. ",
                                mapping.setterName, mapping.builderName);

                        if (hasComment(mi, commentText)) {
                            return mi;
                        }

                        String prefixWhitespace = mi.getPrefix().getWhitespace();
                        TextComment comment = new TextComment(true, commentText, prefixWhitespace, Markers.EMPTY);
                        return mi.withPrefix(mi.getPrefix().withComments(
                                ListUtils.concat(mi.getPrefix().getComments(), comment)));
                    }

                    private boolean hasComment(J.MethodInvocation mi, String commentText) {
                        return mi.getPrefix().getComments().stream()
                                .filter(TextComment.class::isInstance)
                                .map(c -> ((TextComment) c).getText().trim())
                                .anyMatch(t -> t.equals(commentText.trim()));
                    }

                    private boolean isCallOnVariable(J.MethodInvocation mi, String varName) {
                        return mi.getSelect() instanceof J.Identifier &&
                                varName.equals(((J.Identifier) mi.getSelect()).getSimpleName()) &&
                                TypeUtils.isAssignableTo(OBJECT_MAPPER, mi.getSelect().getType());
                    }

                    private boolean referencesVariable(Statement stmt, String varName) {
                        return !new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier ident, Set<String> set) {
                                if (varName.equals(ident.getSimpleName()) &&
                                        TypeUtils.isAssignableTo(OBJECT_MAPPER, ident.getType())) {
                                    set.add(varName);
                                }
                                return ident;
                            }
                        }.reduce(stmt, new HashSet<>()).isEmpty();
                    }
                }
        );
    }
}
