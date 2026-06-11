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
import org.openrewrite.java.*;
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

    private static final List<String> ALL_FACTORIES = Collections.singletonList(JSON_FACTORY);

    private static final Map<MethodMatcher, String> FACTORY_CTORS;

    static {
        FACTORY_CTORS = new LinkedHashMap<>();
        for (String factory : ALL_FACTORIES) {
            FACTORY_CTORS.put(new MethodMatcher(factory + " <constructor>()"), factory);
        }
    }

    private static final String INVOCATIONS_TO_REMOVE = "INVOCATIONS_TO_REMOVE";

    @RequiredArgsConstructor
    enum SetterToBuilderMapping {
        // Feature enable/disable/configure (covers all overloaded feature types)
        CONFIGURE("configure", "configure"),
        DISABLE("disable", "disable"),
        ENABLE("enable", "enable"),

        // Character escapes and value separators
        SET_CHARACTER_ESCAPES("setCharacterEscapes", "characterEscapes"),
        SET_ROOT_VALUE_SEPARATOR("setRootValueSeparator", "rootValueSeparator"),

        // Stream-decoration
        SET_INPUT_DECORATOR("setInputDecorator", "inputDecorator"),
        SET_OUTPUT_DECORATOR("setOutputDecorator", "outputDecorator"),

        // Read/write constraints
        SET_STREAM_READ_CONSTRAINTS("setStreamReadConstraints", "streamReadConstraints"),
        SET_STREAM_WRITE_CONSTRAINTS("setStreamWriteConstraints", "streamWriteConstraints");

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
                            return applyBuilderTemplate(factoryFqn, emptyList(), null, emptyList(),
                                    nc.getCoordinates().replace(), ctx);
                        }

                        J.Block block = getCursor().firstEnclosing(J.Block.class);
                        if (block == null) {
                            return nc;
                        }

                        // Collect known setter calls that appear before any unknown factory
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
                            // Even with no setters, migrate `new JsonFactory()` to `JsonFactory.builder().build()`
                            return applyBuilderTemplate(factoryFqn, emptyList(), null, emptyList(),
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

                        return applyBuilderTemplate(factoryFqn, builderSetters, null, emptyList(),
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

                        return applyBuilderTemplate(factoryFqn, setterCalls, mappings, suffixCalls,
                                mi.getCoordinates().replace(), ctx);
                    }

                    /**
                     * Builds and applies the {@code Factory.builder()...build()} template for a list of setter calls.
                     */
                    private J applyBuilderTemplate(String factoryFqn, List<J.MethodInvocation> setters,
                                                   @Nullable List<SetterToBuilderMapping> resolvedMappings,
                                                   List<J.MethodInvocation> suffixCalls,
                                                   JavaCoordinates coordinates, ExecutionContext ctx) {
                        String simpleFactoryName = factoryFqn.substring(factoryFqn.lastIndexOf('.') + 1);
                        StringBuilder templateCode = new StringBuilder(simpleFactoryName + ".builder()");
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

                        maybeAddImport(factoryFqn);

                        JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "jackson-annotations-2", "jackson-core-2", "jackson-databind-2")
                                .dependsOn(factoryStub(factoryFqn), factoryBuilderStub(factoryFqn));

                        return JavaTemplate.builder(templateCode.toString())
                                .imports(factoryFqn)
                                .javaParser(parser)
                                .build()
                                .apply(getCursor(), coordinates, templateArgs.toArray());
                    }
                }
        );
    }

    /**
     * Returns the FQN of the factory type matched by the given new class, or null if none match.
     */
    private static @Nullable String matchingFactoryFqn(J.NewClass nc) {
        for (Map.Entry<MethodMatcher, String> entry : FACTORY_CTORS.entrySet()) {
            if (entry.getKey().matches(nc)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the FQN of the factory type that the given type is assignable to, or null if none match.
     */
    private static @Nullable String matchingFactoryType(@Nullable JavaType type) {
        if (type == null) {
            return null;
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
     * Generates a stub for {@code JsonFactory} so the JavaTemplate parser can resolve
     * {@code JsonFactory.builder()} to a builder type with the relevant setter overloads.
     */
    private static String factoryStub(String factoryFqn) {
        int lastDot = factoryFqn.lastIndexOf('.');
        String packageName = factoryFqn.substring(0, lastDot);
        String simpleName = factoryFqn.substring(lastDot + 1);
        String builderSimple = simpleName + "Builder";
        return "package " + packageName + ";\n" +
                "public class " + simpleName + " {\n" +
                "    public " + simpleName + "() {}\n" +
                "    public " + simpleName + " enable(Object f) { return this; }\n" +
                "    public " + simpleName + " disable(Object f) { return this; }\n" +
                "    public " + simpleName + " configure(Object f, boolean state) { return this; }\n" +
                "    public " + simpleName + " setCharacterEscapes(Object e) { return this; }\n" +
                "    public " + simpleName + " setRootValueSeparator(Object s) { return this; }\n" +
                "    public " + simpleName + " setInputDecorator(Object d) { return this; }\n" +
                "    public " + simpleName + " setOutputDecorator(Object d) { return this; }\n" +
                "    public " + simpleName + " setStreamReadConstraints(Object c) { return this; }\n" +
                "    public " + simpleName + " setStreamWriteConstraints(Object c) { return this; }\n" +
                "    public static " + builderSimple + " builder() { return null; }\n" +
                "}\n";
    }

    /**
     * Generates a stub for the factory's top-level builder companion (e.g. {@code JsonFactoryBuilder}).
     */
    private static String factoryBuilderStub(String factoryFqn) {
        int lastDot = factoryFqn.lastIndexOf('.');
        String packageName = factoryFqn.substring(0, lastDot);
        String simpleName = factoryFqn.substring(lastDot + 1);
        String builderSimple = simpleName + "Builder";
        return "package " + packageName + ";\n" +
                "public class " + builderSimple + " {\n" +
                "    public " + builderSimple + "() {}\n" +
                "    public " + builderSimple + " enable(Object f) { return this; }\n" +
                "    public " + builderSimple + " disable(Object f) { return this; }\n" +
                "    public " + builderSimple + " configure(Object f, boolean state) { return this; }\n" +
                "    public " + builderSimple + " characterEscapes(Object e) { return this; }\n" +
                "    public " + builderSimple + " rootValueSeparator(Object s) { return this; }\n" +
                "    public " + builderSimple + " inputDecorator(Object d) { return this; }\n" +
                "    public " + builderSimple + " outputDecorator(Object d) { return this; }\n" +
                "    public " + builderSimple + " streamReadConstraints(Object c) { return this; }\n" +
                "    public " + builderSimple + " streamWriteConstraints(Object c) { return this; }\n" +
                "    public " + simpleName + " build() { return null; }\n" +
                "}\n";
    }

    /**
     * Appends a single builder method call to the template string.
     */
    private static void appendBuilderCall(J.MethodInvocation mi, SetterToBuilderMapping mapping,
                                          StringBuilder templateCode, List<Expression> templateArgs) {
        templateCode.append("\n.").append(mapping.builderName).append("(");
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
}
