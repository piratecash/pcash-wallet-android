package cash.p.terminal.trezor.signer

import io.horizontalsystems.bitcoincore.AbstractKit

/**
 * Supplies the raw hex of a previous transaction so a Trezor can verify the UTXO an input spends.
 * The USB firmware requests each spent input's prev-tx during signing; the deeplink flow never needed
 * this because Trezor Suite fetched it itself.
 *
 * [hash] is the display-order (reversed) txid, matching what [AbstractKit.getRawTransaction] expects.
 */
interface BtcPreviousTransactionProvider {
    fun getRawTransaction(hash: String): String?
}

/** [BtcPreviousTransactionProvider] backed by the wallet's own bitcoin-kit storage. */
class KitBtcPreviousTransactionProvider(
    private val kit: AbstractKit
) : BtcPreviousTransactionProvider {
    override fun getRawTransaction(hash: String): String? = kit.getRawTransaction(hash)
}
