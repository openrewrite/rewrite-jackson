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
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.InlineVariable;

import java.util.*;

import static java.util.Collections.singleton;

@Getter
public class MigrateMapperSettersToBuilder extends Recipe {

    private static final String JSON_MAPPER = "com.fasterxml.jackson.databind.json.JsonMapper";

    private static final MethodMatcher JSON_MAPPER_NO_ARG_CTOR = new MethodMatcher(JSON_MAPPER + " <constructor>()");

    private static final String INVOCATIONS_TO_REMOVE = "INVOCATIONS_TO_REMOVE";

    @RequiredArgsConstructor
    enum SetterToBuilderMapping {
        // Feature enable/disable/configure (covers all feature types via overloading)
        CONFIGURE("configure", "configure"),
        DISABLE("disable", "disable"),
        ENABLE("enable", "enable"),

        // Module registration
        REGISTER_MODULE("registerModule", "addModule"),
        REGISTER_MODULES("registerModules", "addModules"),
        FIND_AND_REGISTER_MODULES("findAndRegisterModules", "findAndAddModules"),

        // Mix-in & subtype registration
        ADD_MIX_IN("addMixIn", "addMixIn"),
        REGISTER_SUBTYPES("registerSubtypes", "registerSubtypes"),

        // Deserialization problem handlers
        ADD_HANDLER("addHandler", "addHandler"),
        CLEAR_PROBLEM_HANDLERS("clearProblemHandlers", "clearProblemHandlers"),

        // Default typing
        ACTIVATE_DEFAULT_TYPING("activateDefaultTyping", "activateDefaultTyping"),
        ACTIVATE_DEFAULT_TYPING_AS_PROPERTY("activateDefaultTypingAsProperty", "activateDefaultTypingAsProperty"),
        DEACTIVATE_DEFAULT_TYPING("deactivateDefaultTyping", "deactivateDefaultTyping"),
        SET_DEFAULT_TYPING("setDefaultTyping", "setDefaultTyping"),

        // Serialization settings
        SET_FILTER_PROVIDER("setFilterProvider", "filterProvider"),
        SET_SERIALIZER_FACTORY("setSerializerFactory", "serializerFactory"),
        SET_DEFAULT_PRETTY_PRINTER("setDefaultPrettyPrinter", "defaultPrettyPrinter"),

        // Deserialization settings
        SET_INJECTABLE_VALUES("setInjectableValues", "injectableValues"),
        SET_NODE_FACTORY("setNodeFactory", "nodeFactory"),
        SET_CONSTRUCTOR_DETECTOR("setConstructorDetector", "constructorDetector"),
        SET_CACHE_PROVIDER("setCacheProvider", "cacheProvider"),

        // Introspection & naming
        SET_ANNOTATION_INTROSPECTOR("setAnnotationIntrospector", "annotationIntrospector"),
        SET_TYPE_FACTORY("setTypeFactory", "typeFactory"),
        SET_SUBTYPES_RESOLVER("setSubtypeResolver", "subtypeResolver"),
        SET_VISIBILITY("setVisibility", "visibility"),
        SET_HANDLER_INSTANTIATOR("setHandlerInstantiator", "handlerInstantiator"),
        SET_PROPERTY_NAMING_STRATEGY("setPropertyNamingStrategy", "propertyNamingStrategy"),
        SET_ENUM_NAMING_STRATEGY("setEnumNamingStrategy", "enumNamingStrategy"),
        SET_ACCESSOR_NAMING("setAccessorNaming", "accessorNaming"),
        SET_POLYMORPHIC_TYPE_VALIDATOR("setPolymorphicTypeValidator", "polymorphicTypeValidator"),

        // Global defaults
        SET_DATE_FORMAT("setDateFormat", "defaultDateFormat"),
        SET_TIME_ZONE("setTimeZone", "defaultTimeZone"),
        SET_LOCALE("setLocale", "defaultLocale"),
        SET_BASE64_VARIANT("setBase64Variant", "defaultBase64Variant"),
        SET_DEFAULT_ATTRIBUTES("setDefaultAttributes", "defaultAttributes"),
        SET_DEFAULT_PROPERTY_INCLUSION("setDefaultPropertyInclusion", "defaultPropertyInclusion"),
        SET_DEFAULT_SETTER_INFO("setDefaultSetterInfo", "defaultSetterInfo"),
        SET_DEFAULT_MERGEABLE("setDefaultMergeable", "defaultMergeable"),
        SET_DEFAULT_LENIENCY("setDefaultLeniency", "defaultLeniency");

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

                        // Determine variable identifier from local declaration or field assignment
                        J.Identifier varIdent;
                        J.VariableDeclarations.NamedVariable namedVar = getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
                        J.Assignment assignment = getCursor().firstEnclosing(J.Assignment.class);
                        if (namedVar != null) {
                            varIdent = namedVar.getName();
                        } else if (assignment != null && assignment.getVariable() instanceof J.Identifier) {
                            varIdent = (J.Identifier) assignment.getVariable();
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
                        Set<J.Identifier> intermediateVars = new HashSet<>();
                        boolean collecting = true;

                        for (Statement stmt : block.getStatements()) {
                            if (!collecting) {
                                break;
                            }

                            // Skip the declaration or assignment that contains the constructor
                            if (stmt instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                if (vd.getVariables().stream().anyMatch(v -> SemanticallyEqual.areEqual(v.getName(), varIdent))) {
                                    continue;
                                }
                                // Track variables declared between constructor and setters
                                for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                                    intermediateVars.add(v.getName());
                                }
                            }
                            if (stmt instanceof J.Assignment) {
                                J.Assignment a = (J.Assignment) stmt;
                                if (a.getVariable() instanceof J.Identifier &&
                                        SemanticallyEqual.areEqual(a.getVariable(), varIdent)) {
                                    continue;
                                }
                            }

                            if (stmt instanceof J.MethodInvocation) {
                                J.MethodInvocation mi = (J.MethodInvocation) stmt;
                                if (isCallOnVariable(mi, varIdent)) {
                                    SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                                    if (mapping != null) {
                                        // Can't fold if arguments reference variables declared after the constructor
                                        if (argumentReferencesAny(mi, intermediateVars)) {
                                            collecting = false;
                                            continue;
                                        }
                                        builderSetters.add(mi);
                                        continue;
                                    }
                                    collecting = false;
                                    continue;
                                }
                            }

                            if (referencesVariable(stmt, varIdent)) {
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

                        // Check if this invocation should be removed (builder migration case)
                        Set<UUID> toRemove = getCursor().getNearestMessage(INVOCATIONS_TO_REMOVE);
                        if (toRemove != null && toRemove.contains(mi.getId())) {
                            return null;
                        }

                        if (!(mi.getSelect() instanceof J.Identifier) ||
                                !TypeUtils.isAssignableTo(JSON_MAPPER, mi.getSelect().getType())) {
                            return mi;
                        }

                        SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                        if (mapping == null) {
                            return mi;
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

                    private boolean argumentReferencesAny(J.MethodInvocation mi, Set<J.Identifier> intermediateVars) {
                        return !new JavaIsoVisitor<Set<Boolean>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier ident, Set<Boolean> set) {
                                for (J.Identifier iv : intermediateVars) {
                                    if (SemanticallyEqual.areEqual(ident, iv)) {
                                        set.add(true);
                                    }
                                }
                                return ident;
                            }
                        }.reduce(mi.getArguments(), new HashSet<>()).isEmpty();
                    }

                    private boolean isCallOnVariable(J.MethodInvocation mi, J.Identifier varIdent) {
                        return mi.getSelect() instanceof J.Identifier &&
                                SemanticallyEqual.areEqual(mi.getSelect(), varIdent);
                    }

                    private boolean referencesVariable(Statement stmt, J.Identifier varIdent) {
                        return !new JavaIsoVisitor<Set<Boolean>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier ident, Set<Boolean> set) {
                                if (SemanticallyEqual.areEqual(ident, varIdent)) {
                                    set.add(true);
                                }
                                return ident;
                            }
                        }.reduce(stmt, new HashSet<>()).isEmpty();
                    }
                }
        );
    }
}
