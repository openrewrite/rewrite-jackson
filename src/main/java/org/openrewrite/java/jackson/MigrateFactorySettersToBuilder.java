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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;
import static java.util.Collections.singleton;

@Getter
public class MigrateFactorySettersToBuilder extends Recipe {

    private static final String JSON_FACTORY = "com.fasterxml.jackson.core.JsonFactory";

    private static final List<String> ALL_FACTORIES = Arrays.asList(
            JSON_FACTORY,
            "com.fasterxml.jackson.dataformat.avro.AvroFactory",
            "com.fasterxml.jackson.dataformat.cbor.CBORFactory",
            "com.fasterxml.jackson.dataformat.csv.CsvFactory",
            "com.fasterxml.jackson.dataformat.smile.SmileFactory",
            "com.fasterxml.jackson.dataformat.xml.XmlFactory",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactory"
    );
    // IonFactory deliberately omitted: it has no plain `IonFactory.builder()` static — it
    // exposes `builderForBinaryWriters()` / `builderForTextualWriters()` instead, and choosing
    // between them isn't safe to automate.

    private static final List<String> CORE_PARSER_CLASSPATH = Arrays.asList(
            "jackson-annotations-2", "jackson-core-2", "jackson-databind-2");

    // Per-factory dataformat artifact to add to the template parser's classpath alongside
    // jackson-core/databind/annotations so its `new <Format>FactoryBuilder()` reference resolves.
    private static final Map<String, String> FACTORY_FQN_TO_PARSER_RESOURCE = new HashMap<>();

    static {
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.avro.AvroFactory", "jackson-dataformat-avro-2");
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.cbor.CBORFactory", "jackson-dataformat-cbor-2");
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.csv.CsvFactory", "jackson-dataformat-csv-2");
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.smile.SmileFactory", "jackson-dataformat-smile-2");
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.xml.XmlFactory", "jackson-dataformat-xml-2");
        FACTORY_FQN_TO_PARSER_RESOURCE.put("com.fasterxml.jackson.dataformat.yaml.YAMLFactory", "jackson-dataformat-yaml-2");
    }

    private static String[] parserClasspathFor(String factoryFqn) {
        String extra = FACTORY_FQN_TO_PARSER_RESOURCE.get(factoryFqn);
        List<String> classpath = extra == null ? CORE_PARSER_CLASSPATH : ListUtils.concat(CORE_PARSER_CLASSPATH, extra);
        return classpath.toArray(new String[0]);
    }

    private static final String INVOCATIONS_TO_REMOVE = "INVOCATIONS_TO_REMOVE";

    @RequiredArgsConstructor
    enum SetterToBuilderMapping {
        // Feature enable/disable/configure. UpgradeJackson_2_3_ModernizeJacksonCoreFeatures
        // runs before this recipe, so the argument constant is always one of: JsonReadFeature,
        // JsonWriteFeature, StreamReadFeature, StreamWriteFeature, or JsonFactory.Feature.
        // With a concrete receiver (either `new JsonFactoryBuilder()` for JsonFactory or
        // `XFactory.builder()` for the format-aligned factories), JsonReadFeature /
        // JsonWriteFeature args resolve through concrete overrides on JsonFactoryBuilder
        // and the others resolve through methods inherited from TSFBuilder<F, B> with F/B
        // already bound to the concrete factory/builder pair.
        CONFIGURE("configure", "configure", false),
        DISABLE("disable", "disable", false),
        ENABLE("enable", "enable", false),

        // Concrete on JsonFactoryBuilder only — the format-aligned builders extend
        // TSFBuilder<F, B> directly, not JsonFactoryBuilder, and don't expose these.
        // For non-Json factories the setter falls through to the TODO-comment path.
        SET_CHARACTER_ESCAPES("setCharacterEscapes", "characterEscapes", true),
        SET_ROOT_VALUE_SEPARATOR("setRootValueSeparator", "rootValueSeparator", true),

        // Stream-decoration — inherited from TSFBuilder<F, B>.
        SET_INPUT_DECORATOR("setInputDecorator", "inputDecorator", false),
        SET_OUTPUT_DECORATOR("setOutputDecorator", "outputDecorator", false),

        // Read/write constraints — inherited from TSFBuilder<F, B>.
        SET_STREAM_READ_CONSTRAINTS("setStreamReadConstraints", "streamReadConstraints", false),
        SET_STREAM_WRITE_CONSTRAINTS("setStreamWriteConstraints", "streamWriteConstraints", false);

        final String setterName;
        final String builderName;
        final boolean jsonFactoryOnly;

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

        static @Nullable SetterToBuilderMapping fromSetter(String name, String factoryFqn) {
            SetterToBuilderMapping m = BY_SETTER_NAME.get(name);
            if (m == null || (m.jsonFactoryOnly && !JSON_FACTORY.equals(factoryFqn))) {
                return null;
            }
            return m;
        }
    }

    final String displayName = "Migrate factory setter calls to builder pattern";
    final String description = "In Jackson 3, `JsonFactory` is immutable and `new JsonFactory()` is no longer the right " +
            "entry point: the concrete factory lives at `tools.jackson.core.json.JsonFactory` and is constructed via " +
            "`JsonFactory.builder()...build()`. Configuration methods like `enable`, `disable`, `configure`, " +
            "`setCharacterEscapes`, etc. must be called on the builder instead. This recipe migrates setter calls to " +
            "the builder pattern when safe, or adds TODO comments when automatic migration is not possible.";
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext>[] preconditions = ALL_FACTORIES.stream()
                .map(f -> new UsesType<>(f, false))
                .toArray(TreeVisitor[]::new);
        return Preconditions.check(
                Preconditions.or(preconditions),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);

                        String factoryFqn = matchingFactoryFqn(nc);
                        if (factoryFqn == null) {
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
                            // Bare `new JsonFactory()` with no surrounding setters still needs builder() form
                            return applyBuilderTemplate(factoryFqn, emptyList(), emptyList(), emptyList(),
                                    nc.getCoordinates().replace(), ctx);
                        }

                        J.Block block = getCursor().firstEnclosing(J.Block.class);
                        if (block == null) {
                            return nc;
                        }

                        // Collect known setter calls that appear before any unknown factory
                        // usage (unknown call on the variable, or variable passed elsewhere).
                        List<J.MethodInvocation> builderSetters = new ArrayList<>();
                        List<SetterToBuilderMapping> builderMappings = new ArrayList<>();
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
                                    SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName(), factoryFqn);
                                    if (mapping != null) {
                                        // Can't fold if arguments reference variables declared after the constructor
                                        if (argumentReferencesAny(mi, intermediateVars)) {
                                            collecting = false;
                                            continue;
                                        }
                                        builderSetters.add(mi);
                                        builderMappings.add(mapping);
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
                            // Even with no setters, migrate `new JsonFactory()` to `JsonFactory.builder().build()`
                            return applyBuilderTemplate(factoryFqn, emptyList(), emptyList(), emptyList(),
                                    nc.getCoordinates().replace(), ctx);
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

                        return applyBuilderTemplate(factoryFqn, builderSetters, builderMappings, emptyList(),
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

                        // Check for fluent chain on new Factory()
                        if (isFluentChainHead(method)) {
                            J result = tryMigrateFluentChain(mi, ctx);
                            if (result != null) {
                                return result;
                            }
                        }

                        if (!(mi.getSelect() instanceof J.Identifier)) {
                            return mi;
                        }

                        String matchedFactory = matchingFactoryType(mi.getSelect().getType());
                        if (matchedFactory == null) {
                            return mi;
                        }

                        // Use the unrestricted lookup here so JsonFactoryBuilder-only setters
                        // (characterEscapes / rootValueSeparator) called on a format-aligned factory
                        // still get a TODO comment explaining the unsupported migration.
                        SetterToBuilderMapping mapping = SetterToBuilderMapping.fromSetter(mi.getName().getSimpleName());
                        if (mapping == null) {
                            return mi;
                        }

                        // Not eligible for builder migration - add a TODO comment
                        String simpleFactoryName = matchedFactory.substring(matchedFactory.lastIndexOf('.') + 1);
                        String commentText = String.format(
                                " TODO %s could not be folded to the builder of %s. " +
                                        "Use factory.rebuild().%s(...).build() or move to the factory's instantiation site.",
                                mapping.setterName, simpleFactoryName, mapping.builderName);

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
                     * at a factory constructor, migrate the chain to the builder pattern.
                     */
                    private @Nullable J tryMigrateFluentChain(J.MethodInvocation mi, ExecutionContext ctx) {
                        String[] factoryHolder = new String[1];
                        List<J.MethodInvocation> chainCalls = collectFluentChain(mi, factoryHolder);
                        if (chainCalls == null) {
                            return null;
                        }

                        String factoryFqn = factoryHolder[0];

                        // Split chain into known setters (prefix) and remaining calls (suffix)
                        List<J.MethodInvocation> setterCalls = new ArrayList<>();
                        List<SetterToBuilderMapping> mappings = new ArrayList<>();
                        List<J.MethodInvocation> suffixCalls = new ArrayList<>();
                        boolean hitUnknown = false;
                        for (J.MethodInvocation call : chainCalls) {
                            if (!hitUnknown) {
                                SetterToBuilderMapping m = SetterToBuilderMapping.fromSetter(call.getName().getSimpleName(), factoryFqn);
                                if (m != null) {
                                    setterCalls.add(call);
                                    mappings.add(m);
                                    continue;
                                }
                                hitUnknown = true;
                            }
                            suffixCalls.add(call);
                        }

                        return applyBuilderTemplate(factoryFqn, setterCalls, mappings, suffixCalls,
                                mi.getCoordinates().replace(), ctx);
                    }

                    /**
                     * Builds and applies the builder-chain template for a list of setter calls. The
                     * chain's receiver type is concrete from the start so feature-typed args resolve
                     * through concrete overrides and the rest resolve through inherited
                     * {@link com.fasterxml.jackson.core.TSFBuilder} methods with F/B already bound.
                     * <p>
                     * For {@link #JSON_FACTORY} we use {@code new JsonFactoryBuilder()} because
                     * {@code JsonFactory.builder()} returns the wildcard {@code TSFBuilder<?, ?>}.
                     * For the format-aligned factories we use {@code XFactory.builder()} — their
                     * static {@code builder()} returns a concretely-typed {@code XFactoryBuilder},
                     * and their no-arg {@code XFactoryBuilder} ctor is {@code protected}, so
                     * {@code new XFactoryBuilder()} would emit uncompilable code.
                     */
                    private J applyBuilderTemplate(String factoryFqn, List<J.MethodInvocation> setters,
                                                   List<SetterToBuilderMapping> resolvedMappings,
                                                   List<J.MethodInvocation> suffixCalls,
                                                   JavaCoordinates coordinates, ExecutionContext ctx) {
                        boolean useStaticBuilder = !JSON_FACTORY.equals(factoryFqn);
                        String simpleFactoryName = factoryFqn.substring(factoryFqn.lastIndexOf('.') + 1);
                        String chainEntry = useStaticBuilder ?
                                simpleFactoryName + ".builder()" :
                                "new " + simpleFactoryName + "Builder()";
                        StringBuilder templateCode = new StringBuilder(chainEntry);
                        List<Expression> templateArgs = new ArrayList<>();
                        for (int i = 0; i < setters.size(); i++) {
                            appendBuilderCall(setters.get(i), resolvedMappings.get(i), templateCode, templateArgs);
                        }
                        templateCode.append("\n.build()");

                        // Append any non-setter calls that follow the known setters
                        for (J.MethodInvocation suffix : suffixCalls) {
                            appendCall(suffix.getName().getSimpleName(), suffix.getArguments(), templateCode, templateArgs);
                        }

                        // JsonFactory branch: emits `new JsonFactoryBuilder()` so we add the
                        // builder import. Format-aligned branch: emits `XFactory.builder()` —
                        // the template snippet references `XFactory` qualifier-free, so the
                        // parser needs that FQN on its imports list to attribute the type. The
                        // user's source already imports XFactory (it appeared in `new XFactory()`),
                        // so we don't add a new import to the final compilation unit.
                        JavaTemplate.Builder templateBuilder = JavaTemplate.builder(templateCode.toString())
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, parserClasspathFor(factoryFqn)));
                        if (useStaticBuilder) {
                            templateBuilder = templateBuilder.imports(factoryFqn);
                        } else {
                            String factoryBuilderFqn = factoryFqn + "Builder";
                            maybeAddImport(factoryBuilderFqn);
                            templateBuilder = templateBuilder.imports(factoryBuilderFqn);
                        }
                        return templateBuilder.build()
                                .apply(getCursor(), coordinates, templateArgs.toArray());
                    }
                }
        );
    }

    /**
     * Returns the FQN of the factory type matched by the given new class, or null if none match.
     * Equivalent to the prior per-factory {@code MethodMatcher(fqn + " <constructor>()")} lookup
     * collapsed into a single exact-FQN check — constructors don't have override semantics, so an
     * exact-type comparison preserves the old behavior across all factories without needing an
     * 8-entry matcher map.
     */
    private static @Nullable String matchingFactoryFqn(J.NewClass nc) {
        if (nc.getArguments().stream().anyMatch(a -> !(a instanceof J.Empty))) {
            return null;
        }
        if (!(nc.getType() instanceof JavaType.FullyQualified)) {
            return null;
        }
        String fqn = ((JavaType.FullyQualified) nc.getType()).getFullyQualifiedName();
        return ALL_FACTORIES.contains(fqn) ? fqn : null;
    }

    /**
     * Returns the FQN of the factory type that the given type is assignable to, or null if none match.
     * Prefers an exact FQN match before falling back to an assignable match — the format-aligned
     * factories all extend {@link #JSON_FACTORY}, so a naive assignable scan in declaration order
     * would always report a {@code YAMLFactory} call site as {@code JsonFactory}.
     */
    private static @Nullable String matchingFactoryType(@Nullable JavaType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof JavaType.FullyQualified) {
            String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
            if (ALL_FACTORIES.contains(fqn)) {
                return fqn;
            }
        }
        for (String factory : ALL_FACTORIES) {
            if (TypeUtils.isAssignableTo(factory, type)) {
                return factory;
            }
        }
        return null;
    }

    /**
     * Walk the select chain of a method invocation to find a fluent chain rooted at
     * a factory constructor. Returns the chain calls in innermost-first order
     * and sets {@code factoryFqnHolder[0]} to the matched factory FQN,
     * or returns {@code null} if the chain is not rooted at a factory constructor.
     */
    private static @Nullable List<J.MethodInvocation> collectFluentChain(J.MethodInvocation mi, String[] factoryFqnHolder) {
        List<J.MethodInvocation> calls = new ArrayList<>();
        Expression current = mi;
        while (current instanceof J.MethodInvocation) {
            calls.add((J.MethodInvocation) current);
            current = ((J.MethodInvocation) current).getSelect();
        }
        if (current instanceof J.NewClass) {
            String factoryFqn = matchingFactoryFqn((J.NewClass) current);
            if (factoryFqn != null) {
                factoryFqnHolder[0] = factoryFqn;
                reverse(calls);
                return calls;
            }
        }
        return null;
    }

    /**
     * Appends a single builder method call to the template string. Arguments are passed
     * through unchanged via {@code #{any()}} substitution; constant renames (Jackson 2
     * legacy feature constants → modern {@code JsonReadFeature}/{@code JsonWriteFeature})
     * happen in a separate pipeline pass before this recipe runs.
     */
    private static void appendBuilderCall(J.MethodInvocation mi, SetterToBuilderMapping mapping,
                                          StringBuilder templateCode, List<Expression> templateArgs) {
        appendCall(mapping.builderName, mi.getArguments(), templateCode, templateArgs);
    }

    /**
     * Appends {@code \n.<methodName>(<args>)} to the template, emitting a {@code #{any()}}
     * placeholder for each non-empty argument and collecting the live argument expressions
     * into {@code templateArgs} for substitution.
     */
    private static void appendCall(String methodName, List<Expression> args,
                                   StringBuilder templateCode, List<Expression> templateArgs) {
        templateCode.append("\n.").append(methodName).append("(");
        boolean first = true;
        for (Expression arg : args) {
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
