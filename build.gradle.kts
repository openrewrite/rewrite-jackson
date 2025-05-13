plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "OpenRewrite recipes for modernizing, upgrading, applying best practices for the fasterxml Jackson serialization/deserialization libraries "

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.assertj:assertj-core:latest.release")
    runtimeOnly("org.openrewrite:rewrite-java-17")


    annotationProcessor("org.openrewrite:rewrite-templating:$rewriteVersion")
    implementation("org.openrewrite:rewrite-templating:$rewriteVersion")
    // The `@BeforeTemplate` and `@AfterTemplate` annotations are needed for refaster style recipes
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-maven")

    runtimeOnly("ch.qos.logback:logback-classic:1.2.+")
}

recipeDependencies {
    parserClasspath("com.fasterxml.jackson.core:jackson-annotations:2.19.0")
    parserClasspath("com.fasterxml.jackson.core:jackson-core:2.19.0")
    parserClasspath("com.fasterxml.jackson.core:jackson-databind:2.19.0")
}

signing {
    // To enable signing have your CI workflow set the "signingKey" and "signingPassword" Gradle project properties
    isRequired = false
}

// Use maven-style "SNAPSHOT" versioning for non-release builds
configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")
        }
    }
}

