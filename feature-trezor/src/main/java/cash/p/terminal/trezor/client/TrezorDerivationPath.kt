package cash.p.terminal.trezor.client

/**
 * Converts BIP-32 derivation paths between the string form used by the app/deeplink
 * (`"m/84'/0'/0'"`) and the list-of-ints form the [cash.p.terminal.trezorkit.client.ITrezorClient]
 * API expects (hardened segments carry the `0x80000000` bit).
 */
internal object TrezorDerivationPath {

    private const val HARDENED_BIT = 0x80000000.toInt()

    /** `"m/84'/0'/0'"` -> `[0x80000054, 0x80000000, 0x80000000]`. */
    fun parse(path: String): List<Int> =
        path.split("/")
            .filter { it.isNotEmpty() && it != "m" }
            .map { segment ->
                val hardened = segment.endsWith("'")
                val value = segment.trimEnd('\'').toInt()
                if (hardened) value or HARDENED_BIT else value
            }

    /** `[0x80000054, 0x80000000, 0x80000000]` -> `"m/84'/0'/0'"`. */
    fun format(path: List<Int>): String =
        buildString {
            append("m")
            for (segment in path) {
                append('/')
                if (segment and HARDENED_BIT != 0) {
                    append(segment and HARDENED_BIT.inv())
                    append('\'')
                } else {
                    append(segment)
                }
            }
        }
}
