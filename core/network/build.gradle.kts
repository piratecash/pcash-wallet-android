import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.devtools.ksp.get().pluginId)
}

kotlin {
    androidLibrary {
        namespace = "cash.p.terminal.network"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest { }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.kotlinx.serialization)
                implementation(libs.ktor.client.log)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)

                implementation(libs.room.runtime)
                implementation(libs.room.ktx)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.timber)
                implementation(libs.koin.android)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
