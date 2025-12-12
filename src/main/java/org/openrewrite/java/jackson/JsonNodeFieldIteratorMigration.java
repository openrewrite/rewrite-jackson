package org.openrewrite.java.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

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

                String iteratorTemplate;
                if (FIELDS.matches(mi)) {
                    iteratorTemplate = "new HashMap<String, JsonNode>().iterator()";
                } else if (FIELDS_NAMES.matches(mi)) {
                    iteratorTemplate = "new HashSet<String>().iterator()";
                } else if (ELEMENTS.matches(mi)) {
                    iteratorTemplate = "new HashSet<JsonNode>().iterator()";
                } else {
                    return mi;
                }

                J.MethodInvocation iteratorCall = JavaTemplate.builder(iteratorTemplate)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jackson-databind-3", "jackson-core-3"))
                        .imports("tools.jackson.databind.JsonNode", "java.util.Iterator", "java.lang.Iterable", "java.util.HashSet", "java.util.HashMap", "java.util.Map.Entry", "java.lang.String")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replaceMethod());
                return autoFormat(iteratorCall.withSelect(mi), ctx);
            }
        };
    }
}
