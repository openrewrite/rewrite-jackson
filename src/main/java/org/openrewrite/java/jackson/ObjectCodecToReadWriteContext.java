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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.openrewrite.Tree.randomId;

@Getter
public class ObjectCodecToReadWriteContext extends Recipe {

    private static final String OBJECT_CODEC = "com.fasterxml.jackson.core.ObjectCodec";
    private static final String OBJECT_READ_CONTEXT = "tools.jackson.core.ObjectReadContext";
    private static final String OBJECT_WRITE_CONTEXT = "tools.jackson.core.ObjectWriteContext";
    private static final MethodMatcher PARSER_GET_CODEC = new MethodMatcher(
            "com.fasterxml.jackson.core.JsonParser getCodec()", true);
    private static final MethodMatcher GENERATOR_GET_CODEC = new MethodMatcher(
            "com.fasterxml.jackson.core.JsonGenerator getCodec()", true);

    final String displayName = "Declare `ObjectCodec` variables as `ObjectReadContext` or `ObjectWriteContext`";

    final String description = "Jackson 3 removes `ObjectCodec`. `JsonParser.getCodec()` becomes " +
            "`objectReadContext()`, which returns an `ObjectReadContext`, and `JsonGenerator.getCodec()` becomes " +
            "`objectWriteContext()`, which returns an `ObjectWriteContext`. This recipe changes the declared type " +
            "of variables initialized from those calls, so the declaration still compiles after the migration.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OBJECT_CODEC, false), new JavaIsoVisitor<ExecutionContext>() {
            // Variables whose declared type changed, keyed by name and owner, so their usages get the new type too
            private final Map<String, JavaType.FullyQualified> retyped = new HashMap<>();

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                JavaType.FullyQualified newType = i.getFieldType() == null ? null : retyped.get(key(i.getFieldType()));
                if (newType == null || newType.equals(i.getType())) {
                    return i;
                }
                return i.withType(newType).withFieldType(i.getFieldType().withType(newType));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (mi.getMethodType() != null && mi.getSelect() instanceof J.Identifier &&
                        TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), OBJECT_CODEC)) {
                    JavaType.FullyQualified newType = TypeUtils.asFullyQualified(mi.getSelect().getType());
                    if (newType != null && !TypeUtils.isOfClassType(newType, OBJECT_CODEC)) {
                        return mi.withMethodType(mi.getMethodType().withDeclaringType(newType));
                    }
                }
                return mi;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                if (!(vd.getTypeExpression() instanceof J.Identifier || vd.getTypeExpression() instanceof J.FieldAccess) ||
                        !TypeUtils.isOfClassType(vd.getTypeExpression().getType(), OBJECT_CODEC) ||
                        vd.getTypeExpression() instanceof J.Identifier &&
                                "var".equals(((J.Identifier) vd.getTypeExpression()).getSimpleName())) {
                    return vd;
                }
                String newFqn = contextType(vd);
                if (newFqn == null) {
                    return vd;
                }

                JavaType.ShallowClass newType = JavaType.ShallowClass.build(newFqn);
                J.Identifier typeExpression = new J.Identifier(randomId(), vd.getTypeExpression().getPrefix(),
                        Markers.EMPTY, emptyList(), newType.getClassName(), newType, null);
                maybeAddImport(newFqn);
                maybeRemoveImport(OBJECT_CODEC);
                return vd.withTypeExpression(typeExpression)
                        .withVariables(ListUtils.map(vd.getVariables(), v -> {
                            if (v.getVariableType() != null) {
                                retyped.put(key(v.getVariableType()), newType);
                            }
                            J.MethodInvocation init = (J.MethodInvocation) v.getInitializer();
                            if (init != null && init.getMethodType() != null) {
                                v = v.withInitializer(init.withMethodType(init.getMethodType().withReturnType(newType)));
                            }
                            return v.withType(newType).withName(v.getName().withType(newType));
                        }));
            }

            private String key(JavaType.Variable variable) {
                return variable.getName() + "@" + variable.getOwner();
            }

            /**
             * @return the Jackson 3 type of the declaration when every variable is initialized from the same kind
             * of {@code getCodec()} call, or {@code null} otherwise.
             */
            private @Nullable String contextType(J.VariableDeclarations vd) {
                String fqn = null;
                for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                    Expression init = v.getInitializer();
                    String candidate = PARSER_GET_CODEC.matches(init) ? OBJECT_READ_CONTEXT :
                            GENERATOR_GET_CODEC.matches(init) ? OBJECT_WRITE_CONTEXT : null;
                    if (candidate == null || fqn != null && !fqn.equals(candidate)) {
                        return null;
                    }
                    fqn = candidate;
                }
                return fqn;
            }
        });
    }
}
