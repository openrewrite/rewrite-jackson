/*
 * Copyright 2026 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.text.PlainTextVisitor;

import java.nio.file.Paths;
import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Getter
public class LombokJacksonizedConfig extends ScanningRecipe<LombokJacksonizedConfig.Accumulator> {

    private static final String JACKSONIZED = "lombok.extern.jackson.Jacksonized";
    private static final String CONFIG_KEY = "lombok.jacksonized.jacksonVersion";
    private static final String CONFIG_LINE = CONFIG_KEY + " += 3";

    String displayName = "Update `lombok.config` for Jackson 3 compatibility";
    String description = "When `@Jacksonized` is used, Lombok generates Jackson annotations. " +
            "By default it generates Jackson 2.x annotations. This recipe adds " +
            "`lombok.jacksonized.jacksonVersion += 3` to `lombok.config` so Lombok generates " +
            "Jackson 3 compatible annotations.";

    public static class Accumulator {
        boolean hasJacksonized;
        boolean hasLombokConfig;
        boolean alreadyConfigured;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile sourceFile = (SourceFile) tree;
                if (tree instanceof PlainText && "lombok.config".equals(sourceFile.getSourcePath().toString())) {
                    acc.hasLombokConfig = true;
                    if (((PlainText) tree).getText().contains(CONFIG_KEY)) {
                        acc.alreadyConfigured = true;
                    }
                }

                if (!acc.hasJacksonized &&
                        tree instanceof JavaSourceFile &&
                        new Annotated.Matcher("@" + JACKSONIZED).lower(sourceFile).findFirst().isPresent()) {
                    acc.hasJacksonized = true;
                }

                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (!acc.hasJacksonized || acc.hasLombokConfig) {
            return emptyList();
        }

        return PlainTextParser.builder().build()
                .parse("")
                .map(s -> (SourceFile) s.withSourcePath(Paths.get("lombok.config")))
                .collect(toList());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (!acc.hasJacksonized || acc.alreadyConfigured) {
            return TreeVisitor.noop();
        }

        return Preconditions.check(
                new FindSourceFiles("lombok.config").getVisitor(),
                new PlainTextVisitor<ExecutionContext>() {
                    @Override
                    public PlainText visitText(PlainText text, ExecutionContext ctx) {
                        String content = text.getText();
                        if (content.isEmpty()) {
                            return text.withText(CONFIG_LINE);
                        }
                        if (!content.endsWith("\n")) {
                            content += "\n";
                        }
                        return text.withText(content + CONFIG_LINE);
                    }
                }
        );
    }
}
