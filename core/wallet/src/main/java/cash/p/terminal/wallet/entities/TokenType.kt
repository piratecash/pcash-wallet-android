package cash.p.terminal.wallet.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class TokenType : Parcelable {

    enum class Derivation {
        Bip44,
        Bip49,
        Bip84,
        Bip86,
    }

    enum class AddressType {
        Type0,
        Type145,
    }

    enum class AddressSpecType {
        Shielded,
        Transparent,
        Unified,
    }

    @Parcelize
    object Native : TokenType()

    @Parcelize
    data class Derived(val derivation: Derivation) : TokenType()

    @Parcelize
    data class AddressTyped(val type: AddressType) : TokenType()

    @Parcelize
    data class AddressSpecTyped(val type: AddressSpecType) : TokenType()

    @Parcelize
    data class Eip20(val address: String) : TokenType()

    @Parcelize
    data class Spl(val address: String) : TokenType()

    @Parcelize
    data class Jetton(val address: String) : TokenType()

    @Parcelize
    data class Asset(val code: String, val issuer: String) : TokenType()

    @Parcelize
    data class Unsupported(val type: String, val reference: String) : TokenType()

    val id: String
        get() {
            val parts = when (this) {
                Native -> listOf("native")
                is Eip20 -> listOf("eip20", address)
                is Spl -> listOf("spl", address)
                is Jetton -> listOf("the-open-network", address)
                is Asset -> listOf("stellar", "$code-$issuer")
                is AddressTyped -> listOf("address_type", type.name.lowercase())
                is AddressSpecTyped -> listOf("address_spec_type", type.name.lowercase())
                is Derived -> listOf("derived", derivation.name.lowercase())
                is Unsupported -> if (reference.isNotBlank()) {
                    listOf("unsupported", type, reference)
                } else {
                    listOf("unsupported", type)
                }
            }
            return parts.joinToString(":")
        }

    val values: Value
        get() = when (this) {
            is Native -> Value("native", "")
            is Eip20 -> Value("eip20", address)
            is Spl -> Value("spl", address)
            is Jetton -> Value("the-open-network", address)
            is Asset -> Value("stellar", "$code-$issuer")
            is AddressTyped -> Value("address_type", type.name)
            is AddressSpecTyped -> Value("address_spec_type", type.name)
            is Derived -> Value("derived", derivation.name)
            is Unsupported -> Value(type, reference)
        }

    data class Value(
        val type: String,
        val reference: String
    )

    companion object {

        fun fromType(type: String, reference: String = ""): TokenType {
            when (type) {
                "native" -> return Native

                "eip20" -> {
                    if (reference.isNotBlank()) {
                        return Eip20(reference)
                    }
                }

                "spl" -> {
                    if (reference.isNotBlank()) {
                        return Spl(reference)
                    }
                }

                "the-open-network" -> {
                    if (reference.isNotBlank()) {
                        return Jetton(reference)
                    }
                }

                "address_type" -> {
                    if (reference.isNotBlank()) {
                        try {
                            return AddressTyped(AddressType.valueOf(reference.lowercase().replaceFirstChar(Char::uppercase)))
                        } catch (e: IllegalArgumentException) {
                        }
                    }
                }

                "address_spec_type" -> {
                    if (reference.isNotBlank()) {
                        try {
                            return AddressSpecTyped(AddressSpecType.valueOf(reference.lowercase().replaceFirstChar(Char::uppercase)))
                        } catch (e: IllegalArgumentException) {
                        }
                    }
                }

                "derived" -> {
                    if (reference.isNotBlank()) {
                        try {
                            return Derived(Derivation.valueOf(reference.lowercase().replaceFirstChar(Char::uppercase)))
                        } catch (e: IllegalArgumentException) {
                        }
                    }
                }
            }

            return Unsupported(type, reference)
        }

        fun fromId(id: String): TokenType? {
            val chunks = id.split(":")
            val type = chunks[0]
            val reference = chunks.getOrNull(1) ?: ""

            return fromType(type, reference)
        }
    }

}
