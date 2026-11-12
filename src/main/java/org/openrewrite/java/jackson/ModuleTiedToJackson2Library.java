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

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.openrewrite.java.jackson.ModuleStillOnJackson2.isJackson2Type;

@Value
@EqualsAndHashCode(callSuper = false)
public class ModuleTiedToJackson2Library extends ScanningRecipe<ModuleTiedToJackson2Library.Accumulator> {

    @Option(displayName = "Invert marking",
            description = "If `true`, marks every source file of the other modules instead, for use as a precondition. " +
                    "Defaults to `false`.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Find modules tied to a library on Jackson 2";

    String description = "Marks where a module hands Jackson 2 types to a library still on Jackson 2, " +
            "so that moving the module to Jackson 3 would no longer compile against that library. " +
            "That is a call with a value for a parameter the library declares with a Jackson 2 type, " +
            "a method overriding such a library method, " +
            "or a reference to a library class that extends a Jackson 2 type, like a deserializer in `@JsonDeserialize(using = ...)`. " +
            "Spring is not such a library, as Spring Framework 7 has Jackson 3 counterparts for its Jackson 2 APIs.";

    public static class Accumulator {
        final Set<String> sourceClasses = new HashSet<>();
        final Map<JavaProject, Set<String>> tyingClassesByModule = new HashMap<>();
        final Map<String, Boolean> extendsJackson2 = new ConcurrentHashMap<>();

        @Nullable
        Set<JavaProject> tiedModules;

        Set<JavaProject> tiedModules() {
            if (tiedModules == null) {
                tiedModules = new HashSet<>();
                for (Map.Entry<JavaProject, Set<String>> module : tyingClassesByModule.entrySet()) {
                    if (module.getValue().stream().anyMatch(this::isLibraryClass)) {
                        tiedModules.add(module.getKey());
                    }
                }
            }
            return tiedModules;
        }

        boolean isLibraryClass(String fqn) {
            // Nested, local and anonymous classes share the name of their top-level class up to the first `$`
            int nested = fqn.indexOf('$');
            return !sourceClasses.contains(nested < 0 ? fqn : fqn.substring(0, nested)) &&
                    !fqn.startsWith("org.springframework.");
        }

        /**
         * Classes that tie the module of the source to Jackson 2, unless the sources declare them.
         */
        Set<String> tyingClasses(JavaSourceFile sourceFile) {
            Set<String> classes = new HashSet<>();
            TypesInUse typesInUse = sourceFile.getTypesInUse();
            for (JavaType.Method method : typesInUse.getUsedMethods()) {
                String tyingClass = tyingClassOfCall(method);
                if (tyingClass != null) {
                    classes.add(tyingClass);
                }
            }
            for (JavaType.Method method : typesInUse.getDeclaredMethods()) {
                classes.addAll(tyingClassesOfOverride(method));
            }
            for (JavaType type : typesInUse.getTypesInUse()) {
                if (type instanceof JavaType.FullyQualified) {
                    addClassesExtendingJackson2((JavaType.FullyQualified) type, classes);
                }
            }
            return classes;
        }

        static @Nullable String tyingClassOfCall(JavaType.Method method) {
            JavaType.FullyQualified declaringType = method.getDeclaringType();
            if (isJackson2Type(declaringType)) {
                return null;
            }
            // A call's parameter types are instantiated, like `JsonNode` for `List<JsonNode>.add(E)`,
            // so the declared parameter has to be a Jackson 2 type as well
            for (JavaType.Method declared : declaringType.getMethods()) {
                if (declared.getName().equals(method.getName()) &&
                        declared.getParameterTypes().size() == method.getParameterTypes().size()) {
                    for (int i = 0; i < declared.getParameterTypes().size(); i++) {
                        if (isJackson2Parameter(declared.getParameterTypes().get(i)) &&
                                isJackson2Parameter(method.getParameterTypes().get(i))) {
                            return declaringType.getFullyQualifiedName();
                        }
                    }
                }
            }
            return null;
        }

        static List<String> tyingClassesOfOverride(JavaType.Method method) {
            List<String> classes = new ArrayList<>();
            for (JavaType.Method overridden = TypeUtils.findOverriddenMethod(method).orElse(null);
                 overridden != null;
                 overridden = TypeUtils.findOverriddenMethod(overridden).orElse(null)) {
                if (!isJackson2Type(overridden.getDeclaringType()) &&
                        overridden.getParameterTypes().stream().anyMatch(ModuleTiedToJackson2Library::isJackson2Parameter)) {
                    classes.add(overridden.getDeclaringType().getFullyQualifiedName());
                }
            }
            return classes;
        }

        void addClassesExtendingJackson2(JavaType.@Nullable FullyQualified type, Set<String> classes) {
            if (type == null || isJackson2Type(type) || !extendsJackson2(type)) {
                return;
            }
            classes.add(type.getFullyQualifiedName());
            addClassesExtendingJackson2(type.getSupertype(), classes);
            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                addClassesExtendingJackson2(anInterface, classes);
            }
        }

        private boolean extendsJackson2(JavaType.@Nullable FullyQualified type) {
            if (type == null) {
                return false;
            }
            if (isJackson2Type(type)) {
                return true;
            }
            Boolean extendsJackson2 = this.extendsJackson2.get(type.getFullyQualifiedName());
            if (extendsJackson2 == null) {
                extendsJackson2 = extendsJackson2(type.getSupertype()) ||
                        type.getInterfaces().stream().anyMatch(this::extendsJackson2);
                this.extendsJackson2.put(type.getFullyQualifiedName(), extendsJackson2);
            }
            return extendsJackson2;
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    acc.tiedModules = null;
                    for (J.ClassDeclaration classDecl : sourceFile.getClasses()) {
                        if (classDecl.getType() != null) {
                            acc.sourceClasses.add(classDecl.getType().getFullyQualifiedName());
                        }
                    }
                    sourceFile.getMarkers().findFirst(JavaProject.class).ifPresent(javaProject -> acc.tyingClassesByModule
                            .computeIfAbsent(javaProject, k -> new HashSet<>())
                            .addAll(acc.tyingClasses(sourceFile)));
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<JavaProject> tiedModules = acc.tiedModules();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                Optional<JavaProject> javaProject = tree.getMarkers().findFirst(JavaProject.class);
                boolean tied = javaProject.isPresent() ?
                        tiedModules.contains(javaProject.get()) :
                        tree instanceof JavaSourceFile && acc.tyingClasses((JavaSourceFile) tree).stream().anyMatch(acc::isLibraryClass);
                if (Boolean.TRUE.equals(invertMarking)) {
                    return tied ? tree : SearchResult.found(tree);
                }
                return tied && tree instanceof JavaSourceFile ? new MarkTies(acc).visit(tree, ctx) : tree;
            }
        };
    }

    private static boolean isJackson2Parameter(JavaType type) {
        while (type instanceof JavaType.Array) {
            type = ((JavaType.Array) type).getElemType();
        }
        return type instanceof JavaType.FullyQualified && isJackson2Type((JavaType.FullyQualified) type);
    }

    @RequiredArgsConstructor
    private static class MarkTies extends JavaIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        @Override
        public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
            return anImport;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            return m.getMethodType() == null ? m : markCall(m, m.getMethodType());
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass n = super.visitNewClass(newClass, ctx);
            return n.getConstructorType() == null ? n : markCall(n, n.getConstructorType());
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
            J.MemberReference m = super.visitMemberReference(memberRef, ctx);
            return m.getMethodType() == null ? m : markCall(m, m.getMethodType());
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (m.getMethodType() != null) {
                for (String tyingClass : Accumulator.tyingClassesOfOverride(m.getMethodType())) {
                    if (acc.isLibraryClass(tyingClass)) {
                        return m.withName(found(m.getName(), tyingClass));
                    }
                }
            }
            return m;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier i = super.visitIdentifier(identifier, ctx);
            if (i.getFieldType() == null && i.getType() instanceof JavaType.FullyQualified) {
                Set<String> classes = new LinkedHashSet<>();
                acc.addClassesExtendingJackson2((JavaType.FullyQualified) i.getType(), classes);
                for (String tyingClass : classes) {
                    if (acc.isLibraryClass(tyingClass)) {
                        return found(i, tyingClass);
                    }
                }
            }
            return i;
        }

        private <T extends J> T markCall(T call, JavaType.Method method) {
            String tyingClass = Accumulator.tyingClassOfCall(method);
            return tyingClass != null && acc.isLibraryClass(tyingClass) ? found(call, tyingClass) : call;
        }

        private static <T extends J> T found(T tree, String libraryClass) {
            return SearchResult.found(tree, "Keeps the module on Jackson 2, as " + libraryClass + " is still on Jackson 2");
        }
    }
}
