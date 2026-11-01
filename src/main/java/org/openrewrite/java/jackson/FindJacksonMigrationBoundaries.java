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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.jackson.table.JacksonMigrationBoundaries;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.*;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindJacksonMigrationBoundaries extends ScanningRecipe<Map<JavaProject, Set<String>>> {
    transient JacksonMigrationBoundaries boundaries = new JacksonMigrationBoundaries(this);

    String displayName = "Find Jackson migration boundaries";
    String description = "Report Jackson mapper construction, declarations, calls, and APIs whose signatures expose " +
            "Jackson 2 or Jackson 3 types. Distinguishes Spring APIs, APIs declared in the build project's supplied sources, and " +
            "external APIs. External APIs require review: sources may be missing, generated, or in another module. Shared Jackson " +
            "annotations do not establish a Jackson 2 requirement. Unresolved calls with Jackson-typed receivers " +
            "or arguments are reported separately. Makes no source or dependency changes and does not infer " +
            "runtime bean wiring, reflective use, or wire-format compatibility.";

    @Override
    public Map<JavaProject, Set<String>> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<JavaProject, Set<String>> sourceTypes) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                if (cd.getType() != null) {
                    JavaSourceFile source = getCursor().firstEnclosing(JavaSourceFile.class);
                    JavaProject project = source == null ? null : source.getMarkers().findFirst(JavaProject.class).orElse(null);
                    sourceTypes.computeIfAbsent(project, p -> new HashSet<>()).add(cd.getType().getFullyQualifiedName());
                }
                return super.visitClassDeclaration(cd, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<JavaProject, Set<String>> sourceTypes) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                recordCall(m, m.getMethodType(), m.getSelect(), m.getArguments(), ctx);
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                recordCall(n, n.getConstructorType(), n, n.getArguments(), ctx);
                return n;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference m = super.visitMemberReference(memberRef, ctx);
                recordCall(m, m.getMethodType(), m.getContaining(), Collections.emptyList(), ctx);
                return m;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(declarations, ctx);
                Set<String> types = jacksonTypes(vd.getType());
                if (!types.isEmpty()) {
                    record(vd, "DECLARATION", types, String.valueOf(vd.getType()), ctx);
                }
                return vd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Set<String> types = signatureTypes(m.getMethodType());
                if (!types.isEmpty()) {
                    // Include annotations such as @Bean and @Qualifier without claiming to resolve runtime wiring.
                    record(m.withBody(null), "DECLARATION", types, String.valueOf(m.getMethodType()), ctx);
                }
                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                Set<String> types = new TreeSet<>();
                if (cd.getExtends() != null) {
                    types.addAll(jacksonTypes(cd.getExtends().getType()));
                }
                if (cd.getImplements() != null) {
                    for (TypeTree implemented : cd.getImplements()) {
                        types.addAll(jacksonTypes(implemented.getType()));
                    }
                }
                if (!types.isEmpty()) {
                    record(cd.getName(), "DECLARATION", types, String.valueOf(cd.getType()), ctx);
                }
                return cd;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type != null && type.getFullyQualifiedName().startsWith("com.fasterxml.jackson.annotation.")) {
                    record(a, "SHARED_ANNOTATION", Collections.singleton(type.getFullyQualifiedName()), type.getFullyQualifiedName(), ctx);
                } else {
                    Set<String> types = jacksonTypes(a.getType());
                    if (!types.isEmpty()) {
                        record(a, "DECLARATION", types, String.valueOf(a.getType()), ctx);
                    }
                }
                return a;
            }

            private void recordCall(J tree, JavaType.@Nullable Method method, @Nullable Expression receiver,
                                    List<Expression> arguments, ExecutionContext ctx) {
                if (method == null) {
                    Set<String> types = new TreeSet<>();
                    if (receiver != null) {
                        types.addAll(jacksonTypes(receiver.getType()));
                    }
                    for (Expression argument : arguments) {
                        types.addAll(jacksonTypes(argument.getType()));
                    }
                    if (!types.isEmpty()) {
                        record(tree, "UNRESOLVED_CALL", types, "", ctx);
                    }
                    return;
                }
                Set<String> types = signatureTypes(method);
                String owner = method.getDeclaringType().getFullyQualifiedName();
                types.addAll(jacksonTypes(method.getDeclaringType()));
                if (types.isEmpty()) {
                    return;
                }
                String category;
                if (owner.startsWith("org.springframework.")) {
                    category = "SPRING_API";
                } else if (isJackson(owner)) {
                    category = tree instanceof J.NewClass &&
                            (TypeUtils.isAssignableTo("com.fasterxml.jackson.databind.ObjectMapper", method.getDeclaringType()) ||
                             TypeUtils.isAssignableTo("tools.jackson.databind.ObjectMapper", method.getDeclaringType())) ?
                            "MAPPER_CONSTRUCTION" : "JACKSON_API";
                } else {
                    JavaSourceFile source = getCursor().firstEnclosing(JavaSourceFile.class);
                    JavaProject project = source == null ? null : source.getMarkers().findFirst(JavaProject.class).orElse(null);
                    if (sourceTypes.getOrDefault(project, Collections.emptySet()).contains(owner)) {
                        category = "SOURCE_API";
                    } else {
                        // Generic signatures can expose Jackson only through call-site inference, not a library constraint.
                        category = isGenericApi(method) ?
                                "EXTERNAL_GENERIC_API" : "EXTERNAL_API";
                    }
                }
                record(tree, category, types, method.toString(), ctx);
            }

            private void record(J tree, String category, Set<String> types, String api, ExecutionContext ctx) {
                JavaSourceFile source = getCursor().firstEnclosing(JavaSourceFile.class);
                if (source != null) {
                    boundaries.insertRow(ctx, new JacksonMigrationBoundaries.Row(
                            source.getMarkers().findFirst(JavaProject.class).map(JavaProject::getProjectName).orElse("unknown"),
                            source.getMarkers().findFirst(JavaSourceSet.class).map(JavaSourceSet::getName).orElse("unknown"),
                            source.getSourcePath().toString(), category, String.join(", ", types), api,
                            tree.printTrimmed(tree.isScope(getCursor().getValue()) ? getCursor().getParentOrThrow() : getCursor())));
                }
            }
        };
    }

    private static boolean isGenericApi(JavaType.Method method) {
        if (!method.getDeclaredFormalTypeNames().isEmpty() || method.getDeclaringType() instanceof JavaType.Parameterized) {
            return true;
        }
        // The attributed call can lose formal type names after substituting inferred arguments.
        // An overload with the same arity is enough to require review rather than claim a fixed constraint.
        for (JavaType.Method declaration : method.getDeclaringType().getMethods()) {
            if (declaration.getName().equals(method.getName()) &&
                    declaration.getParameterTypes().size() == method.getParameterTypes().size() &&
                    !declaration.getDeclaredFormalTypeNames().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> signatureTypes(JavaType.@Nullable Method method) {
        Set<String> types = new TreeSet<>();
        if (method != null) {
            types.addAll(jacksonTypes(method.getReturnType()));
            for (JavaType parameter : method.getParameterTypes()) {
                types.addAll(jacksonTypes(parameter));
            }
        }
        return types;
    }

    private static Set<String> jacksonTypes(@Nullable JavaType type) {
        Set<String> types = new TreeSet<>();
        collectTypes(type, types, Collections.newSetFromMap(new IdentityHashMap<>()));
        return types;
    }

    private static void collectTypes(@Nullable JavaType type, Set<String> types, Set<JavaType> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq != null && isJackson(fq.getFullyQualifiedName())) {
            types.add(fq.getFullyQualifiedName());
        }
        if (type instanceof JavaType.Parameterized) {
            for (JavaType parameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                collectTypes(parameter, types, visited);
            }
        } else if (type instanceof JavaType.Array) {
            collectTypes(((JavaType.Array) type).getElemType(), types, visited);
        } else if (type instanceof JavaType.GenericTypeVariable) {
            for (JavaType bound : ((JavaType.GenericTypeVariable) type).getBounds()) {
                collectTypes(bound, types, visited);
            }
        }
        // A customer subtype is itself version-bound (e.g. class MyMapper extends ObjectMapper).
        if (fq != null && !isJackson(fq.getFullyQualifiedName())) {
            collectTypes(fq.getSupertype(), types, visited);
            for (JavaType.FullyQualified implemented : fq.getInterfaces()) {
                collectTypes(implemented, types, visited);
            }
        }
    }

    private static boolean isJackson(String fqn) {
        return fqn.startsWith("com.fasterxml.jackson.") && !fqn.startsWith("com.fasterxml.jackson.annotation.") ||
                fqn.startsWith("tools.jackson.") || fqn.startsWith("org.codehaus.jackson.");
    }
}
