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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.Comments;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.singleton;

public class CommentMutatorsInObjectMapperSubclasses extends Recipe {

    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";

    private static final Set<String> MUTATORS = new HashSet<>(Arrays.asList(
            "configure", "enable", "disable", "registerModule", "registerModules", "findAndRegisterModules",
            "addMixIn", "addHandler", "clearProblemHandlers", "registerSubtypes",
            "activateDefaultTyping", "activateDefaultTypingAsProperty", "deactivateDefaultTyping"));

    private static final String COMMENT = "TODO Jackson 3's ObjectMapper is immutable, so a subclass can no longer configure itself. " +
            "Build a configured `JsonMapper.builder()` and pass it to the `super(..)` constructor, " +
            "see https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md#6-immutability-of-objectmapper-jsonfactory";

    @Getter
    final String displayName = "Flag mutators called by an `ObjectMapper` subclass on itself";

    @Getter
    final String description = "Jackson 3's `ObjectMapper` is immutable, and `setVisibility(..)`, `registerModule(..)` and the like no longer exist. " +
            "A subclass that configures itself in its constructor or initializer has no builder to fold these calls into, " +
            "so this recipe adds a `TODO` comment to each of them for manual migration.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OBJECT_MAPPER, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!isMutator(m) || !callsOnItself(m) || !insideObjectMapperSubclass() || alreadyCommented(m)) {
                    return m;
                }
                return Comments.of(new Cursor(getCursor().getParentOrThrow(), m)).comment(" " + COMMENT);
            }

            private boolean isMutator(J.MethodInvocation m) {
                String name = m.getSimpleName();
                return (name.startsWith("set") || MUTATORS.contains(name)) &&
                       m.getMethodType() != null &&
                       TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), OBJECT_MAPPER);
            }

            private boolean callsOnItself(J.MethodInvocation m) {
                Expression select = m.getSelect();
                return select == null || select instanceof J.Identifier && "this".equals(((J.Identifier) select).getSimpleName());
            }

            private boolean insideObjectMapperSubclass() {
                Cursor c = getCursor().dropParentUntil(v -> v instanceof J.ClassDeclaration || v instanceof J.NewClass || v == Cursor.ROOT_VALUE);
                Object value = c.getValue();
                JavaType type = value instanceof J.ClassDeclaration ? ((J.ClassDeclaration) value).getType() :
                        value instanceof J.NewClass && ((J.NewClass) value).getBody() != null ? ((J.NewClass) value).getType() : null;
                return type != null && TypeUtils.isAssignableTo(OBJECT_MAPPER, type);
            }

            private boolean alreadyCommented(J.MethodInvocation m) {
                return m.getComments().stream().anyMatch(c -> c.printComment(getCursor()).contains("TODO Jackson 3's ObjectMapper is immutable"));
            }
        });
    }
}
