package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.z.ecc.android.sdk.internal.jni.RustBackend
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import cash.z.ecc.android.sdk.tool.DerivationTool
import java.io.File

/** Single source of truth for which ZEC receiver an address spec maps to; `null` spec means Native. */
suspend fun <T> AddressSpecType?.selectZcashReceiver(
    sapling: suspend () -> T,
    transparent: suspend () -> T,
    unified: suspend () -> T,
): T = when (this) {
    AddressSpecType.Shielded, null -> sapling()
    AddressSpecType.Transparent -> transparent()
    AddressSpecType.Unified -> unified()
}

/**
 * [UNUSED_FILE] is never opened — the receiver JNI calls take only the address string. Never call
 * `RustBackend.clear()` here: it deletes the wallet database.
 */
class ZcashAddressDeriver {

    suspend fun deriveUnifiedAddressFromSeed(seed: ByteArray): String =
        DerivationTool.getInstance()
            .deriveUnifiedAddress(seed, ZcashNetwork.Mainnet, Zip32AccountIndex.new(ACCOUNT_INDEX))

    suspend fun deriveUnifiedAddressFromUfvk(ufvk: String): String =
        DerivationTool.getInstance().deriveUnifiedAddress(ufvk, ZcashNetwork.Mainnet)

    suspend fun saplingReceiver(unifiedAddress: String): String? =
        backend().getSaplingReceiver(unifiedAddress)

    suspend fun transparentReceiver(unifiedAddress: String): String? =
        backend().getTransparentReceiver(unifiedAddress)

    private suspend fun backend(): RustBackend = RustBackend.new(
        fsBlockDbRoot = UNUSED_FILE,
        dataDbFile = UNUSED_FILE,
        saplingSpendFile = UNUSED_FILE,
        saplingOutputFile = UNUSED_FILE,
        zcashNetworkId = ZcashNetwork.Mainnet.id,
    )

    private companion object {
        const val ACCOUNT_INDEX = 0L
        val UNUSED_FILE = File("zcash-address-deriver-never-opened")
    }
}
