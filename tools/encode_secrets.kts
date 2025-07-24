#!/usr/bin/env kotlin

import java.io.File
import java.util.Base64
import kotlin.experimental.xor

/**
 * How to use this tool:
 * 1) Put Api keys in local.properties file in the root of the project:
 * *    api.app_key=your_app_key
 * 2) Run kotlin ./tools/encode_secrets.kts
 */

val dot = "."
val z = File(dot)
val x = File(z, "local.properties")
val y = File(z, "app/build.gradle")
val w = File(z, "./core/network/src/commonMain/kotlin/cash/p/terminal/network/data/EncodedSecrets.kt")

val p = "pcash-public-password"

val a = readSecrets(x)
val b = grabId(y)
val c = twist(b)

if (a.isEmpty()) {
    println("⚠️  No secrets!")
    kotlin.system.exitProcess(0)
}

println("→ ID: $b")
println("→ Keys: ${a.keys.joinToString()}")

val d = a.mapValues { (k, v) -> scramble(v, p, c) }

writeOut(d, w)

println("✅ Done: ${w.absolutePath}")
d.forEach { (k, v) ->
    println("  » $k: ${v.take(16)}...")
}

fun readSecrets(f: File): Map<String, String> {
    if (!f.exists()) {
        println("✘ No local.properties found")
        return emptyMap()
    }

    return f.readLines()
        .asSequence()
        .filter { it.startsWith("api.") && it.contains("=") }
        .map { it.split("=", limit = 2) }
        .associate { it[0].removePrefix("api.").trim() to it[1].trim() }
}

fun grabId(f: File): String {
    val l = f.readText().lines()
    val t = l.firstOrNull { it.contains("applicationId") }
        ?: error("☠️ App ID missing")
    return t.split("\"")[1]
}

fun twist(s: String): String {
    val r = s.reversed()
    return r.drop(1).dropLast(1)
}

fun scramble(secret: String, key: String, salt: String): String {
    val comboKey = (key + salt).toByteArray()
    val input = secret.toByteArray()
    val encrypted = ByteArray(input.size) { i ->
        input[i] xor comboKey[i % comboKey.size]
    }
    return Base64.getEncoder().encodeToString(encrypted)
}

fun writeOut(data: Map<String, String>, target: File) {
    target.parentFile?.mkdirs()

    val result = buildString {
        appendLine("package cash.p.terminal.network.data")
        appendLine()
        appendLine("// generated file — do not edit manually")
        appendLine("object EncodedSecrets {")
        data.forEach { (k, v) ->
            val constName = k.uppercase()
            appendLine("    val $constName = \"$v\".decode()")
        }
        appendLine("}")
    }

    target.writeText(result)
}