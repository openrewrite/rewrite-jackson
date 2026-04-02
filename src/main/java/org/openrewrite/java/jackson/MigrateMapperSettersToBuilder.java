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
        TreeVisitor<?, ExecutionContext>[] preconditions = ALL_MAPPERS.stream()
                .map(m -> new UsesType<>(m, false))
                .toArray(TreeVisitor[]::new);
        return Preconditions.check(
                Preconditions.or(preconditions),
                new JavaVisitor<ExecutionContext>() {

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
                            // (handles both J.VariableDeclarations and Kotlin K.Property wrappers)
                            J.VariableDeclarations vd = extractVariableDeclarations(stmt);
                            if (vd != null) {
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

                            // Look inside init blocks for setter calls (must be checked BEFORE
                            // extractMethodInvocation, which would visit into the block and find
                            // only the first MI)
                            if (stmt instanceof J.Block) {
                                for (Statement innerStmt : ((J.Block) stmt).getStatements()) {
                                    if (!collecting) {
                                        break;
                                    }
                                    J.MethodInvocation initMi = extractMethodInvocation(innerStmt);
                                    if (initMi != null) {
                                        if (isCallOnVariable(initMi, varIdent)) {
                                            SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(initMi.getName().getSimpleName());
                                            if (mapping != null) {
                                                if (argumentReferencesAny(initMi, intermediateVars)) {
                                                    collecting = false;
                                                    continue;
                                                }
                                                builderSetters.add(initMi);
                                                continue;
                                            }
                                            collecting = false;
                                            continue;
                                        }
                                    }
                                    if (referencesVariable(innerStmt, varIdent)) {
                                        collecting = false;
                                    }
                                }
                                continue;
                            }

                            // Check if statement is a setter call (handles K.ExpressionStatement wrappers)
                            J.MethodInvocation mi = extractMethodInvocation(stmt);
                            if (mi != null) {
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

                        doAfterVisit(new InlineVariable().getVisitor());
                        // Kotlin-aware inlining: InlineVariable doesn't handle K.Property/K.Return wrappers
                        doAfterVisit(inlineWrappedVariable());
                        // Clean up empty init blocks after setter removal
                        doAfterVisit(removeEmptyInitBlocks());

                        return applyBuilderTemplate(mapperFqn, builderSetters, null, emptyList(),
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
                                " TODO %s was removed from %s in Jackson 3. " +
                                        "Use mapper.rebuild().%s(...).build() or move to the mapper's instantiation site. ",
                                mapping.setterName, simpleMapperName, mapping.builderName);

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
                        if (!(mi.getSelect() instanceof J.Identifier)) {
                            return false;
                        }
                        J.Identifier sel = (J.Identifier) mi.getSelect();
                        // Use name + type comparison instead of SemanticallyEqual, because Kotlin
                        // sets different fieldType owners for declarations vs usages of local variables
                        return sel.getSimpleName().equals(varIdent.getSimpleName()) &&
                                TypeUtils.isOfType(sel.getType(), varIdent.getType());
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
                     * If this method invocation is the outermost call of a fluent chain rooted
                     * at a format-aligned mapper constructor, migrate the chain to the builder pattern.
                     */
                    private @Nullable J tryMigrateFluentChain(J.MethodInvocation mi, ExecutionContext ctx) {
                        String[] mapperHolder = new String[1];
                        List<J.MethodInvocation> chainCalls = collectFluentChain(mi, mapperHolder);
                        if (chainCalls == null) {
                            return null;
                        }

                        String mapperFqn = mapperHolder[0];

                        // Split chain into known setters (prefix) and remaining calls (suffix)
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
                                hitUnknown = true;
                            }
                            suffixCalls.add(call);
                        }

                        // Need at least one known setter to migrate
                        if (setterCalls.isEmpty()) {
                            return null;
                        }

                        return applyBuilderTemplate(mapperFqn, setterCalls, mappings, suffixCalls,
                                mi.getCoordinates().replace(), ctx);
                    }

                    /**
                     * Builds and applies the {@code Mapper.builder()...build()} template for a list of setter calls.
                     */
                    private J applyBuilderTemplate(String mapperFqn, List<J.MethodInvocation> setters,
                                                   @Nullable List<SetterToBuilderMapping> resolvedMappings,
                                                   List<J.MethodInvocation> suffixCalls,
                                                   JavaCoordinates coordinates, ExecutionContext ctx) {
                        String simpleMapperName = mapperFqn.substring(mapperFqn.lastIndexOf('.') + 1);
                        StringBuilder templateCode = new StringBuilder(simpleMapperName + ".builder()");
                        List<Expression> templateArgs = new ArrayList<>();
                        for (int i = 0; i < setters.size(); i++) {
                            J.MethodInvocation setter = setters.get(i);
                            SetterToBuilderMapping mapping = resolvedMappings != null ?
                                    resolvedMappings.get(i) :
                                    SetterToBuilderMapping.fromSetter(setter.getName().getSimpleName());
                            assert mapping != null;
                            appendBuilderCall(setter, mapping, templateCode, templateArgs);
                        }
                        templateCode.append("\n.build()");

                        // Append any non-setter calls that follow the known setters
                        for (J.MethodInvocation suffix : suffixCalls) {
                            templateCode.append("\n.").append(suffix.getName().getSimpleName()).append("(");
                            boolean first = true;
                            for (Expression arg : suffix.getArguments()) {
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

                        maybeAddImport(mapperFqn);
                        maybeAddImport(JSON_INCLUDE);

                        JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "jackson-annotations-2", "jackson-core-2", "jackson-databind-2");
                        if (!JSON_MAPPER.equals(mapperFqn)) {
                            parser.dependsOn(mapperStub(mapperFqn));
                        }

                        return JavaTemplate.builder(templateCode.toString())
                                .imports(mapperFqn, JSON_INCLUDE)
                                .javaParser(parser)
                                .build()
                                .apply(getCursor(), coordinates, templateArgs.toArray());
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
     * Walk the select chain of a method invocation to find a fluent chain rooted at
     * a format-aligned mapper constructor. Returns the chain calls in innermost-first order
     * and sets {@code mapperFqnHolder[0]} to the matched mapper FQN,
     * or returns {@code null} if the chain is not rooted at a mapper constructor.
     */
    private static @Nullable List<J.MethodInvocation> collectFluentChain(J.MethodInvocation mi, String[] mapperFqnHolder) {
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
        return null;
    }

    /**
     * Generates a stub class for a format-aligned mapper, so the JavaTemplate parser can resolve
     * the builder pattern without requiring the actual dataformat jar on the classpath.
     */
    private static String mapperStub(String mapperFqn) {
        int lastDot = mapperFqn.lastIndexOf('.');
        String packageName = mapperFqn.substring(0, lastDot);
        String simpleName = mapperFqn.substring(lastDot + 1);
        return "package " + packageName + ";\n" +
                "public class " + simpleName + " extends com.fasterxml.jackson.databind.ObjectMapper {\n" +
                "    public " + simpleName + "() {}\n" +
                "    public static com.fasterxml.jackson.databind.json.JsonMapper.Builder builder() { return null; }\n" +
                "}\n";
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
     * Inline a variable declaration into a following return statement, handling Kotlin
     * wrapper types ({@code K.Property}, {@code K.Return}) that {@link InlineVariable} does not support.
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
