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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.*;

public class TransferUsingArgumentFromCodehausToFasterXML extends Recipe {

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Transfer using argument from Codehaus to FasterXML";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Transfer the using argument from Codehaus to FasterXML if it was not set before. " +
                "If the `using` argument was set already, it will not be transferred.";
    }


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                        new UsesType<>("org.codehaus.jackson.map.annotate.JsonSerialize", false),
                        new UsesType<>("com.fasterxml.jackson.databind.annotation.JsonSerialize", false)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J preVisit(@NonNull J tree, ExecutionContext ctx) {
                        stopAfterPreVisit();

                        // Map from codehaus -> fasterxml annotation
                        Map<J.Annotation, J.Annotation> doubleAnnotated = new RemoveDoublyAnnotatedCodehausAnnotations
                                .FindDoublyAnnotatedVisitor().reduce(tree, new HashMap<>());

                        doAfterVisit(new TransferUsingVisitor(mapToArgumentExpression(doubleAnnotated)));
                        return tree;
                    }
                });
    }


    private Map<J.Annotation, Expression> mapToArgumentExpression(Map<J.Annotation, J.Annotation> doubleAnnotated) {
        // Map from fasterxml -> value of "using=..." in codehaus annotation
        Map<J.Annotation, Expression> mapToArgument = new HashMap<>();
        doubleAnnotated.forEach((key, value) -> {
            if (key.getArguments() != null || key.getArguments().isEmpty()) {
                key.getArguments().forEach(arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assign = (J.Assignment) arg;
                        J.Identifier varId = (J.Identifier) assign.getVariable();
                        if ("using".equals(varId.getSimpleName())) {
                            mapToArgument.put(value, arg);
                        }
                    }
                });
            }
        });
        return mapToArgument;
    }

    @RequiredArgsConstructor
    private static class TransferUsingVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final Map<J.Annotation, Expression> mapToArgument;

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            Expression e = mapToArgument.get(annotation);
            if (e != null) {
                List<Expression> arguments = annotation.getArguments();
                if (arguments == null) {
                    arguments = new ArrayList<>();
                } else if (!arguments.isEmpty()) {
                    Optional<Expression> expression = arguments.stream().filter(arg -> {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assign = (J.Assignment) arg;
                            J.Identifier varId = (J.Identifier) assign.getVariable();
                            return "using".equals(varId.getSimpleName());
                        }
                        return false;
                    }).findFirst();

                    if (expression.isPresent()) {
                        return annotation;
                    }
                }

                arguments.add(e);
                J.Annotation a = annotation.withArguments(arguments);
                a = autoFormat(a, ctx);
                return a;
            }
            return annotation;
        }
    }
}
