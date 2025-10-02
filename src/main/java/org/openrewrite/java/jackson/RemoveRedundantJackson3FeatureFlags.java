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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.Statement;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveRedundantJackson3FeatureFlags extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher ENABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " enable(..)");
    private static final MethodMatcher DISABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " disable(..)");
    private static final MethodMatcher CONFIGURE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " configure(..)");

    @Option(displayName = "Feature name",
            description = "The fully qualified feature flag name that has a new default in Jackson 3. " +
                    "Format: `ClassName.FEATURE_NAME` (e.g., `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`).",
            example = "MapperFeature.SORT_PROPERTIES_ALPHABETICALLY")
    String featureName;

    @Option(displayName = "New default value",
            description = "The new default boolean value for this feature flag in Jackson 3.",
            example = "true")
    Boolean newDefaultValue;

    @Override
    public String getDisplayName() {
        return "Remove redundant Jackson 3 feature flag configurations";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Remove `ObjectMapper` feature flag configurations that set values to their new Jackson 3 defaults. " +
                "For example, `disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` and " +
                "`configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` are redundant since this is " +
                "now disabled by default in Jackson 3. Handles `MapperFeature`, `DeserializationFeature`, " +
                "`SerializationFeature`, `CBORReadFeature`, `CBORWriteFeature`, and `XmlWriteFeature`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(ENABLE_MATCHER),
                        new UsesMethod<>(DISABLE_MATCHER),
                        new UsesMethod<>(CONFIGURE_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public @Nullable Statement visitStatement(Statement statement, ExecutionContext ctx) {
                        Statement s = super.visitStatement(statement, ctx);

                        // Check if this statement is a method invocation we want to remove
                        if (s instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) s;
                            if (shouldRemove(mi)) {
                                // If it's part of a chain, return the select; otherwise remove the statement
                                if (mi.getSelect() instanceof J.MethodInvocation) {
                                    return (Statement) mi.getSelect();
                                }
                                return null;
                            }
                        }

                        return s;
                    }

                    private boolean shouldRemove(J.MethodInvocation mi) {
                        if (ENABLE_MATCHER.matches(mi)) {
                            // Remove enable() if the new default is true
                            return Boolean.TRUE.equals(newDefaultValue) &&
                                   featureName.equals(getFeatureNameFromArg(mi.getArguments().get(0)));
                        }
                        if (DISABLE_MATCHER.matches(mi)) {
                            // Remove disable() if the new default is false
                            return Boolean.FALSE.equals(newDefaultValue) &&
                                   featureName.equals(getFeatureNameFromArg(mi.getArguments().get(0)));
                        }
                        if (CONFIGURE_MATCHER.matches(mi)) {
                            return shouldRemoveConfigureCall(mi);
                        }
                        return false;
                    }

                    private boolean shouldRemoveConfigureCall(J.MethodInvocation mi) {
                        // configure() takes two arguments: feature and boolean value
                        if (mi.getArguments().size() != 2) {
                            return false;
                        }

                        Expression featureArg = mi.getArguments().get(0);
                        Expression booleanArg = mi.getArguments().get(1);

                        // Remove configure(feature, value) if feature matches and value equals the new default
                        return featureName.equals(getFeatureNameFromArg(featureArg)) &&
                               J.Literal.isLiteralValue(booleanArg, newDefaultValue);
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
                }
        );
    }
}
