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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class UseJsonFactoryStaticBuilder extends Recipe {

    private static final String JSON_FACTORY = "tools.jackson.core.json.JsonFactory";
    private static final String JSON_FACTORY_BUILDER = "tools.jackson.core.json.JsonFactoryBuilder";

    private static final MethodMatcher BUILDER_NO_ARG_CTOR =
            new MethodMatcher(JSON_FACTORY_BUILDER + " <constructor>()");

    String displayName = "Use `JsonFactory.builder()` over `new JsonFactoryBuilder()`";
    String description = "After the Jackson 2 → 3 migration, prefer the concrete static " +
            "`JsonFactory.builder()` entry over `new JsonFactoryBuilder()` so `JsonFactory` " +
            "chains read the same way as the format-aligned factories (`YAMLFactory.builder()`, " +
            "`CBORFactory.builder()`, etc.). The reason `MigrateFactorySettersToBuilder` emits " +
            "`new JsonFactoryBuilder()` in the first place is a Jackson 2 quirk — " +
            "`JsonFactory.builder()` returned the wildcard `TSFBuilder<?, ?>` there. In Jackson 3 " +
            "the static returns a concretely-typed `JsonFactoryBuilder`, so the constructor form " +
            "no longer earns its keep.";
    Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(JSON_FACTORY_BUILDER, false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                        if (!BUILDER_NO_ARG_CTOR.matches(nc)) {
                            return nc;
                        }
                        maybeRemoveImport(JSON_FACTORY_BUILDER);
                        maybeAddImport(JSON_FACTORY);
                        return JavaTemplate.builder("JsonFactory.builder()")
                                .imports(JSON_FACTORY)
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "jackson-core-3"))
                                .build()
                                .apply(getCursor(), nc.getCoordinates().replace());
                    }
                }
        );
    }
}
