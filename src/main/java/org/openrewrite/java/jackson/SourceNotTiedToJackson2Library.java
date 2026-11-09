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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.java.jackson.ModuleStillOnJackson2.isJackson2Type;

@Getter
public class SourceNotTiedToJackson2Library extends ScanningRecipe<Set<String>> {

    String displayName = "Find sources not tied to a library on Jackson 2";
    String description = "Marks all source files except Java sources that hand Jackson 2 types to a library still on Jackson 2. " +
            "Such a source passes a value to a library method or constructor with a Jackson 2 parameter, " +
            "or references a library class that extends a Jackson 2 type, " +
            "like a deserializer in `@JsonDeserialize(using = ...)`. " +
            "Moving it to Jackson 3 would no longer compile against that library. " +
            "Spring is not such a library, as Spring Framework 7 has Jackson 3 counterparts for its Jackson 2 APIs.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> sourceClasses) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    for (J.ClassDeclaration classDecl : ((JavaSourceFile) tree).getClasses()) {
                        if (classDecl.getType() != null) {
                            sourceClasses.add(classDecl.getType().getFullyQualifiedName());
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> sourceClasses) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile) ||
                        tree instanceof JavaSourceFile && tiedToJackson2Library((JavaSourceFile) tree, sourceClasses)) {
                    return tree;
                }
                return SearchResult.found(tree);
            }
        };
    }

    private static boolean tiedToJackson2Library(JavaSourceFile sourceFile, Set<String> sourceClasses) {
        TypesInUse typesInUse = sourceFile.getTypesInUse();
        for (JavaType.Method method : typesInUse.getUsedMethods()) {
            if (isLibraryType(method.getDeclaringType(), sourceClasses)) {
                for (JavaType parameterType : method.getParameterTypes()) {
                    JavaType.FullyQualified parameterClass = TypeUtils.asFullyQualified(parameterType);
                    if (parameterClass != null && isJackson2Type(parameterClass)) {
                        return true;
                    }
                }
            }
        }
        for (JavaType type : typesInUse.getTypesInUse()) {
            if (type instanceof JavaType.FullyQualified &&
                    extendsJackson2ThroughLibrary((JavaType.FullyQualified) type, false, sourceClasses)) {
                return true;
            }
        }
        return false;
    }

    private static boolean extendsJackson2ThroughLibrary(JavaType.@Nullable FullyQualified type, boolean throughLibrary,
                                                         Set<String> sourceClasses) {
        if (type == null) {
            return false;
        }
        if (isJackson2Type(type)) {
            return throughLibrary;
        }
        boolean library = throughLibrary || isLibraryType(type, sourceClasses);
        if (extendsJackson2ThroughLibrary(type.getSupertype(), library, sourceClasses)) {
            return true;
        }
        for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
            if (extendsJackson2ThroughLibrary(anInterface, library, sourceClasses)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLibraryType(JavaType.FullyQualified type, Set<String> sourceClasses) {
        String fqn = type.getFullyQualifiedName();
        // Nested, local and anonymous classes share the name of their top-level class up to the first `$`
        int nested = fqn.indexOf('$');
        return !sourceClasses.contains(nested < 0 ? fqn : fqn.substring(0, nested)) &&
                !isJackson2Type(type) &&
                !fqn.startsWith("org.springframework.");
    }
}
