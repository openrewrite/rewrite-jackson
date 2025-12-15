plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "OpenRewrite recipes for modernizing, upgrading, applying best practices for the fasterxml Jackson serialization/deserialization libraries"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-properties")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:${rewriteVersion}")
    implementation("org.assertj:assertj-core:latest.release")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")
    // The `@BeforeTemplate` and `@AfterTemplate` annotations are needed for refaster style recipes
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-maven")
    testImplementation("org.openrewrite.gradle.tooling:model:${rewriteVersion}")

    testRuntimeOnly(gradleApi())
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("org.codehaus.jackson:jackson-core-asl:latest.release")
    testRuntimeOnly("org.codehaus.jackson:jackson-mapper-asl:latest.release")
    testRuntimeOnly("org.codehaus.jackson:jackson-xc:latest.release")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-avro:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-smile:2.17.3")
    testRuntimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-ion:2.17.3")
}

recipeDependencies {
    parserClasspath("com.fasterxml.jackson.core:jackson-annotations:2.19.2")
    parserClasspath("com.fasterxml.jackson.core:jackson-core:2.19.2")
    parserClasspath("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.3")
    parserClasspath("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.3")

    parserClasspath("tools.jackson.core:jackson-core:3.+")
    parserClasspath("tools.jackson.core:jackson-databind:3.+")
}
