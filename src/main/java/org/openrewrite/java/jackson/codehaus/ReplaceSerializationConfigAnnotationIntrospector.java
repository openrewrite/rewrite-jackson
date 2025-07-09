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
package org.openrewrite.java.jackson.codehaus;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor;
import org.openrewrite.java.tree.J;

import static org.openrewrite.java.template.internal.AbstractRefasterJavaVisitor.EmbeddingOption.SHORTEN_NAMES;

public class ReplaceSerializationConfigAnnotationIntrospector extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate serialization annotation processor";
    }

    @Override
    public String getDescription() {
        return "Migrate serialization annotation processor to use the codehaus config method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // The before and after templates use different types matching types, so we use JavaTemplate.Matcher here only
        JavaVisitor<ExecutionContext> javaVisitor = new AbstractRefasterJavaVisitor() {
            final JavaTemplate before = JavaTemplate
                    .builder("#{mapper:any(org.codehaus.jackson.map.ObjectMapper)}.getSerializationConfig().setAnnotationIntrospector(#{introspector:any(org.codehaus.jackson.map.AnnotationIntrospector)});")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();
            final JavaTemplate after = JavaTemplate
                    .builder("#{mapper:any(com.fasterxml.jackson.databind.ObjectMapper)}.setConfig(#{mapper}.getSerializationConfig().with(#{introspector:any(com.fasterxml.jackson.databind.AnnotationIntrospector)}));")
                    .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation elem, ExecutionContext ctx) {
                JavaTemplate.Matcher matcher = before.matcher(getCursor());
                if (matcher.find()) {
                    return embed(
                            after.apply(getCursor(), elem.getCoordinates().replace(), matcher.parameter(0), matcher.parameter(1)),
                            getCursor(),
                            ctx,
                            SHORTEN_NAMES
                    );
                }
                return super.visitMethodInvocation(elem, ctx);
            }
        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>("org.codehaus.jackson.map.ObjectMapper getSerializationConfig(..)", true),
                        new UsesMethod<>("org.codehaus.jackson.map.MapperConfig setAnnotationIntrospector(..)", true)
                ),
                javaVisitor
        );
    }

}
