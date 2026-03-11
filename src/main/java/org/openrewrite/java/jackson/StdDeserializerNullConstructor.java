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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class StdDeserializerNullConstructor extends Recipe {

    private static final String STD_DESERIALIZER = "com.fasterxml.jackson.databind.deser.std.StdDeserializer";

    private static final MethodMatcher STD_DESER_CONSTRUCTOR =
            new MethodMatcher(STD_DESERIALIZER + " <constructor>(..)", true);

    final String displayName = "Replace `null` type in `StdDeserializer` constructor with actual type";

    final String description = "In Jackson 3, `StdDeserializer` no longer accepts `null` for the handled type " +
            "parameter. This recipe replaces `this(null)` and `super((Class<?>) null)` in `StdDeserializer` " +
            "subclass constructors with the actual type parameter from the class declaration.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(STD_DESERIALIZER, true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Match super(null) targeting StdDeserializer constructor
                        boolean isSuperCall = STD_DESER_CONSTRUCTOR.matches(mi);
                        // Match this(null) in StdDeserializer subclass constructors
                        boolean isThisCall = !isSuperCall && "this".equals(mi.getSimpleName());

                        if (!isSuperCall && !isThisCall) {
                            return mi;
                        }

                        J.MethodDeclaration constructor = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        if (constructor == null || !constructor.isConstructor()) {
                            return mi;
                        }

                        List<Expression> args = mi.getArguments();
                        if (args.size() != 1 || !isNullLiteral(args.get(0))) {
                            return mi;
                        }

                        J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (classDecl == null || classDecl.getType() == null) {
                            return mi;
                        }

                        // For this() calls, verify the enclosing class extends StdDeserializer
                        if (isThisCall && !TypeUtils.isAssignableTo(STD_DESERIALIZER, classDecl.getType())) {
                            return mi;
                        }

                        JavaType typeParam = resolveStdDeserializerTypeParam(classDecl.getType());
                        if (!(typeParam instanceof JavaType.FullyQualified)) {
                            return mi;
                        }

                        String className = ((JavaType.FullyQualified) typeParam).getClassName();
                        return JavaTemplate.builder(className + ".class")
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replaceArguments());
                    }

                    private boolean isNullLiteral(Expression expr) {
                        if (expr instanceof J.Literal) {
                            return ((J.Literal) expr).getValue() == null &&
                                    ((J.Literal) expr).getType() == JavaType.Primitive.Null;
                        }
                        if (expr instanceof J.TypeCast) {
                            return isNullLiteral(((J.TypeCast) expr).getExpression());
                        }
                        return false;
                    }

                    private @Nullable JavaType resolveStdDeserializerTypeParam(JavaType.FullyQualified type) {
                        JavaType.FullyQualified current = type;
                        while (current != null) {
                            JavaType.FullyQualified supertype = current.getSupertype();
                            if (supertype == null) {
                                break;
                            }
                            if (STD_DESERIALIZER.equals(supertype.getFullyQualifiedName())) {
                                if (supertype instanceof JavaType.Parameterized) {
                                    List<JavaType> params = ((JavaType.Parameterized) supertype).getTypeParameters();
                                    if (!params.isEmpty()) {
                                        return params.get(0);
                                    }
                                }
                                return null;
                            }
                            current = supertype;
                        }
                        return null;
                    }
                }
        );
    }
}
