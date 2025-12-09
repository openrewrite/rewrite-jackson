package org.openrewrite.java.jackson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.Set;

import static java.util.Collections.singleton;

public class UpdateSerializationInclusionConfiguration extends Recipe {

    @Override
    public String getDisplayName() {
        return "Update configuration of serialization inclusion in ObjectMapper for Jackson 3";
    }

    @Override
    public String getDescription() {
        return "In Jackson 3, `mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)` is no longer supported " +
                "and should be replaced by `changeDefaultPropertyInclusion()` for both `valueInclusion` and `contentInclusion`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("jackson-3");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return null;
    }
}
