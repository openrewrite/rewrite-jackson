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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

public class JsonNodeFieldIteratorMigration extends Recipe {

    private static final MethodMatcher FIELDS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fields()");
    private static final MethodMatcher FIELDS_NAMES = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fieldNames()");
    private static final MethodMatcher ELEMENTS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode elements()");

    @Override
    public String getDisplayName() {
        return "Migrate `JSONNode` field iterator";
    }

    @Override
    public String getDescription() {
        return "`JSONNode` field iterators now use `Collections` instead of `Iterator`. " +
                "To mimic Jackson's old behavior an additional call to `Collection#iterator()`is needed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation( method, ctx );

                if (FIELDS.matches(mi)) {
                    return addCallToChain( "java.util.Collections.<String, JsonNode>emptyMap().entrySet().iterator()", mi );
                }
                if (FIELDS_NAMES.matches(mi)) {
                    return addCallToChain( "java.util.Collections.<String>emptySet().iterator()", mi );
                }
                if (ELEMENTS.matches(mi)) {
                    return addCallToChain( "java.util.Collections.<JsonNode>emptySet().iterator()", mi );
                }

                return mi;
            }

            private J.MethodInvocation addCallToChain(String template, J.MethodInvocation mi) {
                // because we use this as intermediate and overwrite all interesting types manually later, we need no CL here
                J.MethodInvocation iteratorCall = JavaTemplate.apply(template, updateCursor(mi), mi.getCoordinates().replace());
                return iteratorCall.withSelect(mi).withPrefix(Space.EMPTY);
            }
        };
    }
}
