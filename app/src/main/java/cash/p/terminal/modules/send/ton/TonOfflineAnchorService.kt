package cash.p.terminal.modules.send.ton

import cash.p.terminal.R
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTonSignRequest
import cash.p.terminal.core.TonOfflineAnchor
import cash.p.terminal.modules.pin.core.UptimeProvider
import io.horizontalsystems.tonkit.FriendlyAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the online-captured anchor (account seqno + network time) that offline
 * signing is built from, and enforces its one-shot nature: a seqno is signed at
 * most once, a consumed seqno is never re-anchored, and only a strictly newer
 * seqno replaces the current anchor.
 */
class TonOfflineAnchorService(
    private val adapter: ISendTonAdapter,
    private val uptimeProvider: UptimeProvider,
) {
    private data class Anchored(
        val anchor: TonOfflineAnchor,
        val elapsedAtFetch: Long,
    )

    private data class AnchorState(
        val anchored: Anchored?,
        val lastConsumedSeqno: Int?,
        val ownershipGeneration: Int,
    )

    private val state = AtomicReference(
        AnchorState(anchored = null, lastConsumedSeqno = null, ownershipGeneration = 0)
    )

    /** Captured by a sign attempt before it starts; see [consumeAnchor]. */
    val ownershipGeneration: Int
        get() = state.get().ownershipGeneration

    /**
     * Fetches a fresh anchor. Transport failures are retried while [connected]
     * returns true and no usable anchor exists; permanent (API/decoding) failures
     * and a successful fetch that is not strictly newer than what is already
     * known both complete without retrying.
     */
    suspend fun refreshAnchor(connected: () -> Boolean) {
        while (connected()) {
            val anchor = try {
                adapter.fetchOfflineAnchor()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                null
            } catch (e: Throwable) {
                // Non-transport failure won't heal on its own — the next reconnect retries.
                return
            }
            if (anchor != null) {
                install(anchor, elapsedAtFetch = uptimeProvider.uptime)
                return
            }
            // A seqno-0 anchor only reports "wallet not deployed" — keep retrying past it.
            if ((state.get().anchored?.anchor?.seqno ?: 0) > 0) return
            delay(ANCHOR_RETRY_DELAY_MS)
        }
    }

    /**
     * Builds a sign request strictly from data captured online. Fails closed:
     * no anchor, an undeployed wallet, or a fee quote that no longer matches
     * the current inputs each abort signing with a localized error.
     */
    fun buildSignRequest(
        amount: BigDecimal,
        address: FriendlyAddress,
        memo: String?,
        quote: TonFeeQuote?,
    ): OfflineTonSignRequest {
        val anchored = requireUsableAnchor()
        if (quote == null || !quote.matches(amount, address, memo)) {
            throw LocalizedException(R.string.send_error_fee_rate_unavailable)
        }
        val elapsedSeconds = (uptimeProvider.uptime - anchored.elapsedAtFetch) / MILLIS_IN_SECOND
        return OfflineTonSignRequest(
            amount = amount,
            address = address,
            memo = memo,
            seqno = anchored.anchor.seqno,
            validUntil = anchored.anchor.unixTimeSeconds + elapsedSeconds + OFFLINE_TX_TTL_SECONDS,
            fee = quote.fee,
        )
    }

    private fun requireUsableAnchor(): Anchored {
        val anchored = state.get().anchored
            ?: throw LocalizedException(R.string.offline_transaction_anchor_required)
        if (anchored.anchor.seqno == 0) {
            // Wallet contracts reject external messages until the wallet is deployed.
            throw LocalizedException(R.string.offline_sign_error_wallet_not_deployed)
        }
        return anchored
    }

    /**
     * Burns the anchor whose seqno was just signed; a consumed seqno never comes back.
     * [ownership] is the generation the attempt captured before signing: if the user
     * abandoned that attempt ([invalidateOwnership] bumped the generation), the burn
     * is skipped — the generation check and the burn are a single CAS, so an abandoned
     * attempt can never take the seqno from the replacement that now owns it.
     */
    fun consumeAnchor(signedSeqno: Int, ownership: Int) {
        while (true) {
            val current = state.get()
            if (current.ownershipGeneration != ownership) return
            val next = current.copy(
                anchored = current.anchored?.takeUnless { it.anchor.seqno == signedSeqno },
                lastConsumedSeqno = maxOf(current.lastConsumedSeqno ?: signedSeqno, signedSeqno),
            )
            if (next == current || state.compareAndSet(current, next)) return
        }
    }

    /** Detaches any in-flight sign attempt from the anchor: its late [consumeAnchor] becomes a no-op. */
    fun invalidateOwnership() {
        state.updateAndGet { it.copy(ownershipGeneration = it.ownershipGeneration + 1) }
    }

    // Accept only a strictly newer seqno: never resurrect a consumed one and never
    // let a slow concurrent fetch overwrite a fresher anchor.
    private fun install(anchor: TonOfflineAnchor, elapsedAtFetch: Long) {
        while (true) {
            val current = state.get()
            val consumed = current.lastConsumedSeqno
            if (consumed != null && anchor.seqno <= consumed) return
            val existing = current.anchored
            if (existing != null && anchor.seqno <= existing.anchor.seqno) return
            val next = current.copy(anchored = Anchored(anchor, elapsedAtFetch))
            if (state.compareAndSet(current, next)) return
        }
    }

    companion object {
        private const val MILLIS_IN_SECOND = 1000L
        private const val ANCHOR_RETRY_DELAY_MS = 3_000L

        // Long TTL so a transaction signed offline stays broadcastable for hours.
        private const val OFFLINE_TX_TTL_SECONDS = 6 * 60 * 60L
    }
}
