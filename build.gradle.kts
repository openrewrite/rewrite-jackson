plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Jackson Migration"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    testImplementation("com.fasterxml.jackson.core:jackson-databind:latest.release")
    testImplementation("org.codehaus.jackson:jackson-core-asl:latest.release")
    testImplementation("org.codehaus.jackson:jackson-mapper-asl:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")
}

recipeDependencies {
    parserClasspath("com.fasterxml.jackson.core:jackson-annotations:latest.release")
}
