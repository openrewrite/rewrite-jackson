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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

@Getter
public class JsonNodeTextValueToStringValue extends Recipe {

    private static final MethodMatcher TEXT_VALUE = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode textValue()", true);

    final String displayName = "Replace `JsonNode.textValue()` with `stringValue(null)`";

    final String description = "In Jackson 2, `JsonNode.textValue()` returns `null` for nodes that are not textual. " +
            "In Jackson 3, `textValue()` delegates to `stringValue()`, which throws for nodes that are not strings. " +
            "`stringValue(null)` keeps the Jackson 2 behavior of returning `null`.";

    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TEXT_VALUE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!TEXT_VALUE.matches(mi) || mi.getMethodType() == null) {
                    return mi;
                }
                JavaType.Method stringValue = mi.getMethodType()
                        .withName("stringValue")
                        .withParameterNames(singletonList("defaultValue"))
                        .withParameterTypes(singletonList(JavaType.ShallowClass.build("java.lang.String")));
                return mi.withName(mi.getName().withSimpleName("stringValue"))
                        .withMethodType(stringValue)
                        .withArguments(singletonList(new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, null, "null", null, JavaType.Primitive.Null)));
            }
        });
    }
}
