import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.gitlab.arturbosch.detekt")
}

// The default source set of the detekt task is src/main and src/test, so a multiplatform module
// is silently reported as NO-SOURCE unless its own directories are named.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/androidHostTest/kotlin")
}

kotlin {
    androidLibrary {
        namespace = "cash.p.terminal.qr.multipart"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest { }
    }

    sourceSets {
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(kotlin("test"))
            }
        }
    }
}
