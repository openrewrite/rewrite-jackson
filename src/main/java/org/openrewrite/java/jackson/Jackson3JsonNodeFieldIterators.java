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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

public class Jackson3JsonNodeFieldIterators extends Recipe {

    private static final MethodMatcher FIELDS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fields()");
    private static final MethodMatcher FIELDS_NAMES = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fieldNames()");
    private static final MethodMatcher ELEMENTS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode elements()");

    @Override
    public String getDisplayName() {
        return "Migrate `JSONNode` field iterator for Jackson 3";
    }

    @Override
    public String getDescription() {
        return "`JSONNode` fields are using `Collections` instead of `Iterator` singe Jackson 3. " +
                "To mimic Jackson 2s behavior an additional call to `Collection#iterator()`is needed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(FIELDS),
                        new UsesMethod<>(FIELDS_NAMES),
                        new UsesMethod<>(ELEMENTS)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (FIELDS.matches(mi)) {
                            doAfterVisit(new ChangeMethodName("com.fasterxml.jackson.databind.JsonNode fields()", "properties", null, null).getVisitor());
                            return addCallToChain("java.util.Collections.<String, JsonNode>emptyMap().entrySet().iterator()", mi);
                        }
                        if (FIELDS_NAMES.matches(mi)) {
                            doAfterVisit(new ChangeMethodName("com.fasterxml.jackson.databind.JsonNode fieldNames()", "propertyNames", null, null).getVisitor());
                            return addCallToChain("java.util.Collections.<String>emptySet().iterator()", mi);
                        }
                        if (ELEMENTS.matches(mi)) {
                            doAfterVisit(new ChangeMethodName("com.fasterxml.jackson.databind.JsonNode elements()", "values", null, null).getVisitor());
                            return addCallToChain("java.util.Collections.<JsonNode>emptySet().iterator()", mi);
                        }

                        return mi;
                    }

                    private J.MethodInvocation addCallToChain(String template, J.MethodInvocation mi) {
                        // because we use this as intermediate and overwrite all interesting types manually later, we need no CL here
                        J.MethodInvocation iteratorCall = JavaTemplate.apply(template, updateCursor(mi), mi.getCoordinates().replace());
                        return iteratorCall.withSelect(mi).withPrefix(Space.EMPTY);
                    }
                }
        );
    }
}
