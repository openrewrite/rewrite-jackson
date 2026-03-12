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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

@Getter
public class StdDeserializerNullConstructor extends Recipe {

    private static final String STD_DESERIALIZER = "com.fasterxml.jackson.databind.deser.std.StdDeserializer";
    private static final MethodMatcher STD_DESER_CONSTRUCTOR =
            new MethodMatcher(STD_DESERIALIZER + " <constructor>(..)", true);
    private static final MethodMatcher ANY_STD_DESER_SUBCLASS_CONSTRUCTOR =
            new MethodMatcher("* <constructor>(..)");

    String displayName = "Replace `null` type in `StdDeserializer` constructor with actual type";

    String description = "In Jackson 3, `StdDeserializer` no longer accepts `null` for the handled type " +
            "parameter. This recipe replaces `this(null)` and `super((Class<?>) null)` in `StdDeserializer` " +
            "subclass constructors with the actual type parameter from the class declaration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(STD_DESER_CONSTRUCTOR),
                        new UsesMethod<>(ANY_STD_DESER_SUBCLASS_CONSTRUCTOR)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Match super(null) on StdDeserializer or this(null) on a subclass
                        if (!STD_DESER_CONSTRUCTOR.matches(mi) &&
                                !(ANY_STD_DESER_SUBCLASS_CONSTRUCTOR.matches(mi) &&
                                        isInStdDeserializerSubclass())) {
                            return mi;
                        }

                        List<Expression> args = mi.getArguments();
                        if (args.size() != 1 || !isNullLiteral(args.get(0))) {
                            // too many arguments, this seems to be a customer constructor
                            return mi;
                        }

                        JavaType typeParam = resolveStdDeserializerTypeParam();
                        if (!(typeParam instanceof JavaType.FullyQualified)) {
                            // unable to determine the generic type parameter
                            return mi;
                        }

                        String className = ((JavaType.FullyQualified) typeParam).getClassName();
                        return JavaTemplate.apply(className + ".class", getCursor(), mi.getCoordinates().replaceArguments());
                    }

                    private boolean isInStdDeserializerSubclass() {
                        J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        return classDecl != null && classDecl.getType() != null &&
                                TypeUtils.isAssignableTo(STD_DESERIALIZER, classDecl.getType());
                    }

                    private boolean isNullLiteral(Expression expr) {
                        if (expr instanceof J.TypeCast) {
                            return isNullLiteral(((J.TypeCast) expr).getExpression());
                        }
                        return J.Literal.isLiteralValue(expr, null);
                    }

                    /**
                     * resolves the generic type argument for the StdDeserializer class we are in
                     */
                    private @Nullable JavaType resolveStdDeserializerTypeParam() {
                        J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (classDecl == null || classDecl.getType() == null) {
                            return null;
                        }

                        // supertype is never null because TypeUtils.isOfClassType would be false otherwise
                        for (JavaType.FullyQualified supertype = classDecl.getType().getSupertype();
                             !TypeUtils.isOfClassType(supertype, "java.lang.Object");
                             supertype = supertype.getSupertype()) {
                            if (TypeUtils.isOfClassType(supertype, STD_DESERIALIZER)) {
                                List<JavaType> params = supertype.getTypeParameters();
                                if (!params.isEmpty()) {
                                    return params.get(0);
                                }
                            }
                        }

                        return null;
                    }
                }
        );
    }
}
