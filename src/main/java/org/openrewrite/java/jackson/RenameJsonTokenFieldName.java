/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class RenameJsonTokenFieldName extends Recipe {

    private static final String JSON_TOKEN = "com.fasterxml.jackson.core.JsonToken";

    String displayName = "Rename `JsonToken.FIELD_NAME` to `PROPERTY_NAME`";

    String description = "In Jackson 3, the enum constant `JsonToken.FIELD_NAME` was renamed to `JsonToken.PROPERTY_NAME`.";

    Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JSON_TOKEN, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                        if (!"FIELD_NAME".equals(fa.getName().getSimpleName())) {
                            return fa;
                        }
                        JavaType targetType = fa.getTarget().getType();
                        if (!(targetType instanceof JavaType.FullyQualified) ||
                            !JSON_TOKEN.equals(((JavaType.FullyQualified) targetType).getFullyQualifiedName())) {
                            return fa;
                        }
                        J.Identifier name = fa.getName().withSimpleName("PROPERTY_NAME");
                        JavaType.Variable fieldType = name.getFieldType();
                        if (fieldType != null) {
                            name = name.withFieldType(fieldType.withName("PROPERTY_NAME"));
                        }
                        return fa.withName(name);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier id = super.visitIdentifier(identifier, ctx);
                        if (!"FIELD_NAME".equals(id.getSimpleName())) {
                            return id;
                        }
                        JavaType.Variable fieldType = id.getFieldType();
                        if (fieldType == null || !(fieldType.getOwner() instanceof JavaType.FullyQualified) ||
                            !JSON_TOKEN.equals(((JavaType.FullyQualified) fieldType.getOwner()).getFullyQualifiedName())) {
                            return id;
                        }
                        return id.withSimpleName("PROPERTY_NAME")
                                .withFieldType(fieldType.withName("PROPERTY_NAME"));
                    }
                }
        );
    }
}
