package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

internal fun buildTestAccount(id: String) = Account(
    id = id,
    name = id,
    type = AccountType.EvmAddress("0x"),
    origin = AccountOrigin.Created,
    level = 0,
)

internal fun buildSwapProviderTransaction(
    provider: SwapProvider,
    transactionId: String,
) = SwapProviderTransaction(
    date = 1_000L,
    outgoingRecordUid = null,
    transactionId = transactionId,
    status = TransactionStatusEnum.NEW.name.lowercase(),
    provider = provider,
    coinUidIn = "coin-in",
    blockchainTypeIn = "ethereum",
    amountIn = BigDecimal.ONE,
    addressIn = "addr-in",
    coinUidOut = "coin-out",
    blockchainTypeOut = "bitcoin",
    amountOut = BigDecimal.TEN,
    addressOut = "addr-out",
)

internal fun TranslatableString?.formatArgFirst(): String? =
    (this as? TranslatableString.ResString)
        ?.formatArgs?.firstOrNull() as? String

internal fun mockZcashToken(addressSpec: TokenType.AddressSpecType): Token =
    mockk {
        every { type } returns TokenType.AddressSpecTyped(addressSpec)
        every { blockchainType } returns BlockchainType.Zcash
    }

internal fun mockNonZcashNativeToken(blockchainType: BlockchainType = BlockchainType.Ethereum): Token =
    mockk {
        every { this@mockk.blockchainType } returns blockchainType
        every { type } returns TokenType.Native
    }

internal fun MarketKitWrapper.stubZcashTransparentToken(token: Token) {
    every {
        this@stubZcashTransparentToken.token(match {
            it.blockchainType == BlockchainType.Zcash &&
                it.tokenType == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)
        })
    } returns token
}
