package cash.p.terminal.strings.helpers


fun String.shorten(): String {
    val prefixes = listOf("0x", "bc", "bnb", "ltc", "bitcoincash:", "ecash:")

    var prefix = ""
    for (p in prefixes) {
        if (this.startsWith(p)) {
            prefix = p
            break
        }
    }

    val withoutPrefix = this.removePrefix(prefix)

    val characters = 4
    return if (withoutPrefix.length > characters * 2)
        prefix + withoutPrefix.take(characters) + "..." + withoutPrefix.takeLast(characters)
    else
        this
}

fun String.toMasked(): String {
    return when {
        isEmpty() -> ""
        length ==1 -> "*"
        length < 10 -> "**"
        else -> "${first()}***${last()}"
    }
}

fun List<String>.toMasked(): String {
    return when (size) {
        0 -> ""
        1 -> this[0].toMasked()
        else -> "${first().first()}****${last().last()}"
    }
}