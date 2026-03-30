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
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class AddJsonCreatorToPrivateConstructors extends Recipe {

    private static final String JACKSON_ANNOTATION_PACKAGE = "com.fasterxml.jackson.annotation";
    private static final String JACKSON_JSON_CREATOR = JACKSON_ANNOTATION_PACKAGE + ".JsonCreator";
    private static final AnnotationMatcher JSON_CREATOR_MATCHER = new AnnotationMatcher("@" + JACKSON_JSON_CREATOR, true);

    @Getter
    final String displayName = "Add `@JsonCreator` to non-public constructors";

    @Getter
    final String description = "Jackson 3 strictly enforces creator visibility rules. Non-public constructors in " +
            "Jackson-annotated classes that were auto-detected in Jackson 2 need an explicit `@JsonCreator` " +
            "annotation to work for deserialization in Jackson 3.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(JACKSON_ANNOTATION_PACKAGE + ".*", false),
                        Preconditions.not(new FindSourceFiles("**/*.kt").getVisitor())
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        getCursor().putMessage("HAS_JACKSON", hasJacksonAnnotation(classDecl));
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                        // Must be a constructor
                        if (!md.isConstructor()) {
                            return md;
                        }

                        // Must be non-public
                        if (md.hasModifier(J.Modifier.Type.Public)) {
                            return md;
                        }

                        // Must have parameters
                        if (md.getParameters().isEmpty() ||
                                (md.getParameters().size() == 1 && md.getParameters().get(0) instanceof J.Empty)) {
                            return md;
                        }

                        // Must not already have @JsonCreator
                        for (J.Annotation annotation : md.getLeadingAnnotations()) {
                            if (JSON_CREATOR_MATCHER.matches(annotation)) {
                                return md;
                            }
                        }

                        // Enclosing class must have at least one Jackson annotation
                        if (!Boolean.TRUE.equals(getCursor().getNearestMessage("HAS_JACKSON"))) {
                            return md;
                        }

                        // Add @JsonCreator
                        maybeAddImport(JACKSON_JSON_CREATOR);

                        return JavaTemplate
                                .builder("@JsonCreator")
                                .imports(JACKSON_JSON_CREATOR)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-annotations-2"))
                                .build()
                                .apply(
                                        getCursor(),
                                        md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                                );
                    }
                }
        );
    }

    private static boolean hasJacksonAnnotation(J.ClassDeclaration classDecl) {
        if (hasJacksonAmong(classDecl.getLeadingAnnotations())) {
            return true;
        }
        for (Statement stmt : classDecl.getBody().getStatements()) {
            if (stmt instanceof J.VariableDeclarations) {
                if (hasJacksonAmong(((J.VariableDeclarations) stmt).getLeadingAnnotations())) {
                    return true;
                }
            } else if (stmt instanceof J.MethodDeclaration) {
                J.MethodDeclaration md = (J.MethodDeclaration) stmt;
                if (hasJacksonAmong(md.getLeadingAnnotations())) {
                    return true;
                }
                for (Statement param : md.getParameters()) {
                    if (param instanceof J.VariableDeclarations &&
                            hasJacksonAmong(((J.VariableDeclarations) param).getLeadingAnnotations())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasJacksonAmong(List<J.Annotation> annotations) {
        for (J.Annotation annotation : annotations) {
            JavaType type = annotation.getType();
            if (type instanceof JavaType.FullyQualified &&
                    ((JavaType.FullyQualified) type).getPackageName().equals(JACKSON_ANNOTATION_PACKAGE)) {
                return true;
            }
        }
        return false;
    }
}
