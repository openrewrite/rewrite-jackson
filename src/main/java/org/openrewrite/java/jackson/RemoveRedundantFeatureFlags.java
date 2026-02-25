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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.FullyQualified;

import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveRedundantFeatureFlags extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher ENABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " enable(..)");
    private static final MethodMatcher DISABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " disable(..)");
    private static final MethodMatcher CONFIGURE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " configure(..)");
    private static final String OBJECT_MAPPER_BUILDER_TYPE = "com.fasterxml.jackson.databind.cfg.MapperBuilder";
    private static final MethodMatcher BUILDER_ENABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_BUILDER_TYPE + " enable(..)");
    private static final MethodMatcher BUILDER_DISABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_BUILDER_TYPE + " disable(..)");
    private static final MethodMatcher BUILDER_CONFIGURE_MATCHER = new MethodMatcher(OBJECT_MAPPER_BUILDER_TYPE + " configure(..)");

    @Option(displayName = "Feature name",
            description = "The fully qualified feature flag name that has a new default in Jackson 3. " +
                    "Format: `ClassName.FEATURE_NAME` (e.g., `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`).",
            example = "MapperFeature.SORT_PROPERTIES_ALPHABETICALLY")
    String featureName;

    @Option(displayName = "New default value",
            description = "The new default boolean value for this feature flag in Jackson 3.",
            example = "true")
    Boolean newDefaultValue;

    String displayName = "Remove redundant Jackson 3 feature flag configurations";

    String description = "Remove `ObjectMapper` feature flag configurations that set values to their new Jackson 3 defaults. " +
                "For example, `disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` and " +
                "`configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` are redundant since this is " +
                "now disabled by default in Jackson 3.";

    Set<String> tags = singleton( "jackson-3" );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;

                    TreeVisitor<?, ExecutionContext> javaVisitor = javaVisitor();
                    if (javaVisitor.isAcceptable(sourceFile, ctx)) {
                        return javaVisitor.visitNonNull(tree, ctx);
                    }

                    TreeVisitor<?, ExecutionContext> propertiesVisitor = propertiesVisitor();
                    if (propertiesVisitor.isAcceptable(sourceFile, ctx)) {
                        return propertiesVisitor.visitNonNull(tree, ctx);
                    }

                    TreeVisitor<?, ExecutionContext> yamlVisitor = yamlVisitor();
                    if (yamlVisitor.isAcceptable(sourceFile, ctx)) {
                        return yamlVisitor.visitNonNull(tree, ctx);
                    }
                }

                return tree;
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> javaVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(ENABLE_MATCHER),
                        new UsesMethod<>(BUILDER_ENABLE_MATCHER),
                        new UsesMethod<>(DISABLE_MATCHER),
                        new UsesMethod<>(BUILDER_DISABLE_MATCHER),
                        new UsesMethod<>(CONFIGURE_MATCHER),
                        new UsesMethod<>(BUILDER_CONFIGURE_MATCHER)
                ),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (shouldRemove(method)) {
                            maybeRemoveFeatureImport(method.getArguments().get(0));
                            // If it's part of a chain (method call or new X()), return the select; otherwise remove the statement
                            if (method.getSelect() instanceof J.MethodInvocation || method.getSelect() instanceof J.NewClass) {
                                return method.getSelect().withPrefix(method.getPrefix());
                            }
                            return null;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }

                    private void maybeRemoveFeatureImport(Expression arg) {
                        if (arg instanceof J.FieldAccess && ((J.FieldAccess) arg).getTarget().getType() instanceof FullyQualified) {
                            maybeRemoveImport((FullyQualified) ((J.FieldAccess) arg).getTarget().getType());
                        } else if (arg instanceof J.Identifier) {
                            J.Identifier identifier = (J.Identifier) arg;
                            if (identifier.getFieldType() != null && identifier.getFieldType().getOwner() instanceof FullyQualified) {
                                maybeRemoveImport((FullyQualified) identifier.getFieldType().getOwner());
                            }
                        }
                    }

                    private boolean shouldRemove(J.MethodInvocation mi) {
                        if (ENABLE_MATCHER.matches(mi) || BUILDER_ENABLE_MATCHER.matches(mi)) {
                            // Remove enable() if the new default is true
                            return newDefaultValue && featureName.equals(getFeatureNameFromArg(mi.getArguments().get(0)));
                        }
                        if (DISABLE_MATCHER.matches(mi) || BUILDER_DISABLE_MATCHER.matches(mi)) {
                            // Remove disable() if the new default is false
                            return !newDefaultValue && featureName.equals(getFeatureNameFromArg(mi.getArguments().get(0)));
                        }
                        if (CONFIGURE_MATCHER.matches(mi) || BUILDER_CONFIGURE_MATCHER.matches(mi)) {
                            // configure() takes two arguments: feature and boolean value
                            return mi.getArguments().size() == 2 &&
                                    J.Literal.isLiteralValue(mi.getArguments().get(1), newDefaultValue) &&
                                    featureName.equals(getFeatureNameFromArg(mi.getArguments().get(0)));
                        }
                        return false;
                    }

                    private @Nullable String getFeatureNameFromArg(Expression arg) {
                        if (arg instanceof J.FieldAccess) {
                            J.FieldAccess fieldAccess = (J.FieldAccess) arg;
                            if (fieldAccess.getTarget() instanceof J.Identifier) {
                                String className = ((J.Identifier) fieldAccess.getTarget()).getSimpleName();
                                String fieldName = fieldAccess.getName().getSimpleName();
                                return className + "." + fieldName;
                            }
                        } else if (arg instanceof J.Identifier) {
                            // Handle static imports
                            J.Identifier identifier = (J.Identifier) arg;
                            if (identifier.getFieldType() != null && identifier.getFieldType().getOwner() instanceof FullyQualified) {
                                FullyQualified owner = (FullyQualified) identifier.getFieldType().getOwner();
                                // Extract the simple class name from the fully qualified name
                                String fullyQualifiedName = owner.getFullyQualifiedName();
                                String className = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
                                String fieldName = identifier.getSimpleName();
                                return className + "." + fieldName;
                            }
                        }
                        return null;
                    }
                });
    }

    private TreeVisitor<?, ExecutionContext> propertiesVisitor() {
        String propertyKey = "spring.jackson.mapper." + featureName.split("\\.")[1];
        return Preconditions.check(
                // Only change if it does not already have the new default value
                new org.openrewrite.properties.ChangePropertyValue(propertyKey, String.valueOf(!newDefaultValue), null, false, false),
                new org.openrewrite.properties.DeleteProperty(propertyKey, false).getVisitor());
    }

    private TreeVisitor<?, ExecutionContext> yamlVisitor() {
        String propertyKey = "spring.jackson.mapper." + featureName.split("\\.")[1];
        return Preconditions.check(
                // Only change if it does not already have the new default value
                new org.openrewrite.yaml.ChangePropertyValue(propertyKey, String.valueOf(!newDefaultValue), null, false, false, null),
                new org.openrewrite.yaml.DeleteProperty(propertyKey, false, null, null).getVisitor());
    }
}
