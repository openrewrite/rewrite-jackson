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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaVarKeyword;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.openrewrite.Tree.randomId;

@Getter
public class ObjectCodecToReadWriteContext extends Recipe {

    private static final String OBJECT_CODEC = "com.fasterxml.jackson.core.ObjectCodec";
    private static final String OBJECT_READ_CONTEXT = "tools.jackson.core.ObjectReadContext";
    private static final String OBJECT_WRITE_CONTEXT = "tools.jackson.core.ObjectWriteContext";
    private static final MethodMatcher PARSER_GET_CODEC = new MethodMatcher("com.fasterxml.jackson.core.JsonParser getCodec()", true);
    private static final MethodMatcher GENERATOR_GET_CODEC = new MethodMatcher("com.fasterxml.jackson.core.JsonGenerator getCodec()", true);

    final String displayName = "Declare `ObjectCodec` variables as `ObjectReadContext` or `ObjectWriteContext`";

    final String description = "Jackson 3 removes `ObjectCodec`. `JsonParser.getCodec()` becomes " +
            "`objectReadContext()`, which returns an `ObjectReadContext`, and `JsonGenerator.getCodec()` becomes " +
            "`objectWriteContext()`, which returns an `ObjectWriteContext`. This recipe changes the declared type " +
            "of variables initialized from those calls, so the declaration still compiles after the migration.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(PARSER_GET_CODEC),
                new UsesMethod<>(GENERATOR_GET_CODEC)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                // Fields can be referenced before their declaration
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations field = (J.VariableDeclarations) statement;
                        JavaType.FullyQualified newType = contextType(field);
                        if (newType != null) {
                            retypeInScope(getCursor(), field, newType);
                        }
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (!TypeUtils.isOfClassType(i.getType(), OBJECT_CODEC)) {
                    return i;
                }
                JavaType.FullyQualified newType = retypedTo(i.getFieldType());
                if (newType == null) {
                    return i;
                }
                return i.withType(newType).withFieldType(i.getFieldType().withType(newType));
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                if (!TypeUtils.isOfClassType(fa.getType(), OBJECT_CODEC)) {
                    return fa;
                }
                JavaType.FullyQualified newType = retypedTo(fa.getName().getFieldType());
                return newType == null ? fa : fa.withType(newType);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (mi.getMethodType() == null || mi.getSelect() == null ||
                        !TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), OBJECT_CODEC)) {
                    return mi;
                }
                JavaType.FullyQualified selectType = TypeUtils.asFullyQualified(mi.getSelect().getType());
                if (!TypeUtils.isOfClassType(selectType, OBJECT_READ_CONTEXT) && !TypeUtils.isOfClassType(selectType, OBJECT_WRITE_CONTEXT)) {
                    return mi;
                }
                return mi.withMethodType(mi.getMethodType().withDeclaringType(selectType));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified newType = contextType(vd);
                if (newType == null) {
                    return vd;
                }

                J.Identifier typeExpression = new J.Identifier(randomId(), vd.getTypeExpression().getPrefix(),
                        Markers.EMPTY, emptyList(), newType.getClassName(), newType, null);
                maybeRemoveImport(OBJECT_CODEC);
                maybeAddImport(newType.getFullyQualifiedName());
                // Scoped to the declaring block or for loop, as same-named variables in sibling scopes have the same name and owner
                retypeInScope(getCursor().dropParentUntil(it -> it instanceof J.Block || it instanceof J.ForLoop || it == Cursor.ROOT_VALUE), vd, newType);
                return vd.withTypeExpression(typeExpression)
                        .withVariables(ListUtils.map(vd.getVariables(), v -> {
                            v = v.withInitializer(retypeInitializer(v.getInitializer(), newType)).withType(newType);
                            return v.withName(v.getName().withType(newType).withFieldType(v.getVariableType()));
                        }));
            }

            private void retypeInScope(Cursor scope, J.VariableDeclarations vd, JavaType.FullyQualified newType) {
                for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                    if (v.getVariableType() != null) {
                        scope.putMessage(key(v.getVariableType()), newType);
                    }
                }
            }

            @SuppressWarnings("unchecked")
            private Expression retypeInitializer(Expression init, JavaType.FullyQualified newType) {
                if (init instanceof J.TypeCast) {
                    return retypeInitializer(((J.TypeCast) init).getExpression().withPrefix(init.getPrefix()), newType);
                }
                if (init instanceof J.Parentheses) {
                    J.Parentheses<Expression> parentheses = (J.Parentheses<Expression>) init;
                    return parentheses.withTree(retypeInitializer(parentheses.getTree(), newType));
                }
                J.MethodInvocation getCodec = (J.MethodInvocation) init;
                return getCodec.getMethodType() == null ? getCodec :
                        getCodec.withMethodType(getCodec.getMethodType().withReturnType(newType));
            }

            private JavaType.@Nullable FullyQualified retypedTo(JavaType.@Nullable Variable variable) {
                return variable == null ? null : getCursor().getNearestMessage(key(variable));
            }

            private String key(JavaType.Variable variable) {
                return "retyped:" + variable.getName() + "@" + variable.getOwner();
            }

            private JavaType.@Nullable ShallowClass contextType(J.VariableDeclarations vd) {
                TypeTree typeExpression = vd.getTypeExpression();
                if (!(typeExpression instanceof J.Identifier || typeExpression instanceof J.FieldAccess) ||
                        typeExpression.getMarkers().findFirst(JavaVarKeyword.class).isPresent() ||
                        !TypeUtils.isOfClassType(typeExpression.getType(), OBJECT_CODEC)) {
                    return null;
                }
                String fqn = null;
                for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                    Expression init = Expression.unwrap(v.getInitializer());
                    if (init instanceof J.TypeCast && TypeUtils.isOfClassType(((J.TypeCast) init).getType(), OBJECT_CODEC)) {
                        init = ((J.TypeCast) init).getExpression().unwrap();
                    }
                    String candidate = PARSER_GET_CODEC.matches(init) ? OBJECT_READ_CONTEXT :
                            GENERATOR_GET_CODEC.matches(init) ? OBJECT_WRITE_CONTEXT : null;
                    if (candidate == null || fqn != null && !fqn.equals(candidate)) {
                        return null;
                    }
                    fqn = candidate;
                }
                return fqn == null ? null : JavaType.ShallowClass.build(fqn);
            }
        });
    }
}
