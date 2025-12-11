package org.openrewrite.java.jackson;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class JsonNodeFieldIteratorMigration extends Recipe {

    private static final MethodMatcher FIELDS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fields()");
    private static final MethodMatcher FIELDS_NAMES = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode fieldNames()");
    private static final MethodMatcher ELEMENTS = new MethodMatcher("com.fasterxml.jackson.databind.JsonNode elements()");

    @Override
    public String getDisplayName() {
        return "Migrate `JSONNode` field iterator";
    }

    @Override
    public String getDescription() {
        return "`JSONNode` field iterators now use `Collections` instead of `Iterator`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                String template;
                if (FIELDS.matches(mi)) {
                    template = "#{any()}.properties().iterator()";
                } else if (FIELDS_NAMES.matches(mi)) {
                    template = "#{any()}.propertyNames().iterator()";
                } else if (ELEMENTS.matches(mi)) {
                    template = "#{any()}.values().iterator()";
                } else {
                    return mi;
                }

                return JavaTemplate.builder(template)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "jackson-databind-3", "jackson-core-3"))
                        .imports("tools.jackson.databind.JsonNode", "java.util.Iterator", "java.lang.Iterable", "java.util.Collection", "java.util.Map", "java.util.Map.Entry", "java.lang.String")
                        .build()
                        .apply(updateCursor(mi), mi.getCoordinates().replace(), mi.getSelect());
            }
        };
    }
}
