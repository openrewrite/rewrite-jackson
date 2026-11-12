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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.jackson.codehaus.RemoveDoublyAnnotatedCodehausAnnotations.FindDoublyAnnotatedVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class TransferJsonSerializeArgumentsFromCodehausToFasterXML extends Recipe {

    private static final String[] TRANSFERABLE_ARGUMENTS = {
            "using", "contentUsing", "keyUsing", "as", "keyAs", "contentAs", "typing"};

    @Getter
    final String displayName = "Transfer @JsonSerialize arguments from Codehaus to FasterXML";

    @Getter
    final String description = "Transfer @JsonSerialize annotation arguments (using, contentUsing, keyUsing, as, " +
            "keyAs, contentAs, typing) from Codehaus to FasterXML. If the argument was set already, it will not be " +
            "transferred.";

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
                        Map<J.Annotation, J.Annotation> doubleAnnotated = new FindDoublyAnnotatedVisitor().reduce(tree, new HashMap<>());

                        for (String argumentName : TRANSFERABLE_ARGUMENTS) {
                            transferArgument(doubleAnnotated, argumentName);
                        }

                        return tree;
                    }

                    private void transferArgument(Map<J.Annotation, J.Annotation> doubleAnnotated, String argumentName) {
                        Map<J.Annotation, J.Assignment> argumentMap = codehausArguments(doubleAnnotated, argumentName);
                        if (!argumentMap.isEmpty()) {
                            doAfterVisit(new TransferArgumentsVisitor(argumentMap, argumentName));
                        }
                    }
                });
    }

    private static Map<J.Annotation, J.Assignment> codehausArguments(
            Map<J.Annotation, J.Annotation> doubleAnnotated, String argumentName) {
        // Map from fasterxml annotation -> the codehaus annotation's argument
        Map<J.Annotation, J.Assignment> mapToArgument = new HashMap<>();
        doubleAnnotated.forEach((key, value) -> {
            if (key.getArguments() != null) {
                key.getArguments().forEach(arg -> {
                    if (isArgument(arg, argumentName) && !isNoClass(((J.Assignment) arg).getAssignment())) {
                        mapToArgument.put(value, (J.Assignment) arg);
                    }
                });
            }
        });
        return mapToArgument;
    }

    private static boolean isArgument(Expression arg, String argumentName) {
        return arg instanceof J.Assignment &&
               ((J.Assignment) arg).getVariable() instanceof J.Identifier &&
               argumentName.equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName());
    }

    /**
     * Codehaus's {@code NoClass.class} is the default of {@code as}, {@code keyAs} and {@code contentAs}, which
     * fasterxml writes as {@code Void.class}.
     */
    private static boolean isNoClass(Expression value) {
        return value instanceof J.FieldAccess &&
               "class".equals(((J.FieldAccess) value).getSimpleName()) &&
               TypeUtils.isOfClassType(((J.FieldAccess) value).getTarget().getType(),
                       "org.codehaus.jackson.map.annotate.NoClass");
    }

    @RequiredArgsConstructor
    private static class TransferArgumentsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final Map<J.Annotation, J.Assignment> fasterXmlToArgument;
        private final String argumentName;

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Assignment argument = fasterXmlToArgument.get(annotation);
            if (argument != null) {
                JContainer<Expression> container = annotation.getPadding().getArguments();
                if (container == null || container.getElements().isEmpty() ||
                    container.getElements().get(0) instanceof J.Empty) {
                    return annotation.withArguments(
                            singletonList(transferred(argument, annotation).withPrefix(Space.EMPTY)));
                }

                List<JRightPadded<Expression>> arguments = container.getPadding().getElements();
                boolean notAlreadySet = arguments.stream().noneMatch(arg -> isArgument(arg.getElement(), argumentName));
                if (notAlreadySet) {
                    JRightPadded<Expression> last = arguments.get(arguments.size() - 1);
                    J.Assignment appended = transferred(argument, annotation)
                            .withPrefix(appendedPrefix(last.getElement().getPrefix()));
                    // The appended argument takes over the space before `)`
                    return annotation.getPadding().withArguments(container.getPadding().withElements(ListUtils.concat(
                            ListUtils.mapLast(arguments, arg -> arg.withAfter(Space.EMPTY)),
                            new JRightPadded<>(appended, last.getAfter(), Markers.EMPTY))));
                }
            }
            return annotation;
        }

        /**
         * On a line of its own when the last argument is, else after a space.
         */
        private Space appendedPrefix(Space lastPrefix) {
            String lineBreak = lastPrefix.getWhitespace();
            for (Comment comment : lastPrefix.getComments()) {
                if (comment.getSuffix().contains("\n")) {
                    lineBreak = comment.getSuffix();
                }
            }
            return lineBreak.contains("\n") ? Space.format(lineBreak) : Space.SINGLE_SPACE;
        }

        /**
         * The codehaus argument as an argument of the fasterxml annotation, typed by that annotation's attribute.
         */
        private J.Assignment transferred(J.Assignment argument, J.Annotation fasterxml) {
            JavaType.FullyQualified annotationType = TypeUtils.asFullyQualified(fasterxml.getType());
            return TypeUtils.findDeclaredMethod(annotationType, argumentName, emptyList())
                    .map(attribute -> argument.withVariable(argument.getVariable().withType(attribute))
                            .withType(attribute.getReturnType()))
                    .orElse(argument);
        }
    }
}
