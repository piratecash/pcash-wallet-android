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

internal fun buildStonfiSwapTonToJettonPayload(
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
    val forwardPayload = buildCell {
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

    return buildCell {
        storeUInt(0x01f3835d, 32)
        storeUInt(queryId, 64)
        storeCoins(tonAmount)
        storeAddress(refundAddress)
        if (forwardPayload != null) {
            storeBit(true)
            storeRef(forwardPayload)
        } else {
            storeBit(false)
        }
    }
}

internal fun buildJettonToTonPayload(
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

    val swapPayload = buildCell {
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
        storeRef(swapPayload)             // forward_payload
    }
}
