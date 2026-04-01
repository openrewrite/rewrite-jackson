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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.singleton;

public class CommentOutSimpleModuleMethodCalls extends Recipe {

    private static final String SIMPLE_MODULE = "com.fasterxml.jackson.databind.module.SimpleModule";

    private static final Set<String> AFFECTED_MODULES = new HashSet<>(Arrays.asList(
            "com.fasterxml.jackson.datatype.joda.JodaModule"
    ));

    private static final MethodMatcher ADD_SERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addSerializer(..)", true);
    private static final MethodMatcher ADD_DESERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addDeserializer(..)", true);
    private static final MethodMatcher ADD_KEY_SERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addKeySerializer(..)", true);
    private static final MethodMatcher ADD_KEY_DESERIALIZER = new MethodMatcher(SIMPLE_MODULE + " addKeyDeserializer(..)", true);

    private static final List<MethodMatcher> MATCHERS = Arrays.asList(
            ADD_SERIALIZER, ADD_DESERIALIZER, ADD_KEY_SERIALIZER, ADD_KEY_DESERIALIZER
    );

    private static final String COMMENT_MARKER = "TODO this module no longer extends SimpleModule in Jackson 3";

    @Getter
    final String displayName = "Add comment to SimpleModule method calls on modules that no longer extend SimpleModule";

    @Getter
    final String description = "In Jackson 3, some modules (e.g. `JodaModule`) no longer extend `SimpleModule` " +
            "and instead extend `JacksonModule` directly. This means methods like `addSerializer()` and " +
            "`addDeserializer()` are no longer available on these types. This recipe adds a TODO comment " +
            "to flag these call sites for manual migration.";

    @Getter
    final Set<String> tags = singleton("jackson-3");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(ADD_SERIALIZER),
                        new UsesMethod<>(ADD_DESERIALIZER),
                        new UsesMethod<>(ADD_KEY_SERIALIZER),
                        new UsesMethod<>(ADD_KEY_DESERIALIZER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (MATCHERS.stream().noneMatch(matcher -> matcher.matches(m))) {
                            return m;
                        }
                        if (!isOnAffectedModule(m)) {
                            return m;
                        }
                        if (hasComment(m.getComments())) {
                            return m;
                        }
                        String prefixWhitespace = m.getPrefix().getWhitespace();
                        String indent = prefixWhitespace.replaceAll("^\\s*\\n", "");
                        String comment = " " + COMMENT_MARKER + ",\n" +
                                indent + " * so addSerializer/addDeserializer calls are no longer available.\n" +
                                indent + " * Move this call to a new SimpleModule and register it separately:\n" +
                                indent + " *   SimpleModule customModule = new SimpleModule();\n" +
                                indent + " *   customModule.addSerializer(...);\n" +
                                indent + " *   mapper.registerModule(customModule);\n" +
                                indent + " * Note: register the custom module AFTER the original module,\n" +
                                indent + " * as the last registered serializer for a given type wins.\n" +
                                indent + " ";
                        TextComment textComment = new TextComment(true, comment, prefixWhitespace, Markers.EMPTY);
                        return m.withComments(ListUtils.concat(m.getComments(), textComment));
                    }

                    private boolean isOnAffectedModule(J.MethodInvocation method) {
                        Expression select = method.getSelect();
                        if (select == null) {
                            return false;
                        }
                        JavaType type = select.getType();
                        if (type == null) {
                            return false;
                        }
                        for (String affectedModule : AFFECTED_MODULES) {
                            if (TypeUtils.isAssignableTo(affectedModule, type)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private boolean hasComment(List<Comment> comments) {
                        for (Comment c : comments) {
                            if (c instanceof TextComment &&
                                    ((TextComment) c).getText().contains(COMMENT_MARKER)) {
                                return true;
                            }
                        }
                        return false;
                    }
                }
        );
    }
}
