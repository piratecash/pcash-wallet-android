package cash.p.terminal.core.utils

import android.net.Uri
import cash.p.terminal.core.IAddressParser
import cash.p.terminal.core.factories.removeScheme
import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.supported
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.AddressUri
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import java.net.URI
import java.net.URLDecoder


class AddressUriParser(
    private val blockchainType: BlockchainType?,
    private val tokenType: TokenType?
) : IAddressParser {
    private fun pair(type: BlockchainType, s2: String?): String {
        val prefix = if (type.removeScheme) null else type.uriScheme
        return listOfNotNull(prefix, s2).joinToString(separator = ":")
    }

    private fun fullAddress(
        scheme: String,
        address: String,
        uriBlockchainUid: String? = null
    ): String {
        // there is no explicit indication of the blockchain in the uri. We use the rules of the blockchain parser
        uriBlockchainUid ?: run {
            // if has blockchainType check if needed prefix
            blockchainType?.let {
                return pair(it, address)
            }

            // if there is no any blockchainTypes supported, try to determine
            BlockchainType.supported.firstOrNull { it.uriScheme == scheme }?.let {
                return pair(it, address)
            }

            return address
        }

        // There is a blockchain Uid in the uri. We use it to create an address
        return pair(BlockchainType.fromUid(uriBlockchainUid), address)
    }

    override fun parse(addressUri: String): AddressUriResult {
        val source = parseSource(addressUri) ?: return AddressUriResult.WrongUri
        val scheme = source.scheme ?: contextualScheme() ?: return AddressUriResult.NoUri
        contextResult(scheme)?.let { return it }

        val parameters = parseQueryParameters(source.parameterPart)
        if (parameters.isUnsafeFor(scheme)) return AddressUriResult.WrongUri

        return createAddressUri(scheme, source.address, parameters)
    }

    private fun parseSource(addressUri: String): Source? {
        val (addressPart, parameterPart) = addressUri.splitParameterSuffix()
        val uri = tryOrNull { URI(addressPart) } ?: return null
        val scheme = uri.scheme?.lowercase()
        val address = uri.schemeSpecificPart.removeSingleLeadingSlash(scheme != null)

        return Source(scheme, address, parameterPart)
    }

    private fun contextualScheme(): String? = blockchainType?.uriScheme ?: blockchainType?.uid

    private fun contextResult(scheme: String): AddressUriResult? {
        if (blockchainType != null && !scheme.equals(contextualScheme(), ignoreCase = true)) {
            return AddressUriResult.InvalidBlockchainType
        }

        return solanaRequestResult(scheme)
    }

    private fun List<Pair<String, String?>>.isUnsafeFor(scheme: String): Boolean {
        return hasUnsupportedRequiredParameter(scheme) || hasIndexedZcashPayment(scheme) ||
                hasSolanaSplToken(scheme)
    }

    private fun List<Pair<String, String?>>.hasSolanaSplToken(scheme: String): Boolean {
        return scheme == SOLANA_SCHEME && any { (key, _) ->
            key.equals(SOLANA_SPL_TOKEN_PARAMETER, ignoreCase = true)
        }
    }

    private fun createAddressUri(
        scheme: String,
        address: String,
        parameters: List<Pair<String, String?>>
    ): AddressUriResult {
        val parsedUri = AddressUri(scheme)
        parsedUri.populate(scheme, parameters)
        uidMismatchResult(parsedUri)?.let { return it }
        parsedUri.address = fullAddress(scheme, address, parsedUri.value(AddressUri.Field.BlockchainUid))
        return AddressUriResult.Uri(parsedUri)
    }

    private fun AddressUri.populate(scheme: String, parameters: List<Pair<String, String?>>) {
        for ((key, value) in parameters) {
            value ?: continue
            fieldFor(scheme, key)?.let { this.parameters[it] = value }
                ?: unhandledParameters.put(key, value)
        }
    }

    private fun uidMismatchResult(addressUri: AddressUri): AddressUriResult? {
        addressUri.value<String>(AddressUri.Field.BlockchainUid)?.let { uid ->
            if (blockchainType?.uid != null && blockchainType.uid != uid) {
                return AddressUriResult.InvalidBlockchainType
            }
        }
        addressUri.value<String>(AddressUri.Field.TokenUid)?.let { uid ->
            if (tokenType?.id != null && tokenType.id.lowercase() != uid.lowercase()) {
                return AddressUriResult.InvalidTokenType
            }
        }
        return null
    }

    private fun solanaRequestResult(scheme: String): AddressUriResult? {
        if (scheme != SOLANA_SCHEME) return null

        return when {
            blockchainType != BlockchainType.Solana -> AddressUriResult.InvalidBlockchainType
            tokenType != TokenType.Native -> AddressUriResult.InvalidTokenType
            else -> null
        }
    }

    private fun fieldFor(scheme: String, key: String): AddressUri.Field? {
        val normalizedKey = if (scheme == BITCOIN_SCHEME) key.lowercase() else key
        return AddressUri.Field.values().firstOrNull { it.value == normalizedKey }
    }

    private fun List<Pair<String, String?>>.hasUnsupportedRequiredParameter(scheme: String): Boolean {
        return scheme in REQUIRED_PARAMETER_SCHEMES && any { (key, _) ->
            key.startsWith(REQUIRED_PARAMETER_PREFIX, ignoreCase = true)
        }
    }

    private fun List<Pair<String, String?>>.hasIndexedZcashPayment(scheme: String): Boolean {
        return scheme == ZCASH_SCHEME && any { (key, _) -> key.isIndexedZcashPaymentParameter() }
    }

    private fun String.isIndexedZcashPaymentParameter(): Boolean {
        val separatorIndex = lastIndexOf('.')
        val index = substring(separatorIndex + 1)
        if (separatorIndex == -1 || index.isEmpty() || index.any { it !in '0'..'9' }) return false

        return substring(0, separatorIndex) in ZCASH_PAYMENT_PARAMETERS
    }

    private fun parseQueryParameters(query: String?): List<Pair<String, String?>> {
        return query.orEmpty()
            .split("&")
            .mapNotNull(::parseParameter)
    }

    private fun parseParameter(fragment: String): Pair<String, String?>? {
        val delimiterIndex = fragment.indexOf('=')
        val rawKey = fragment.substring(0, delimiterIndex.takeIf { it >= 0 } ?: fragment.length)
        if (rawKey.isEmpty()) return null

        val key = rawKey.decodeUriComponent() ?: return null
        val value = delimiterIndex.takeIf { it >= 0 }
            ?.let { fragment.substring(it + 1).decodeUriComponent() }

        return key to value
    }

    private fun String.splitParameterSuffix(): Pair<String, String?> {
        val queryStart = indexOfAny(charArrayOf('?', '&'))
        return if (queryStart == -1) {
            this to null
        } else {
            substring(0, queryStart) to substring(queryStart + 1)
        }
    }

    private fun String.removeSingleLeadingSlash(hasScheme: Boolean): String {
        return if (hasScheme) removePrefix("/") else this
    }

    private fun String.decodeUriComponent(): String? {
        if (!hasValidPercentEncoding()) return null

        return tryOrNull { URLDecoder.decode(replace("+", "%2B"), Charsets.UTF_8.name()) }
    }

    private fun String.hasValidPercentEncoding(): Boolean {
        return split('%').drop(1).all { it.startsWithHexByte() }
    }

    private fun String.startsWithHexByte(): Boolean {
        if (length < 2) return false

        return this[0].digitToIntOrNull(16) != null && this[1].digitToIntOrNull(16) != null
    }

    fun uri(addressUri: AddressUri): String {
        val uriBuilder = Uri.Builder()
            .scheme(blockchainType?.uriScheme)
            .path(
                addressUri.address.removePrefix(blockchainType?.uriScheme ?: "").removePrefix(":")
            )

        for ((key, value) in addressUri.parameters) {
            uriBuilder.appendQueryParameter(key.value, value)
        }

        for ((key, value) in addressUri.unhandledParameters) {
            uriBuilder.appendQueryParameter(key, value)
        }

        return uriBuilder
            .build()
            .toString()
            .replace("/", "")
            .replace("%3A", ":")
    }

    companion object {
        private const val BITCOIN_SCHEME = "bitcoin"
        private const val ZCASH_SCHEME = "zcash"
        private const val SOLANA_SCHEME = "solana"
        private const val SOLANA_SPL_TOKEN_PARAMETER = "spl-token"
        private const val REQUIRED_PARAMETER_PREFIX = "req-"

        private val REQUIRED_PARAMETER_SCHEMES = setOf(BITCOIN_SCHEME, ZCASH_SCHEME)
        private val ZCASH_PAYMENT_PARAMETERS = setOf("address", "amount", "memo", "label", "message")

        fun hasUriPrefix(text: String): Boolean {
            return text.split(":").size > 1
        }

        fun addressUri(text: String): AddressUri? {
            if (hasUriPrefix(text)) {
                val abstractUriParse = AddressUriParser(null, null)
                return when (val result = abstractUriParse.parse(text)) {
                    is AddressUriResult.Uri -> {
                        if (BlockchainType.supported.map { it.uriScheme }
                                .contains(result.addressUri.scheme))
                            result.addressUri
                        else
                            null
                    }

                    else -> null
                }
            }
            return null
        }
    }

    private data class Source(
        val scheme: String?,
        val address: String,
        val parameterPart: String?
    )
}

sealed class AddressUriResult {
    object WrongUri : AddressUriResult()
    object InvalidBlockchainType : AddressUriResult()
    object InvalidTokenType : AddressUriResult()
    object NoUri : AddressUriResult()
    class Uri(val addressUri: AddressUri) : AddressUriResult()
}
