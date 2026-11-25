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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.*;

public class FindJackson1TypesWithoutJackson2Equivalent extends Recipe {

    @Getter
    final String displayName = "Find Jackson Codehaus (legacy) types without a Jackson 2 equivalent";

    @Getter
    final String description = "Marks each reference to a Jackson 1 (`org.codehaus.jackson`) type that is left after the " +
                               "migration to Jackson 2, which are the types Jackson 2 dropped without a counterpart of the " +
                               "same name, and names the Jackson 2 mechanism to use in its place. Also marks each " +
                               "instance created of a class from a dependency that is still built on Jackson 1, which " +
                               "Jackson 2 can't use.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesType<>("org.codehaus.jackson..*", false), new UsesJackson1Subtype()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                String jackson1 = type == null || isJackson1(type) ? null : jackson1Supertype(type);
                if (jackson1 != null && n.getBody() == null) {
                    return SearchResult.found(n, type.getFullyQualifiedName() + " is built on Jackson 1 (it extends " + jackson1 +
                                                 "), and Jackson 2 can't use it: upgrade the dependency that declares it to a " +
                                                 "version built on Jackson 2, or replace it with a class of your own");
                }
                return n;
            }

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                return _import;
            }

            @Override
            public J.Package visitPackage(J.Package pkg, ExecutionContext ctx) {
                return pkg;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                String message = message(fieldAccess.getType(), fieldAccess.getSimpleName());
                if (message != null) {
                    return SearchResult.found(fieldAccess, message);
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                if (identifier.getFieldType() == null) {
                    String message = message(identifier.getType(), identifier.getSimpleName());
                    if (message != null) {
                        return SearchResult.found(identifier, message);
                    }
                }
                return identifier;
            }
        });
    }

    private static boolean isJackson1(JavaType.FullyQualified type) {
        return type.getFullyQualifiedName().startsWith("org.codehaus.jackson.");
    }

    /**
     * The Jackson 1 type a type extends or implements, if any. After the migration only a type declared in a dependency
     * still does, since the migration moved the supertypes of the types in the sources to Jackson 2.
     */
    private static @Nullable String jackson1Supertype(JavaType.FullyQualified type) {
        Set<String> seen = new HashSet<>();
        Deque<JavaType.FullyQualified> queue = new ArrayDeque<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            JavaType.FullyQualified next = queue.poll();
            if (!seen.add(next.getFullyQualifiedName())) {
                continue;
            }
            if (next != type && isJackson1(next)) {
                return next.getFullyQualifiedName();
            }
            if (next.getSupertype() != null) {
                queue.add(next.getSupertype());
            }
            queue.addAll(next.getInterfaces());
        }
        return null;
    }

    /**
     * Finds the source files that use a type built on Jackson 1, such as one from a dependency that still is.
     */
    private static class UsesJackson1Subtype extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                for (JavaType type : ((JavaSourceFile) tree).getTypesInUse().getTypesInUse()) {
                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                    if (fq != null && !isJackson1(fq) && jackson1Supertype(fq) != null) {
                        return SearchResult.found((JavaSourceFile) tree);
                    }
                }
                return (J) tree;
            }
            return super.visit(tree, ctx);
        }
    }

    /**
     * The message for a type name that refers to a Jackson 1 type, or null when it doesn't name one.
     */
    private static @Nullable String message(@Nullable JavaType type, String simpleName) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null || type instanceof JavaType.Parameterized || !fq.getFullyQualifiedName().startsWith("org.codehaus.jackson.") ||
            !simpleName.equals(fq.getClassName().substring(fq.getClassName().lastIndexOf('.') + 1))) {
            return null;
        }
        return advice(fq.getFullyQualifiedName());
    }

    static String advice(String type) {
        if (type.startsWith("org.codehaus.jackson.map.ext.Joda")) {
            return "Jackson 2 handles Joda types in jackson-datatype-joda: register com.fasterxml.jackson.datatype.joda.JodaModule " +
                   "with ObjectMapper.registerModule, or name its serializers in com.fasterxml.jackson.datatype.joda.ser and .deser";
        }
        if (type.equals("org.codehaus.jackson.map.ser.CustomSerializerFactory")) {
            return "Jackson 2 has no CustomSerializerFactory: add the serializers to a com.fasterxml.jackson.databind.module.SimpleModule " +
                   "with addSerializer and register it with ObjectMapper.registerModule";
        }
        if (type.equals("org.codehaus.jackson.map.deser.CustomDeserializerFactory")) {
            return "Jackson 2 has no CustomDeserializerFactory: add the deserializers to a com.fasterxml.jackson.databind.module.SimpleModule " +
                   "with addDeserializer and register it with ObjectMapper.registerModule";
        }
        if (type.startsWith("org.codehaus.jackson.xc.XmlAdapterJson")) {
            return "Jackson 2 applies @XmlJavaTypeAdapter through com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule; register it, " +
                   "or wrap the XmlAdapter in com.fasterxml.jackson.module.jaxb.AdapterConverter";
        }
        if (type.endsWith("SerializerFactory") || type.contains("SerializerFactory$") || type.contains(".map.ser.")) {
            return "Jackson 2 has no counterpart of this serializer factory API: customize serialization with a " +
                   "com.fasterxml.jackson.databind.Module, such as a SimpleModule or a BeanSerializerModifier";
        }
        if (type.endsWith("DeserializerFactory") || type.contains("DeserializerFactory$") || type.contains(".map.deser.")) {
            return "Jackson 2 has no counterpart of this deserializer factory API: customize deserialization with a " +
                   "com.fasterxml.jackson.databind.Module, such as a SimpleModule or a BeanDeserializerModifier";
        }
        if (type.contains("Config$Feature") || type.contains("Config")) {
            return "Jackson 2 has no counterpart of this configuration type: configure the ObjectMapper with " +
                   "com.fasterxml.jackson.databind.SerializationFeature, DeserializationFeature or MapperFeature";
        }
        return "Jackson 2 has no counterpart of this Jackson 1 type: replace it with the Jackson 2 mechanism for the same purpose, " +
               "usually a com.fasterxml.jackson.databind.Module";
    }
}
