import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "cash.p.terminal.desktop.MainKt"

        nativeDistributions {
            packageName = "p.cash"
        }
    }
}
