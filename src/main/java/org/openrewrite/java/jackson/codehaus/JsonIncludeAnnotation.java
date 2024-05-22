/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.jackson.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.jackson.codehaus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class JsonIncludeAnnotation extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate to Jackson `@JsonInclude`";
    }

    @Override
    public String getDescription() {
        return "Codehaus Jackson only had `@JsonSerialize`, whereas FasterXML additionally has `@JsonInclude`. " +
               "This recipe adds that additional or replacement annotation when `include` options are passed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.codehaus.jackson.map.annotate.JsonSerialize", false),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        // TODO Update class and field annotations
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        // TODO Update method and parameter annotations
                        return super.visitMethodDeclaration(method, ctx);
                    }
                });
    }
}
