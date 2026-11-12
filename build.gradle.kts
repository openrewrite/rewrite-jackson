plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "OpenRewrite recipes for modernizing, upgrading, applying best practices for the fasterxml Jackson serialization/deserialization libraries"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")


    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")

    testImplementation("org.openrewrite:rewrite-kotlin")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")

    testRuntimeOnly(gradleApi())
}

// testParserClasspath's Jackson 3 dataformat jars build on parserClasspath's core and databind, so they share a version
val jackson3 = "3.2.4"

recipeDependencies {
    parserClasspath("com.fasterxml.jackson.core:jackson-annotations:2.19.2")
    parserClasspath("com.fasterxml.jackson.core:jackson-core:2.19.2")
    parserClasspath("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    parserClasspath("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")

    // Needed by the template parsers of MigrateFactorySettersToBuilder and MigrateMapperSettersToBuilder to resolve
    // the format-aligned factory and mapper builders.
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-avro:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-ion:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-smile:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")

    parserClasspath("tools.jackson.core:jackson-core:$jackson3")
    parserClasspath("tools.jackson.core:jackson-databind:$jackson3")

    parserClasspath("org.codehaus.jackson:jackson-core-asl:1.9.13")
    parserClasspath("org.codehaus.jackson:jackson-mapper-asl:1.9.13")

    testParserClasspath("com.fasterxml.jackson.datatype:jackson-datatype-joda:2.19.2")
    testParserClasspath("com.fasterxml.jackson.module:jackson-module-afterburner:2.19.2")
    testParserClasspath("org.codehaus.jackson:jackson-xc:1.9.13")
    testParserClasspath("tools.jackson.dataformat:jackson-dataformat-cbor:$jackson3")
    testParserClasspath("tools.jackson.dataformat:jackson-dataformat-smile:$jackson3")
}

tasks.withType<Test>().configureEach {
    // Fails a test whose recipe gives the code it writes other types than a parser gives that code
    systemProperty("org.openrewrite.test.parserTypedTrees", true)
}
