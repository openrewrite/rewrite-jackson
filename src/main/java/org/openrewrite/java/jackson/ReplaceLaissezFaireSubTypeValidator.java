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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceLaissezFaireSubTypeValidator extends Recipe {

    private static final String LAISSEZ_FAIRE = "com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator";
    private static final String BASIC_VALIDATOR = "com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator";

    String displayName = "Replace `LaissezFaireSubTypeValidator` with an equivalent `BasicPolymorphicTypeValidator`";
    String description = "In Jackson 3, `LaissezFaireSubTypeValidator` is no longer public. " +
            "Replace `LaissezFaireSubTypeValidator.instance` and `new LaissezFaireSubTypeValidator()` with " +
            "`BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build()`, which also allows every subtype.";
    Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(LAISSEZ_FAIRE, false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        if (isInstanceField(fieldAccess.getName().getFieldType()) &&
                                getCursor().firstEnclosing(J.Import.class) == null) {
                            return replace(fieldAccess, ctx);
                        }
                        return super.visitFieldAccess(fieldAccess, ctx);
                    }

                    @Override
                    public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        // A statically imported `instance`
                        if (isInstanceField(identifier.getFieldType()) &&
                                !(getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess) &&
                                getCursor().firstEnclosing(J.Import.class) == null) {
                            maybeRemoveImport(LAISSEZ_FAIRE + ".instance");
                            return replace(identifier, ctx);
                        }
                        return super.visitIdentifier(identifier, ctx);
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (TypeUtils.isOfClassType(newClass.getType(), LAISSEZ_FAIRE) && newClass.getBody() == null) {
                            return replace(newClass, ctx);
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private boolean isInstanceField(JavaType.@Nullable Variable field) {
                        return field != null && "instance".equals(field.getName()) &&
                                TypeUtils.isOfClassType(field.getOwner(), LAISSEZ_FAIRE);
                    }

                    private J replace(J tree, ExecutionContext ctx) {
                        maybeRemoveImport(LAISSEZ_FAIRE);
                        maybeAddImport(BASIC_VALIDATOR);
                        return JavaTemplate.builder("BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build()")
                                .imports(BASIC_VALIDATOR)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                        "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"))
                                .build()
                                .apply(getCursor(), ((Expression) tree).getCoordinates().replace());
                    }
                }
        );
    }
}
