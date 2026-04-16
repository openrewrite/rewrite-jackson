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
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;
import static java.util.Collections.singleton;

@Getter
public class MigrateMapperSettersToBuilder extends Recipe {

    private static final String JSON_MAPPER = "com.fasterxml.jackson.databind.json.JsonMapper";

    /**
     * All format-aligned mapper types that support the builder pattern.
     * Includes JsonMapper and all format-specific mappers from UseFormatAlignedObjectMappers.
     */
    private static final List<String> ALL_MAPPERS = Arrays.asList(
            JSON_MAPPER,
            "com.fasterxml.jackson.dataformat.avro.AvroMapper",
            "com.fasterxml.jackson.dataformat.cbor.CBORMapper",
            "com.fasterxml.jackson.dataformat.csv.CsvMapper",
            "com.fasterxml.jackson.dataformat.ion.IonMapper",
            "com.fasterxml.jackson.dataformat.smile.SmileMapper",
            "com.fasterxml.jackson.dataformat.xml.XmlMapper",
            "com.fasterxml.jackson.dataformat.yaml.YAMLMapper"
    );

    private static final Map<MethodMatcher, String> MAPPER_CTORS;

    static {
        MAPPER_CTORS = new LinkedHashMap<>();
        for (String mapper : ALL_MAPPERS) {
            MAPPER_CTORS.put(new MethodMatcher(mapper + " <constructor>()"), mapper);
        }
    }

    private static final String INVOCATIONS_TO_REMOVE = "INVOCATIONS_TO_REMOVE";
    private static final String JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";

    /**
     * Kotlin top-level extension function {@code com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()}
     * that returns a {@link com.fasterxml.jackson.databind.ObjectMapper} pre-configured with the Kotlin module.
     * Treated as an alternate fluent-chain root equivalent to {@code new JsonMapper()}; emits
     * {@code jacksonMapperBuilder()} from the same Kotlin extensions package so the implicit
     * Kotlin module registration is preserved.
     */
    private static final String KOTLIN_EXTENSIONS_PACKAGE = "com.fasterxml.jackson.module.kotlin";
    private static final String KOTLIN_EXTENSIONS_FQN = KOTLIN_EXTENSIONS_PACKAGE + ".ExtensionsKt";
    private static final String JACKSON_MAPPER_BUILDER_FQN = KOTLIN_EXTENSIONS_FQN + ".jacksonMapperBuilder";
    private static final String JACKSON_OBJECT_MAPPER_NAME = "jacksonObjectMapper";
    private static final String JACKSON_MAPPER_BUILDER_NAME = "jacksonMapperBuilder";
    private static final MethodMatcher JACKSON_OBJECT_MAPPER_MATCHER =
            new MethodMatcher(KOTLIN_EXTENSIONS_FQN + " " + JACKSON_OBJECT_MAPPER_NAME + "()");

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
        SET_DEFAULT_LENIENCY("setDefaultLeniency", "defaultLeniency"),

        // SPECIAL CASES
        // UpdateSerializationInclusionConfiguration will take this one further
        SET_SERIALIZATION_INCLUSION("setSerializationInclusion", "serializationInclusion");

        final String setterName;
        final String builderName;

        private static final Map<String, SetterToBuilderMapping> BY_SETTER_NAME;

        static {
            Map<String, SetterToBuilderMapping> map = new HashMap<>();
            for (SetterToBuilderMapping m : values()) {
                map.put(m.setterName, m);
            }
            BY_SETTER_NAME = map;
        }

        static @Nullable SetterToBuilderMapping fromSetter(String name) {
            return BY_SETTER_NAME.get(name);
        }
    }

    final String displayName = "Migrate mapper setter calls to builder pattern";
    final String description = "In Jackson 3, `JsonMapper` and other format-aligned mappers are immutable. " +
            "Configuration methods like `setFilterProvider`, `addMixIn`, `disable`, `enable`, etc. " +
            "must be called on the builder instead. This recipe migrates setter calls to the builder " +
            "pattern when safe, or adds TODO comments when automatic migration is not possible.";
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<TreeVisitor<?, ExecutionContext>> preconditions = new ArrayList<>();
        for (String mapper : ALL_MAPPERS) {
            preconditions.add(new UsesType<>(mapper, false));
        }
        // Kotlin: the jacksonObjectMapper() extension returns ObjectMapper, so files that only
        // import ObjectMapper (not JsonMapper) still need to be visited. UsesMethod can also
        // miss the call when the parser only has a type table for jackson-module-kotlin and the
        // extension function lacks resolved method-type info, so include UsesType(ObjectMapper)
        // as a broader fallback.
        preconditions.add(new UsesType<>("com.fasterxml.jackson.databind.ObjectMapper", false));
        preconditions.add(new UsesMethod<>(KOTLIN_EXTENSIONS_FQN + " " + JACKSON_OBJECT_MAPPER_NAME + "()", false));
        return Preconditions.check(
                Preconditions.or(preconditions.toArray(new TreeVisitor[0])),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitBlock(J.Block block, ExecutionContext ctx) {
                        J visited = super.visitBlock(block, ctx);
                        if (visited != block) {
                            doAfterVisit(new UpdateSerializationInclusionConfiguration().getVisitor());
                            doAfterVisit(new UpdateAutoDetectVisibilityConfiguration().getVisitor());
                        }
                        return visited;
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);

                        String mapperFqn = matchingMapperFqn(nc);
                        if (mapperFqn == null) {
                            return nc;
                        }

                        // Skip if constructor is part of a fluent chain - handled by visitMethodInvocation
                        if (getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation) {
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

                        // Collect setter calls that appear after the constructor
                        List<J.MethodInvocation> builderSetters = collectStandaloneSetters(
                                block, varIdent, new HashSet<>());

                        if (builderSetters.isEmpty()) {
                            return nc;
                        }

                        // Mark setter invocations for removal
                        Cursor blockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
                        Set<UUID> toRemove = blockCursor.getMessage(INVOCATIONS_TO_REMOVE);
                        if (toRemove == null) {
                            toRemove = new HashSet<>();
                            blockCursor.putMessage(INVOCATIONS_TO_REMOVE, toRemove);
                        }
                        for (J.MethodInvocation setter : builderSetters) {
                            toRemove.add(setter.getId());
                        }

                        doAfterVisit(inlineWrappedVariable());
                        // Clean up empty init blocks after setter removal
                        doAfterVisit(removeEmptyInitBlocks());

                        return applyBuilderTemplate(mapperFqn, null, builderSetters, null, emptyList(),
                                nc.getCoordinates().replace(), ctx);
                    }

                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        // Check if this invocation should be removed (builder migration case)
                        Set<UUID> toRemove = getCursor().getNearestMessage(INVOCATIONS_TO_REMOVE);
                        if (toRemove != null && toRemove.contains(mi.getId())) {
                            return null;
                        }

                        // Check for fluent chain on new Mapper()
                        if (isFluentChainHead(method)) {
                            J result = tryMigrateFluentChain(mi, ctx);
                            if (result != null) {
                                return result;
                            }
                        }

                        if (!(mi.getSelect() instanceof J.Identifier)) {
                            return mi;
                        }

                        String matchedMapper = matchingMapperType(mi.getSelect().getType());
                        if (matchedMapper == null) {
                            return mi;
                        }

                        SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                        if (mapping == null) {
                            return mi;
                        }

                        // Not eligible for builder migration - add a TODO comment
                        String simpleMapperName = matchedMapper.substring(matchedMapper.lastIndexOf('.') + 1);
                        String commentText = String.format(
                                " TODO %s could not be folded to the builder of %s. " +
                                        "Use mapper.rebuild().%s(...).build() or move to the mapper's instantiation site.",
                                mapping.setterName, simpleMapperName, mapping.builderName);

                        if (hasComment(mi, commentText)) {
                            return mi;
                        }

                        String prefixWhitespace = mi.getPrefix().getWhitespace();
                        TextComment comment = new TextComment(false, commentText, prefixWhitespace, Markers.EMPTY);
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
                        if (!(mi.getSelect() instanceof J.Identifier)) {
                            return false;
                        }
                        J.Identifier sel = (J.Identifier) mi.getSelect();
                        // Use name + type comparison instead of SemanticallyEqual, because Kotlin
                        // sets different fieldType owners for declarations vs usages of local variables
                        return sel.getSimpleName().equals(varIdent.getSimpleName()) &&
                                TypeUtils.isOfType(sel.getType(), varIdent.getType());
                    }

                    private boolean isSetterReturnType(J.MethodInvocation mi) {
                        JavaType.Method methodType = mi.getMethodType();
                        if (methodType == null) {
                            return false;
                        }
                        JavaType returnType = methodType.getReturnType();
                        if (returnType == JavaType.Primitive.Void) {
                            return true;
                        }
                        return TypeUtils.isAssignableTo("com.fasterxml.jackson.databind.ObjectMapper", returnType);
                    }

                    private boolean referencesVariable(Statement stmt, J.Identifier varIdent) {
                        return !new JavaIsoVisitor<Set<Boolean>>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier ident, Set<Boolean> set) {
                                // Use name + type comparison instead of SemanticallyEqual for Kotlin compatibility
                                if (ident.getSimpleName().equals(varIdent.getSimpleName()) &&
                                        TypeUtils.isOfType(ident.getType(), varIdent.getType())) {
                                    set.add(true);
                                }
                                return ident;
                            }
                        }.reduce(stmt, new HashSet<>()).isEmpty();
                    }

                    /**
                     * Check if this method invocation is the outermost call in a fluent chain
                     * (i.e., it is not itself the select of another method invocation).
                     */
                    private boolean isFluentChainHead(J.MethodInvocation method) {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (parent instanceof J.MethodInvocation) {
                            return ((J.MethodInvocation) parent).getSelect() != method;
                        }
                        return true;
                    }

                    /**
                     * Returns true if the enclosing compilation unit imports
                     * {@code com.fasterxml.jackson.module.kotlin.jacksonObjectMapper}. Used as a
                     * fallback signal for matching the Kotlin top-level extension function when
                     * its method type info is not resolved (e.g. when only a type table — without
                     * jars — is on the parser classpath).
                     */
                    private boolean compilationUnitImportsJacksonObjectMapper() {
                        // Use JavaSourceFile rather than J.CompilationUnit so this matches both
                        // J.CompilationUnit (Java) and K.CompilationUnit (Kotlin), which only
                        // implement JavaSourceFile.
                        JavaSourceFile cu = getCursor().firstEnclosing(JavaSourceFile.class);
                        if (cu == null) {
                            return false;
                        }
                        String expected = KOTLIN_EXTENSIONS_PACKAGE + "." + JACKSON_OBJECT_MAPPER_NAME;
                        for (J.Import imp : cu.getImports()) {
                            if (expected.equals(imp.getTypeName()) ||
                                    expected.equals(imp.getQualid().toString())) {
                                return true;
                            }
                        }
                        return false;
                    }

                    /**
                     * If this method invocation is the outermost call of a fluent chain rooted
                     * at a format-aligned mapper constructor, migrate the chain to the builder pattern.
                     * When the chain is assigned to a variable, also collects standalone setter calls
                     * that follow the assignment and folds them into the builder.
                     */
                    private @Nullable J tryMigrateFluentChain(J.MethodInvocation mi, ExecutionContext ctx) {
                        String[] mapperHolder = new String[2];
                        List<J.MethodInvocation> chainCalls = collectFluentChain(mi, mapperHolder,
                                compilationUnitImportsJacksonObjectMapper());
                        if (chainCalls == null) {
                            return null;
                        }

                        String mapperFqn = mapperHolder[0];
                        String builderEntry = mapperHolder[1];

                        // Split chain into known setters (prefix) and remaining calls (suffix).
                        // A Kotlin `.apply { this.setX(...); setY(...) }` whose body is entirely
                        // known setters is unwrapped so the inner setters join the prefix.
                        List<J.MethodInvocation> setterCalls = new ArrayList<>();
                        List<SetterToBuilderMapping> mappings = new ArrayList<>();
                        List<J.MethodInvocation> suffixCalls = new ArrayList<>();
                        boolean hitUnknown = false;
                        for (J.MethodInvocation call : chainCalls) {
                            if (!hitUnknown) {
                                SetterToBuilderMapping m = SetterToBuilderMapping.fromSetter(call.getName().getSimpleName());
                                if (m != null) {
                                    setterCalls.add(call);
                                    mappings.add(m);
                                    continue;
                                }
                                List<J.MethodInvocation> unwrapped = tryUnwrapApplyBlock(call);
                                if (unwrapped != null) {
                                    for (J.MethodInvocation inner : unwrapped) {
                                        // Guaranteed non-null: tryUnwrapApplyBlock only returns
                                        // calls whose simple name maps to a known setter.
                                        SetterToBuilderMapping im = SetterToBuilderMapping.fromSetter(inner.getName().getSimpleName());
                                        setterCalls.add(inner);
                                        mappings.add(im);
                                    }
                                    continue;
                                }
                                hitUnknown = true;
                            }
                            suffixCalls.add(call);
                        }

                        // Need at least one known setter to migrate
                        if (setterCalls.isEmpty()) {
                            return null;
                        }

                        // When the fluent chain is assigned to a variable, also collect
                        // standalone setter calls that follow the assignment statement
                        J.Identifier varIdent = findVariableIdentifier();
                        if (varIdent != null) {
                            J.Block block = getCursor().firstEnclosing(J.Block.class);
                            if (block != null) {
                                List<J.MethodInvocation> standaloneSetters = collectStandaloneSetters(
                                        block, varIdent, new HashSet<>());
                                if (!standaloneSetters.isEmpty()) {
                                    // Add standalone setters after the chain setters
                                    for (J.MethodInvocation setter : standaloneSetters) {
                                        SetterToBuilderMapping m = SetterToBuilderMapping.fromSetter(setter.getName().getSimpleName());
                                        setterCalls.add(setter);
                                        mappings.add(m);
                                    }

                                    // Mark standalone setters for removal
                                    Cursor blockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
                                    Set<UUID> toRemove = blockCursor.getMessage(INVOCATIONS_TO_REMOVE);
                                    if (toRemove == null) {
                                        toRemove = new HashSet<>();
                                        blockCursor.putMessage(INVOCATIONS_TO_REMOVE, toRemove);
                                    }
                                    for (J.MethodInvocation setter : standaloneSetters) {
                                        toRemove.add(setter.getId());
                                    }

                                    doAfterVisit(inlineWrappedVariable());
                                    doAfterVisit(removeEmptyInitBlocks());
                                }
                            }
                        }

                        return applyBuilderTemplate(mapperFqn, builderEntry, setterCalls, mappings, suffixCalls,
                                mi.getCoordinates().replace(), ctx);
                    }

                    /**
                     * Determine variable identifier from local declaration or field assignment
                     * enclosing the current cursor position.
                     */
                    private J.@Nullable Identifier findVariableIdentifier() {
                        J.VariableDeclarations.NamedVariable namedVar = getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
                        if (namedVar != null) {
                            return namedVar.getName();
                        }
                        J.Assignment assignment = getCursor().firstEnclosing(J.Assignment.class);
                        if (assignment != null && assignment.getVariable() instanceof J.Identifier) {
                            return (J.Identifier) assignment.getVariable();
                        }
                        return null;
                    }

                    /**
                     * Collect standalone setter calls on a variable that appear after the variable's
                     * declaration/assignment. Stops collecting when the variable is referenced in a
                     * non-setter context (e.g., passed to another method). Both known and unknown
                     * setters are collected; unknown setters will get TODO comments in the builder chain.
                     */
                    private List<J.MethodInvocation> collectStandaloneSetters(
                            J.Block block, J.Identifier varIdent, Set<J.Identifier> intermediateVars) {
                        List<J.MethodInvocation> setters = new ArrayList<>();
                        boolean pastDeclaration = false;
                        boolean collecting = true;

                        for (Statement stmt : block.getStatements()) {
                            if (!collecting) {
                                break;
                            }

                            // Skip until we're past the declaration/assignment
                            if (!pastDeclaration) {
                                J.VariableDeclarations vd = extractVariableDeclarations(stmt);
                                if (vd != null && vd.getVariables().stream()
                                        .anyMatch(v -> SemanticallyEqual.areEqual(v.getName(), varIdent))) {
                                    pastDeclaration = true;
                                    continue;
                                }
                                if (stmt instanceof J.Assignment) {
                                    J.Assignment a = (J.Assignment) stmt;
                                    if (a.getVariable() instanceof J.Identifier &&
                                            SemanticallyEqual.areEqual(a.getVariable(), varIdent)) {
                                        pastDeclaration = true;
                                        continue;
                                    }
                                }
                                continue;
                            }

                            // Track intermediate variable declarations
                            J.VariableDeclarations vd = extractVariableDeclarations(stmt);
                            if (vd != null) {
                                for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                                    intermediateVars.add(v.getName());
                                }
                            }

                            // Look inside init blocks for setter calls (must be checked BEFORE
                            // extractMethodInvocation, which would visit into the block and find
                            // only the first MI)
                            if (stmt instanceof J.Block) {
                                for (Statement innerStmt : ((J.Block) stmt).getStatements()) {
                                    if (!collecting) {
                                        break;
                                    }
                                    J.MethodInvocation initMi = extractMethodInvocation(innerStmt);
                                    if (initMi != null && isCallOnVariable(initMi, varIdent)) {
                                        if (argumentReferencesAny(initMi, intermediateVars)) {
                                            collecting = false;
                                            continue;
                                        }
                                        if (SetterToBuilderMapping.fromSetter(initMi.getName().getSimpleName()) == null &&
                                                !isSetterReturnType(initMi)) {
                                            collecting = false;
                                            continue;
                                        }
                                        setters.add(initMi);
                                        continue;
                                    }
                                    if (referencesVariable(innerStmt, varIdent)) {
                                        collecting = false;
                                    }
                                }
                                continue;
                            }

                            // Check if statement is a setter call on the variable
                            J.MethodInvocation mi = extractMethodInvocation(stmt);
                            if (mi != null && isCallOnVariable(mi, varIdent)) {
                                if (argumentReferencesAny(mi, intermediateVars)) {
                                    collecting = false;
                                    continue;
                                }
                                if (SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName()) == null &&
                                        !isSetterReturnType(mi)) {
                                    collecting = false;
                                    continue;
                                }
                                setters.add(mi);
                                continue;
                            }

                            if (referencesVariable(stmt, varIdent)) {
                                collecting = false;
                            }
                        }

                        return setters;
                    }

                    /**
                     * Builds and applies the {@code Mapper.builder()...build()} template for a list of setter calls.
                     */
                    private J applyBuilderTemplate(String mapperFqn, @Nullable String builderEntry,
                                                   List<J.MethodInvocation> setters,
                                                   @Nullable List<SetterToBuilderMapping> resolvedMappings,
                                                   List<J.MethodInvocation> suffixCalls,
                                                   JavaCoordinates coordinates, ExecutionContext ctx) {
                        String simpleMapperName = mapperFqn.substring(mapperFqn.lastIndexOf('.') + 1);
                        String entryExpr = builderEntry != null ? builderEntry : (simpleMapperName + ".builder()");
                        StringBuilder templateCode = new StringBuilder(entryExpr);
                        List<Expression> templateArgs = new ArrayList<>();
                        List<J.MethodInvocation> unknownSetters = new ArrayList<>();
                        for (int i = 0; i < setters.size(); i++) {
                            J.MethodInvocation setter = setters.get(i);
                            SetterToBuilderMapping mapping = resolvedMappings != null ?
                                    resolvedMappings.get(i) :
                                    SetterToBuilderMapping.fromSetter(setter.getName().getSimpleName());
                            if (mapping != null) {
                                appendBuilderCall(setter, mapping, templateCode, templateArgs);
                            } else {
                                unknownSetters.add(setter);
                                String methodName = setter.getName().getSimpleName();
                                appendComments(setter.getPrefix().getComments(), templateCode);
                                templateCode.append(String.format("\n // TODO %s was removed from %s in Jackson 3.", methodName, simpleMapperName));
                                templateCode.append("\n.").append(methodName).append("(");
                                boolean first = true;
                                for (Expression arg : setter.getArguments()) {
                                    if (arg instanceof J.Empty) {
                                        continue;
                                    }
                                    if (!first) {
                                        templateCode.append(", ");
                                    }
                                    first = false;
                                    templateCode.append("#{any()}");
                                    templateArgs.add(arg);
                                }
                                templateCode.append(")");
                            }
                        }
                        templateCode.append("\n.build()");

                        boolean useKotlinFactory = builderEntry != null &&
                                builderEntry.startsWith(JACKSON_MAPPER_BUILDER_NAME);
                        if (useKotlinFactory) {
                            // Kotlin top-level extension functions are imported by their bare name
                            // (e.g. `import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder`),
                            // not as a static member of the synthetic `ExtensionsKt` class.
                            maybeRemoveImport(KOTLIN_EXTENSIONS_PACKAGE + "." + JACKSON_OBJECT_MAPPER_NAME);
                            maybeAddImport(KOTLIN_EXTENSIONS_PACKAGE + "." + JACKSON_MAPPER_BUILDER_NAME, false);
                        } else {
                            maybeAddImport(mapperFqn);
                        }
                        maybeAddImport(JSON_INCLUDE);

                        JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "jackson-annotations-2", "jackson-core-2", "jackson-databind-2")
                                .dependsOn(mapperStub(mapperFqn, unknownSetters));
                        if (useKotlinFactory) {
                            parser = parser.dependsOn(kotlinExtensionsStub());
                        }

                        JavaTemplate.Builder templateBuilder = JavaTemplate.builder(templateCode.toString())
                                .imports(mapperFqn, JSON_INCLUDE)
                                .javaParser(parser);
                        if (useKotlinFactory) {
                            templateBuilder = templateBuilder.staticImports(JACKSON_MAPPER_BUILDER_FQN);
                        }
                        J built = templateBuilder
                                .build()
                                .apply(getCursor(), coordinates, templateArgs.toArray());

                        // Reattach non-setter calls that followed the known setters by swapping
                        // each suffix's select to the previous result. Keeping the original
                        // J.MethodInvocation nodes preserves formatting details the JavaTemplate
                        // cannot round-trip (e.g. Kotlin trailing-lambda syntax on `.apply { }`).
                        // Strip the template result's prefix: the outermost suffix already owns
                        // the outer whitespace (it was the outermost MI before rewrite), so
                        // keeping the prefix on `built` would double the leading space.
                        if (suffixCalls.isEmpty() || !(built instanceof Expression)) {
                            return built;
                        }
                        Expression chained = ((Expression) built).withPrefix(Space.EMPTY);
                        for (J.MethodInvocation suffix : suffixCalls) {
                            J.MethodInvocation annotated = isApplyBlock(suffix) ?
                                    addApplyTodoComment(suffix, simpleMapperName) : suffix;
                            chained = annotated.withSelect(chained);
                        }
                        return chained;
                    }
                }
        );
    }

    /**
     * Returns the FQN of the mapper type matched by the given new class, or null if none match.
     */
    private static @Nullable String matchingMapperFqn(J.NewClass nc) {
        for (Map.Entry<MethodMatcher, String> entry : MAPPER_CTORS.entrySet()) {
            if (entry.getKey().matches(nc)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the FQN of the mapper type that the given type is assignable to, or null if none match.
     */
    private static @Nullable String matchingMapperType(@Nullable JavaType type) {
        if (type == null) {
            return null;
        }
        for (String mapper : ALL_MAPPERS) {
            if (TypeUtils.isAssignableTo(mapper, type)) {
                return mapper;
            }
        }
        return null;
    }

    /**
     * Walk the select chain of a method invocation to find a fluent chain rooted at either
     * a format-aligned mapper constructor (e.g. {@code new JsonMapper()}) or a known factory
     * call (e.g. the Kotlin {@code jacksonObjectMapper()} extension function). Returns the
     * chain calls in chain order (excluding the root) and sets {@code mapperFqnHolder[0]} to
     * the matched mapper FQN; sets {@code mapperFqnHolder[1]} to a non-null builder entry
     * expression (e.g. {@code "jacksonMapperBuilder()"}) when the rewritten output should use
     * a different builder entry than {@code SimpleName.builder()}. Returns {@code null} if
     * the chain is not rooted at a recognized mapper.
     */
    private static @Nullable List<J.MethodInvocation> collectFluentChain(J.MethodInvocation mi, String[] mapperFqnHolder,
                                                                          boolean importsJacksonObjectMapper) {
        List<J.MethodInvocation> calls = new ArrayList<>();
        Expression current = mi;
        while (current instanceof J.MethodInvocation) {
            calls.add((J.MethodInvocation) current);
            current = ((J.MethodInvocation) current).getSelect();
        }
        if (current instanceof J.NewClass) {
            String mapperFqn = matchingMapperFqn((J.NewClass) current);
            if (mapperFqn != null) {
                mapperFqnHolder[0] = mapperFqn;
                reverse(calls);
                return calls;
            }
        }
        // Kotlin: chains rooted at the top-level extension function jacksonObjectMapper().
        // The factory call itself is the last element added to `calls` (it has no select).
        if (current == null && !calls.isEmpty()) {
            J.MethodInvocation rootCandidate = calls.get(calls.size() - 1);
            if (isJacksonObjectMapperFactory(rootCandidate, importsJacksonObjectMapper)) {
                calls.remove(calls.size() - 1);
                mapperFqnHolder[0] = JSON_MAPPER;
                mapperFqnHolder[1] = JACKSON_MAPPER_BUILDER_NAME + "()";
                reverse(calls);
                return calls;
            }
        }
        return null;
    }

    /**
     * Detect a top-level call to {@code com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()}.
     * Uses {@link MethodMatcher} when the call has resolved type info. When type attribution is
     * partial (e.g. the parser is using a type table without the backing jar), falls back to a
     * structural check: no select, no args, simple name matches, and the enclosing compilation
     * unit imports the function.
     */
    private static boolean isJacksonObjectMapperFactory(J.MethodInvocation mi, boolean importsJacksonObjectMapper) {
        if (JACKSON_OBJECT_MAPPER_MATCHER.matches(mi)) {
            return true;
        }
        if (mi.getSelect() != null || !mi.getArguments().isEmpty() && !(mi.getArguments().get(0) instanceof J.Empty)) {
            return false;
        }
        if (!JACKSON_OBJECT_MAPPER_NAME.equals(mi.getName().getSimpleName())) {
            return false;
        }
        JavaType.Method methodType = mi.getMethodType();
        if (methodType != null && methodType.getDeclaringType() != null &&
                KOTLIN_EXTENSIONS_FQN.equals(methodType.getDeclaringType().getFullyQualifiedName())) {
            return true;
        }
        return importsJacksonObjectMapper;
    }

    /**
     * Stub for {@code com.fasterxml.jackson.module.kotlin.ExtensionsKt} so the JavaTemplate
     * parser can resolve {@code jacksonMapperBuilder()} as a static method import. The Kotlin
     * call site renders without the {@code ExtensionsKt} qualifier; the import is rewritten
     * separately via {@link #maybeAddImport(String, boolean)}.
     */
    private static String kotlinExtensionsStub() {
        return "package com.fasterxml.jackson.module.kotlin;\n" +
                "public class ExtensionsKt {\n" +
                "    public static com.fasterxml.jackson.databind.json.JsonMapper.Builder jacksonMapperBuilder() { return null; }\n" +
                "}\n";
    }

    /**
     * Generates a stub class for a mapper with an inner {@code Builder} class, so the
     * JavaTemplate parser can resolve the builder pattern. Always provides a full Builder
     * with all known methods declared explicitly plus any unknown methods passed in.
     */
    private static String mapperStub(String mapperFqn, List<J.MethodInvocation> unknownSetters) {
        int lastDot = mapperFqn.lastIndexOf('.');
        String packageName = mapperFqn.substring(0, lastDot);
        String simpleName = mapperFqn.substring(lastDot + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n");
        sb.append("public class ").append(simpleName)
                .append(" extends com.fasterxml.jackson.databind.ObjectMapper {\n");
        sb.append("    public ").append(simpleName).append("() {}\n");
        sb.append("    public static Builder builder() { return null; }\n");
        sb.append("    public static class Builder extends ")
                .append("com.fasterxml.jackson.databind.cfg.MapperBuilder<")
                .append(simpleName).append(", Builder> {\n");
        sb.append("        protected Builder() { super(); }\n");

        // Common mapped builder methods
        sb.append("        public Builder enable(Object... f) { return this; }\n");
        sb.append("        public Builder disable(Object... f) { return this; }\n");
        sb.append("        public Builder configure(Object f, boolean state) { return this; }\n");
        sb.append("        public Builder addModule(com.fasterxml.jackson.databind.Module m) { return this; }\n");
        sb.append("        public Builder addModules(com.fasterxml.jackson.databind.Module... m) { return this; }\n");
        sb.append("        public Builder findAndAddModules() { return this; }\n");
        sb.append("        public Builder addMixIn(Class<?> target, Class<?> mixin) { return this; }\n");
        sb.append("        public Builder registerSubtypes(Object... subtypes) { return this; }\n");
        sb.append("        public Builder addHandler(Object h) { return this; }\n");
        sb.append("        public Builder clearProblemHandlers() { return this; }\n");
        sb.append("        public Builder activateDefaultTyping(Object... v) { return this; }\n");
        sb.append("        public Builder activateDefaultTypingAsProperty(Object... v) { return this; }\n");
        sb.append("        public Builder deactivateDefaultTyping() { return this; }\n");
        sb.append("        public Builder setDefaultTyping(Object t) { return this; }\n");
        sb.append("        public Builder filterProvider(Object fp) { return this; }\n");
        sb.append("        public Builder serializerFactory(Object sf) { return this; }\n");
        sb.append("        public Builder defaultPrettyPrinter(Object pp) { return this; }\n");
        sb.append("        public Builder injectableValues(Object iv) { return this; }\n");
        sb.append("        public Builder nodeFactory(Object nf) { return this; }\n");
        sb.append("        public Builder constructorDetector(Object cd) { return this; }\n");
        sb.append("        public Builder cacheProvider(Object cp) { return this; }\n");
        sb.append("        public Builder annotationIntrospector(Object ai) { return this; }\n");
        sb.append("        public Builder typeFactory(Object tf) { return this; }\n");
        sb.append("        public Builder subtypeResolver(Object sr) { return this; }\n");
        sb.append("        public Builder visibility(Object... v) { return this; }\n");
        sb.append("        public Builder handlerInstantiator(Object hi) { return this; }\n");
        sb.append("        public Builder propertyNamingStrategy(Object s) { return this; }\n");
        sb.append("        public Builder enumNamingStrategy(Object s) { return this; }\n");
        sb.append("        public Builder accessorNaming(Object p) { return this; }\n");
        sb.append("        public Builder polymorphicTypeValidator(Object v) { return this; }\n");
        sb.append("        public Builder defaultDateFormat(Object df) { return this; }\n");
        sb.append("        public Builder defaultTimeZone(java.util.TimeZone tz) { return this; }\n");
        sb.append("        public Builder defaultLocale(java.util.Locale l) { return this; }\n");
        sb.append("        public Builder defaultBase64Variant(Object v) { return this; }\n");
        sb.append("        public Builder defaultAttributes(Object a) { return this; }\n");
        sb.append("        public Builder defaultPropertyInclusion(Object v) { return this; }\n");
        sb.append("        public Builder serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include incl) { return this; }\n");
        sb.append("        public Builder defaultSetterInfo(Object v) { return this; }\n");
        sb.append("        public Builder defaultMergeable(Object m) { return this; }\n");
        sb.append("        public Builder defaultLeniency(Object l) { return this; }\n");

        // Stubs for unknown methods so the template compiles
        Set<String> addedMethods = new HashSet<>();
        for (J.MethodInvocation mi : unknownSetters) {
            String name = mi.getName().getSimpleName();
            if (addedMethods.add(name)) {
                List<Expression> callArgs = new ArrayList<>();
                for (Expression arg : mi.getArguments()) {
                    if (!(arg instanceof J.Empty)) {
                        callArgs.add(arg);
                    }
                }
                sb.append("        public Builder ").append(name).append("(");
                for (int i = 0; i < callArgs.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(stubTypeName(callArgs.get(i))).append(" arg").append(i);
                }
                sb.append(") { return this; }\n");
            }
        }

        sb.append("        public ").append(simpleName).append(" build() { return null; }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String stubTypeName(Expression arg) {
        JavaType type = arg.getType();
        if (type instanceof JavaType.Primitive) {
            JavaType.Primitive p = (JavaType.Primitive) type;
            if (p == JavaType.Primitive.Null || p == JavaType.Primitive.None) {
                return "Object";
            }
            return p.getKeyword();
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq != null) {
            return fq.getFullyQualifiedName();
        }
        return "Object";
    }

    /**
     * True if {@code call} is a Kotlin scope function {@code .apply { ... }} invocation
     * (single-argument lambda). Does not inspect the body — used to decide whether a
     * preserved suffix call warrants a semantic-shift TODO comment.
     */
    private static boolean isApplyBlock(J.MethodInvocation call) {
        return "apply".equals(call.getName().getSimpleName()) &&
                call.getArguments().size() == 1 &&
                call.getArguments().get(0) instanceof J.Lambda;
    }

    /**
     * Attach a TODO comment before a preserved {@code .apply { ... }} suffix call. In
     * Jackson 2 the block ran on the mutable mapper mid-chain, so setter calls inside
     * took effect. In Jackson 3 the same block runs on the built, immutable mapper —
     * any mutator calls inside will throw at runtime. The comment warns the reader to
     * move setter calls into the builder chain.
     * <p>
     * The comment is placed in the whitespace after the select (i.e. between the
     * previous call and the {@code .}) so it renders on its own line directly above
     * {@code .apply} rather than bubbling up to the start of the whole expression.
     */
    private static J.MethodInvocation addApplyTodoComment(J.MethodInvocation apply, String simpleMapperName) {
        String commentText = String.format(
                " TODO This .apply {} now runs on the immutable %s returned by .build(); " +
                        "any setter calls inside it will fail at runtime. Move them into the builder chain above.",
                simpleMapperName);
        JRightPadded<Expression> selectPad = apply.getPadding().getSelect();
        if (selectPad == null) {
            return apply;
        }
        Space after = selectPad.getAfter();
        for (Comment existing : after.getComments()) {
            if (existing instanceof TextComment &&
                    ((TextComment) existing).getText().trim().equals(commentText.trim())) {
                return apply;
            }
        }
        TextComment comment = new TextComment(false, commentText, after.getWhitespace(), Markers.EMPTY);
        Space newAfter = after.withComments(ListUtils.concat(after.getComments(), comment));
        return apply.getPadding().withSelect(selectPad.withAfter(newAfter));
    }

    /**
     * If {@code call} is a Kotlin scope function {@code .apply { ... }} whose body consists
     * entirely of simple expression statements that invoke setters mapped by
     * {@link SetterToBuilderMapping}, return those inner invocations in body order so they
     * can be folded into the builder chain. Returns {@code null} if the block contains
     * anything else (variable declarations, control flow, calls on something other than
     * the implicit receiver, unknown setters) — in which case the {@code .apply(...)}
     * call should be left alone.
     */
    private static @Nullable List<J.MethodInvocation> tryUnwrapApplyBlock(J.MethodInvocation call) {
        if (!"apply".equals(call.getName().getSimpleName()) ||
                call.getArguments().size() != 1 ||
                !(call.getArguments().get(0) instanceof J.Lambda)) {
            return null;
        }
        J.Lambda lambda = (J.Lambda) call.getArguments().get(0);
        if (!(lambda.getBody() instanceof J.Block)) {
            return null;
        }
        J.Block body = (J.Block) lambda.getBody();
        if (body.getStatements().isEmpty()) {
            return null;
        }
        List<J.MethodInvocation> result = new ArrayList<>();
        for (Statement stmt : body.getStatements()) {
            if (!isSimpleExpressionStatement(stmt)) {
                return null;
            }
            J.MethodInvocation mi = extractMethodInvocation(stmt);
            if (mi == null) {
                return null;
            }
            if (!isImplicitOrThisSelect(mi.getSelect())) {
                return null;
            }
            if (SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName()) == null) {
                return null;
            }
            result.add(mi);
        }
        return result;
    }

    /**
     * True if {@code select} is either {@code null} (implicit receiver) or a reference
     * to {@code this}. Accepts both Java's {@code J.Identifier} named {@code "this"}
     * and Kotlin's {@code K.This} (matched by class name to avoid a direct dependency
     * on {@code rewrite-kotlin}).
     */
    private static boolean isImplicitOrThisSelect(@Nullable Expression select) {
        if (select == null) {
            return true;
        }
        if (select instanceof J.Identifier && "this".equals(((J.Identifier) select).getSimpleName())) {
            return true;
        }
        Class<?> c = select.getClass();
        return "This".equals(c.getSimpleName()) && c.getName().startsWith("org.openrewrite.kotlin.");
    }

    /**
     * True if {@code stmt} is a Java method-invocation statement or a Kotlin
     * {@code K.ExpressionStatement} wrapping one. Used to reject variable declarations
     * and control-flow statements inside lambda bodies we would otherwise unwrap.
     * Matches {@code K.ExpressionStatement} by class name so {@code rewrite-kotlin}
     * stays a runtime-only dependency.
     */
    private static boolean isSimpleExpressionStatement(Statement stmt) {
        if (stmt instanceof J.MethodInvocation) {
            return true;
        }
        Class<?> c = stmt.getClass();
        return "ExpressionStatement".equals(c.getSimpleName()) &&
                c.getName().startsWith("org.openrewrite.kotlin.");
    }

    /**
     * Extract {@link J.VariableDeclarations} from a statement, handling both Java
     * (where the statement IS a {@code J.VariableDeclarations}) and Kotlin
     * (where {@code K.Property} wraps {@code J.VariableDeclarations}).
     */
    private static J.@Nullable VariableDeclarations extractVariableDeclarations(Statement stmt) {
        if (stmt instanceof J.VariableDeclarations) {
            return (J.VariableDeclarations) stmt;
        }
        J.VariableDeclarations[] found = new J.VariableDeclarations[1];
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, Integer i) {
                if (found[0] == null) {
                    found[0] = vd;
                }
                return vd;
            }
        }.visit(stmt, 0);
        return found[0];
    }

    /**
     * Extract {@link J.MethodInvocation} from a statement, handling both Java
     * (where the statement IS a {@code J.MethodInvocation}) and Kotlin
     * (where {@code K.ExpressionStatement} wraps {@code J.MethodInvocation}).
     */
    private static J.@Nullable MethodInvocation extractMethodInvocation(Statement stmt) {
        if (stmt instanceof J.MethodInvocation) {
            return (J.MethodInvocation) stmt;
        }
        J.MethodInvocation[] found = new J.MethodInvocation[1];
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, Integer i) {
                if (found[0] == null) {
                    found[0] = mi;
                }
                return mi;
            }
        }.visit(stmt, 0);
        return found[0];
    }

    /**
     * Inline a variable declaration into a following return statement, handling both
     * Java and Kotlin wrapper types ({@code K.Property}, {@code K.Return}).
     */
    private static JavaIsoVisitor<ExecutionContext> inlineWrappedVariable() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                List<Statement> stmts = b.getStatements();
                if (stmts.size() < 2) {
                    return b;
                }

                Statement secondLast = stmts.get(stmts.size() - 2);
                J.VariableDeclarations vd = extractVariableDeclarations(secondLast);
                if (vd == null || vd.getVariables().size() != 1) {
                    return b;
                }

                J.VariableDeclarations.NamedVariable nv = vd.getVariables().get(0);
                Expression init = nv.getInitializer();
                if (init == null) {
                    return b;
                }

                // Use a mini-visitor to modify the inner J.Return inside K.Return (or J.Return directly)
                Statement last = stmts.get(stmts.size() - 1);
                boolean[] inlined = {false};
                Space declPrefix = secondLast.getPrefix();
                Statement newLast = (Statement) new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitReturn(J.Return ret, ExecutionContext ctx) {
                        if (ret.getExpression() instanceof J.Identifier &&
                                ((J.Identifier) ret.getExpression()).getSimpleName().equals(nv.getName().getSimpleName()) &&
                                TypeUtils.isOfType(ret.getExpression().getType(), nv.getName().getType())) {
                            inlined[0] = true;
                            return ret
                                    .withExpression(init.withPrefix(ret.getExpression().getPrefix()))
                                    .withPrefix(declPrefix.withComments(
                                            ListUtils.concatAll(declPrefix.getComments(), ret.getComments())));
                        }
                        return ret;
                    }
                }.visitNonNull(last, ctx, getCursor());

                if (inlined[0]) {
                    return b.withStatements(ListUtils.map(stmts, (i, s) -> {
                        if (i == stmts.size() - 2) {
                            return null;
                        }
                        if (i == stmts.size() - 1) {
                            return newLast;
                        }
                        return s;
                    }));
                }
                return b;
            }
        };
    }

    /**
     * Remove empty non-static {@link J.Block} statements (e.g. Kotlin init blocks
     * that became empty after setter removal).
     */
    private static JavaIsoVisitor<ExecutionContext> removeEmptyInitBlocks() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                if (b.getStatements().stream().anyMatch(s ->
                        s instanceof J.Block && ((J.Block) s).getStatements().isEmpty())) {
                    b = b.withStatements(ListUtils.map(b.getStatements(), s -> {
                        if (s instanceof J.Block && ((J.Block) s).getStatements().isEmpty()) {
                            return null;
                        }
                        return s;
                    }));
                }
                return b;
            }
        };
    }

    /**
     * Appends a single builder method call to the template string.
     */
    private static void appendBuilderCall(J.MethodInvocation mi, SetterToBuilderMapping mapping,
                                           StringBuilder templateCode, List<Expression> templateArgs) {
        String builderName = mapping.builderName;
        // Jackson 2 MapperBuilder does not have defaultPropertyInclusion(Include), only
        // serializationInclusion(Include). Use serializationInclusion here so the template
        // resolves against the Jackson 2 classpath; UpdateSerializationInclusionConfiguration
        // (running after) converts it to changeDefaultPropertyInclusion.
        if (mapping == SetterToBuilderMapping.SET_DEFAULT_PROPERTY_INCLUSION &&
                mi.getArguments().size() == 1 &&
                !(mi.getArguments().get(0) instanceof J.Empty) &&
                TypeUtils.isAssignableTo("com.fasterxml.jackson.annotation.JsonInclude$Include",
                        mi.getArguments().get(0).getType())) {
            builderName = "serializationInclusion";
        }
        appendComments(mi.getPrefix().getComments(), templateCode);
        appendComments(mi.getName().getPrefix().getComments(), templateCode);
        if (mi.getPadding().getSelect() != null) {
            appendComments(mi.getPadding().getSelect().getAfter().getComments(), templateCode);
        }
        templateCode.append("\n.").append(builderName).append("(");
        boolean first = true;
        for (Expression arg : mi.getArguments()) {
            if (arg instanceof J.Empty) {
                continue;
            }
            if (!first) {
                templateCode.append(", ");
            }
            first = false;
            templateCode.append("#{any()}");
            templateArgs.add(arg);
        }
        templateCode.append(")");
    }

    private static void appendComments(List<Comment> comments, StringBuilder templateCode) {
        for (Comment comment : comments) {
            if (comment instanceof TextComment) {
                TextComment tc = (TextComment) comment;
                if (tc.isMultiline()) {
                    templateCode.append("\n/*").append(tc.getText()).append("*/");
                } else {
                    // Leading space before // so the auto-formatter indents the comment line
                    templateCode.append("\n //").append(tc.getText());
                }
            }
        }
    }
}
