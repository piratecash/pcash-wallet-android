import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isMacOs = System.getProperty("os.name").startsWith("Mac")
val applicationName = "P.CASH"
val macIconFile = project.file("icons/p-cash.icns")

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
            packageName = applicationName
            macOS {
                iconFile.set(macIconFile)
            }
            windows {
                iconFile.set(project.file("icons/p-cash.ico"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (isMacOs && name == "hotRun") {
        jvmArgs("-Xdock:name=$applicationName")
        jvmArgs("-Xdock:icon=${macIconFile.absolutePath}")
    }
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        if (isMacOs) {
            jvmArgs("-Xdock:name=$applicationName")
        }
    }
}
