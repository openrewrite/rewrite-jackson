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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RemoveRedundantJackson3FeatureFlags extends Recipe {

    private static final String OBJECT_MAPPER_TYPE = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final MethodMatcher ENABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " enable(..)");
    private static final MethodMatcher DISABLE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " disable(..)");
    private static final MethodMatcher CONFIGURE_MATCHER = new MethodMatcher(OBJECT_MAPPER_TYPE + " configure(..)");

    // Features that changed from false to true (should remove enable() or configure(..., true) calls)
    private static final Set<String> ENABLED_BY_DEFAULT_IN_V3 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MapperFeature.SORT_PROPERTIES_ALPHABETICALLY",
            "DeserializationFeature.READ_ENUMS_USING_TO_STRING",
            "DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES",
            "DeserializationFeature.FAIL_ON_TRAILING_TOKENS",
            "SerializationFeature.WRITE_ENUMS_USING_TO_STRING",
            // CBOR read features
            "CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING",
            "CBORReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT",
            "CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT",
            // CBOR write features
            "CBORWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING",
            // XML write features
            "XmlWriterFeature.UNWRAP_ROOT_OBJECT_NODE",
            "XmlWriterFeature.WRITE_NULLS_AS_XSI_NIL",
            "XmlWriterFeature.AUTO_DETECT_XSI_TYPE",
            "XmlWriterFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS"
    )));

    // Features that changed from true to false (should remove disable() calls)
    private static final Set<String> DISABLED_BY_DEFAULT_IN_V3 = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS",
            "MapperFeature.DEFAULT_VIEW_INCLUSION",
            "MapperFeature.USE_GETTERS_AS_SETTERS",
            "DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES",
            "SerializationFeature.FAIL_ON_EMPTY_BEANS",
            "SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS",
            "SerializationFeature.WRITE_DATES_AS_TIMESTAMPS"
    )));

    @Override
    public String getDisplayName() {
        return "Remove redundant Jackson 3 feature flag configurations";
    }

    @Override
    public String getDescription() {
        return "Remove `ObjectMapper` feature flag configurations that set values to their new Jackson 3 defaults. " +
                "For example, `disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` and " +
                "`configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` are redundant since this is " +
                "now disabled by default in Jackson 3. Handles `MapperFeature`, `DeserializationFeature`, " +
                "`SerializationFeature`, `CBORReadFeature`, `CBORWriteFeature`, and `XmlWriterFeature`.";
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
                            return isFeatureInSet(mi.getArguments().get(0), ENABLED_BY_DEFAULT_IN_V3);
                        }
                        if (DISABLE_MATCHER.matches(mi)) {
                            return isFeatureInSet(mi.getArguments().get(0), DISABLED_BY_DEFAULT_IN_V3);
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

                        // Check if it's configure(feature, true) for features enabled by default
                        if (J.Literal.isLiteralValue(booleanArg, true) &&
                                isFeatureInSet(featureArg, ENABLED_BY_DEFAULT_IN_V3)) {
                            return true;
                        }

                        // Check if it's configure(feature, false) for features disabled by default
                        return J.Literal.isLiteralValue(booleanArg, false) &&
                                isFeatureInSet(featureArg, DISABLED_BY_DEFAULT_IN_V3);
                    }

                    private boolean isFeatureInSet(Expression arg, Set<String> featureSet) {
                        String featureName = getFeatureName(arg);
                        return featureName != null && featureSet.contains(featureName);
                    }

                    private @Nullable String getFeatureName(Expression arg) {
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
                            if (identifier.getFieldType() != null && identifier.getFieldType().getOwner() instanceof org.openrewrite.java.tree.JavaType.FullyQualified) {
                                org.openrewrite.java.tree.JavaType.FullyQualified owner =
                                    (org.openrewrite.java.tree.JavaType.FullyQualified) identifier.getFieldType().getOwner();
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
