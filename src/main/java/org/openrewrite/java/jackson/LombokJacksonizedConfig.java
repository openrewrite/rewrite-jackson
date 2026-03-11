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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.text.PlainTextVisitor;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class LombokJacksonizedConfig extends ScanningRecipe<LombokJacksonizedConfig.Accumulator> {

    private static final String JACKSONIZED = "lombok.extern.jackson.Jacksonized";
    private static final String CONFIG_KEY = "lombok.jacksonized.jacksonVersion";
    private static final String CONFIG_LINE = CONFIG_KEY + " = 3";

    String displayName = "Update `lombok.config` for Jackson 3 compatibility";

    String description = "When `@Jacksonized` is used, Lombok generates Jackson annotations. " +
            "By default it generates Jackson 2.x annotations. This recipe adds " +
            "`lombok.jacksonized.jacksonVersion = 3` to `lombok.config` so Lombok generates " +
            "Jackson 3 compatible annotations.";

    static class Accumulator {
        final AtomicBoolean hasJacksonized = new AtomicBoolean(false);
        final AtomicBoolean hasLombokConfig = new AtomicBoolean(false);
        final AtomicBoolean alreadyConfigured = new AtomicBoolean(false);
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@SuppressWarnings("NullableProblems") Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile sourceFile = (SourceFile) tree;
                if (tree instanceof PlainText && sourceFile.getSourcePath().toString().equals("lombok.config")) {
                    acc.hasLombokConfig.set(true);
                    if (((PlainText) tree).getText().contains(CONFIG_KEY)) {
                        acc.alreadyConfigured.set(true);
                    }
                }

                if (tree instanceof org.openrewrite.java.tree.JavaSourceFile &&
                    new Annotated.Matcher("@" + JACKSONIZED).lower(sourceFile).findFirst().isPresent()) {
                    acc.hasJacksonized.set(true);
                }

                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        if (!acc.hasJacksonized.get() || acc.hasLombokConfig.get()) {
            return Collections.emptyList();
        }

        return PlainTextParser.builder().build()
                .parse("")
                .map(s -> (SourceFile) s.withSourcePath(Paths.get("lombok.config")))
                .collect(Collectors.toList());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (!acc.hasJacksonized.get() || acc.alreadyConfigured.get()) {
            return TreeVisitor.noop();
        }

        return Preconditions.check(
                new FindSourceFiles("lombok.config").getVisitor(),
                new PlainTextVisitor<ExecutionContext>() {
                    @Override
                    public PlainText visitText(PlainText text, ExecutionContext ctx) {
                        String content = text.getText();
                        if (!content.endsWith("\n")) {
                            content += "\n";
                        }
                        return text.withText(content + CONFIG_LINE);
                    }
                }
        );
    }
}
