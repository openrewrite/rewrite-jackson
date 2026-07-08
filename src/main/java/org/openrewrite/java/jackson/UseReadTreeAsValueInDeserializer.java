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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class UseReadTreeAsValueInDeserializer extends Recipe {

    private static final String OBJECT_CODEC = "com.fasterxml.jackson.core.ObjectCodec";
    private static final String DESERIALIZATION_CONTEXT = "com.fasterxml.jackson.databind.DeserializationContext";
    private static final MethodMatcher CODEC_TREE_TO_VALUE = new MethodMatcher(
            OBJECT_CODEC + " treeToValue(com.fasterxml.jackson.core.TreeNode, java.lang.Class)");
    private static final String COMMENT_MARKER =
            "TODO Jackson 3: ObjectReadContext has no treeToValue; call readTreeAsValue on a DeserializationContext";

    final String displayName = "Use `DeserializationContext.readTreeAsValue()` instead of `ObjectCodec.treeToValue()`";

    final String description = "In Jackson 3, `ObjectCodec` is replaced by `ObjectReadContext`, which does not " +
            "expose `treeToValue`. The equivalent method is `readTreeAsValue(JsonNode, Class)` on " +
            "`DeserializationContext`. This recipe rewrites `parser.getCodec().treeToValue(node, Foo.class)` to " +
            "`ctxt.readTreeAsValue(node, Foo.class)` inside methods that have a `DeserializationContext` " +
            "parameter (typically `JsonDeserializer.deserialize`).";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(CODEC_TREE_TO_VALUE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!CODEC_TREE_TO_VALUE.matches(mi)) {
                    return mi;
                }
                J.Identifier ctxtIdent = enclosingDeserializationContextParam();
                if (ctxtIdent == null) {
                    return addTodoComment(mi);
                }
                return JavaTemplate.builder(
                        "#{any(com.fasterxml.jackson.databind.DeserializationContext)}" +
                        ".readTreeAsValue(#{any(com.fasterxml.jackson.databind.JsonNode)}, #{any(java.lang.Class)})")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "jackson-annotations-2", "jackson-core-2", "jackson-databind-2"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(),
                                ctxtIdent, mi.getArguments().get(0), mi.getArguments().get(1));
            }

            private J.MethodInvocation addTodoComment(J.MethodInvocation mi) {
                for (Comment c : mi.getComments()) {
                    if (c instanceof TextComment && ((TextComment) c).getText().contains(COMMENT_MARKER)) {
                        return mi;
                    }
                }
                TextComment todo = new TextComment(true, " " + COMMENT_MARKER + " ",
                        mi.getPrefix().getWhitespace(), Markers.EMPTY);
                return mi.withComments(ListUtils.concat(mi.getComments(), todo));
            }

            private J.@Nullable Identifier enclosingDeserializationContextParam() {
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosing == null) {
                    return null;
                }
                for (Statement param : enclosing.getParameters()) {
                    if (!(param instanceof J.VariableDeclarations)) {
                        continue;
                    }
                    J.VariableDeclarations vd = (J.VariableDeclarations) param;
                    if (!TypeUtils.isOfClassType(vd.getType(), DESERIALIZATION_CONTEXT) ||
                            vd.getVariables().isEmpty()) {
                        continue;
                    }
                    J.VariableDeclarations.NamedVariable var = vd.getVariables().get(0);
                    JavaType type = var.getType() != null ? var.getType() : vd.getType();
                    return var.getName().withType(type);
                }
                return null;
            }
        });
    }
}
