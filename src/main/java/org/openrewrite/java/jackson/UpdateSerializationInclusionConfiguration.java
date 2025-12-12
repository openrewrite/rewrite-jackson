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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;

import java.util.Set;

import static java.util.Collections.singleton;

public class UpdateSerializationInclusionConfiguration extends Recipe {

    private static final MethodMatcher MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER = new MethodMatcher("com.fasterxml.jackson.databind..MapperBuilder serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include)");

    @Override
    public String getDisplayName() {
        return "Update configuration of serialization inclusion in ObjectMapper for Jackson 3";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)` is no longer supported " +
                "and should be replaced by `changeDefaultPropertyInclusion()` for both `valueInclusion` and `contentInclusion`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (MAPPER_BUILDER_SERIALIZATION_INCLUSION_MATCHER.matches(mi)) {
                    J.MethodInvocation result = JavaTemplate
                            .builder("#{any(com.fasterxml.jackson.databind.json.JsonMapper$Builder)}.changeDefaultPropertyInclusion(incl -> incl" +
                                    ".withContentInclusion(#{any(com.fasterxml.jackson.annotation.JsonInclude.Include)})" +
                                    ".withValueInclusion(#{any(com.fasterxml.jackson.annotation.JsonInclude.Include)}))")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-annotations-3", "jackson-core-3", "jackson-databind-3"))
                            .build()
                            .apply(
                                    getCursor(),
                                    mi.getCoordinates().replace(),
                                    mi.getSelect(),
                                    mi.getArguments().get(0),
                                    mi.getArguments().get(0));
                    return result.getPadding().withSelect(JRightPadded.build(result.getSelect()).withAfter(mi.getPadding().getSelect().getAfter()));
                }
                return mi;
            }
        });
    }
}
