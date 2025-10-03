package cash.p.terminal.modules.multiswap.providers

import org.ton.bigint.BigInt
import org.ton.bigint.bitLength
import org.ton.bigint.sign
import org.ton.bigint.toBigInt
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell

private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

fun CellBuilder.storeVarUint(value: BigInt, headerBits: Int): CellBuilder = apply {
    require(headerBits >= 0 && headerBits <= 32) { "invalid headerBits: $headerBits" }
    require(value.sign >= 0) { "value is negative: $value" }

    if (value == 0.toBigInt()) {
        storeUInt(0, headerBits)
        return@apply
    }

    val sizeBytes = ceilDiv(value.bitLength, 8)
    val sizeBits = sizeBytes * 8
    storeUInt(sizeBytes, headerBits)
    storeUInt(value, sizeBits)
}

fun CellBuilder.storeVarUint(value: Int, headerBits: Int): CellBuilder =
    storeVarUint(value.toBigInt(), headerBits)

/** Coins = VarUInteger 16 (как writeCoins → writeVarUint(amount, 4) в @ton/core) */
fun CellBuilder.storeCoins(amount: BigInt): CellBuilder =
    storeVarUint(amount, 4)

fun CellBuilder.storeCoins(amount: Int): CellBuilder =
    storeVarUint(amount, 4)

fun CellBuilder.storeAddress(address: AddrStd?): CellBuilder = apply {
    when (address) {
        null -> {
            // Empty address: 00
            storeUInt(0, 2)
        }

        else -> {
            // Internal: 10
            storeUInt(2, 2)
            // No anycast: 0
            storeUInt(0, 1)
            // workchain: int8
            storeInt(address.workchainId, 8)
            storeBits(address.address)
        }
    }
}

internal fun buildStonfiSwapTonToJettonPayloadV2(
    tonAmount: BigInt,
    tokenWallet: AddrStd,
    receiver: AddrStd,
    minOut: BigInt,
    refundAddress: AddrStd,
    excessesAddress: AddrStd = refundAddress,
    deadline: Long = (System.currentTimeMillis() / 1000L) + (15 * 60), // +15 min
    refFee: Int? = 10,
    queryId: Long = System.currentTimeMillis(),
    fwdGas: BigInt = 0.toBigInt(),
    referralAddress: AddrStd? = null
): Cell {
    return buildCell {
        storeUInt(0x01f3835d, 32)
        storeUInt(queryId, 64)
        storeCoins(tonAmount)
        storeAddress(refundAddress)
        storeBit(true)
        storeRef(buildCell {
            // op
            storeUInt(0x6664de2a, 32)

            // ask_jetton_wallet
            storeAddress(tokenWallet)

            // refund_address
            storeAddress(refundAddress)

            // excesses_address
            storeAddress(excessesAddress)

            // deadline
            storeUInt(deadline, 64)

            // cross_swap_body
            storeRef(
                buildCell {
                    storeCoins(minOut)
                    storeAddress(receiver)
                    storeCoins(fwdGas)
                    storeBit(false) // dexCustomPayload
                    storeCoins(0)   // refund_fwd_gas
                    storeBit(false) // refund_payload
                    storeUInt(refFee ?: 0, 16)
                    storeAddress(referralAddress)
                }
            )
        }
        )
    }
}

internal fun buildStonfiSwapTonToJettonTransferV1(
    amount: BigInt,
    routerAddress: AddrStd,
    routerJettonWallet: AddrStd,
    receiver: AddrStd,
    minOut: BigInt,
    referralAddress: AddrStd?,
    forwardTonAmount: BigInt,
    queryId: Long = System.currentTimeMillis(),
): Cell {
    return buildCell {
        storeUInt(0x0f8a7ea5, 32)                  // jetton_transfer opcode
        storeUInt(queryId, 64)
        storeCoins(amount)                         // amount
        storeAddress(routerAddress)                // destination = router
        storeAddress(null)                         // response_destination (absent)
        storeBit(false)                            // custom_payload (absent)
        storeCoins(forwardTonAmount)               // forward_ton_amount
        storeBit(true)                             // forward_payload present
        storeRef(buildCell {
            storeUInt(0x25938561, 32)   // swap opcode v1
            storeAddress(routerJettonWallet)  // token_wallet1
            storeCoins(minOut)          // min_out
            storeAddress(receiver)      // to_address
            referralAddress?.let {
                storeBit(true)          // has_ref = 1
                storeAddress(it)
            } ?: storeBit(false)        // has_ref = 0
        })                         // forward_payload
    }
}

internal fun buildJettonToTonPayloadV2(
    amount: BigInt,
    router: AddrStd,
    ptonWallet: AddrStd,
    refundAddress: AddrStd,
    excessesAddress: AddrStd = refundAddress,
    minOut: BigInt,
    forwardGas: BigInt,
    queryId: Long = System.currentTimeMillis(),
    deadline: Long = (System.currentTimeMillis() / 1000L) + (15 * 60), // +15 min
    refFee: Int = 10,               // 0.1%
    referralAddress: AddrStd?
): Cell {

    // JettonTransfer → router
    return buildCell {
        storeUInt(0x0f8a7ea5, 32)         // opcode jetton_transfer
        storeUInt(queryId, 64)
        storeCoins(amount)
        storeAddress(router)              // destination = router v2
        storeAddress(refundAddress)              // response_destination
        storeBit(false)
        storeCoins(forwardGas)            // forward_ton_amount
        storeBit(true)
        storeRef(
            buildCell {
                // STON.fi SwapV2
                storeUInt(0x6664de2a, 32)         // opcode swap
                storeAddress(ptonWallet)
                storeAddress(refundAddress)
                storeAddress(excessesAddress)
                storeUInt(deadline, 64) // deadline (+15min)

                // cross_swap_body
                storeRef(
                    buildCell {
                        storeCoins(minOut)
                        storeAddress(refundAddress)
                        storeCoins(0)        // fwd_gas
                        storeBit(false)               // dexCustomPayload
                        storeCoins(0)                 // refund_fwd_gas
                        storeBit(false)               // refund_payload
                        storeUInt(refFee, 16)         // referral_fee
                        storeAddress(referralAddress)
                    }
                )
            }
        )             // forward_payload
    }
}

internal fun buildJettonToTonPayloadV1(
    amount: BigInt,
    router: AddrStd,
    routerPtonWallet: AddrStd,
    refundAddress: AddrStd,
    minOut: BigInt,
    queryId: Long = System.currentTimeMillis(),
    referralAddress: AddrStd? = null,
    forwardTonAmount: BigInt
): Cell {
    return buildCell {
        storeUInt(0x0f8a7ea5, 32)     // jetton_transfer
        storeUInt(queryId, 64)
        storeCoins(amount)            // amount jetton_in
        storeAddress(router)          // dest = router v1
        storeAddress(refundAddress)   // response_destination
        storeBit(false)               // no custom_payload
        storeCoins(forwardTonAmount)  // forward_ton_amount
        storeBit(true)                // forward_payload present
        storeRef(
            buildCell {
                storeUInt(0x25938561, 32)     // swap opcode
                storeAddress(routerPtonWallet)// token_wallet1 (router wallet TON/pTON)
                storeCoins(minOut)            // min_out
                storeAddress(refundAddress)   // to_address
                referralAddress?.let {
                    storeBit(true)            // has_ref
                    storeAddress(it)
                } ?: storeBit(false)
            }
        )
    }
}
