plugins {
    alias(libs.plugins.hilt) apply(false)
    alias(libs.plugins.kotlin.ksp) apply(false)
    id("io.ktor.plugin") version "2.3.12"

}

buildscript {
    val isFullBuild by extra {
        gradle.startParameter.taskNames.none { task -> task.contains("foss", ignoreCase = true) }
    }

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
        classpath("com.github.caoimhebyrne:KDiscordIPC:0.2.2")
        classpath("io.ktor:ktor-server-core:2.3.12")
        classpath("io.ktor:ktor-server-netty:2.3.12")


    }
}

tasks.register<Delete>("Clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.add("-P")
                    freeCompilerArgs.add("plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${project.layout.buildDirectory}/compose_metrics")
                }
            }
        }
    }
}
