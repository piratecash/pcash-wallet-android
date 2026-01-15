package cash.p.terminal.core.usecase

import cash.p.terminal.core.App
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.tangem.domain.TangemConfig
import cash.p.terminal.tangem.domain.model.ScanResponse
import cash.p.terminal.tangem.domain.totalSignedHashes
import cash.p.terminal.tangem.domain.usecase.BuildHardwarePublicKeyUseCase
import cash.p.terminal.tangem.domain.usecase.ICreateHardwareWalletUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CreateHardwareWalletUseCase(
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val accountManager: IAccountManager
) : ICreateHardwareWalletUseCase {

    private val accountFactory: IAccountFactory = App.accountFactory
    private val walletActivator: WalletActivator = App.walletActivator

    @OptIn(ExperimentalStdlibApi::class)
    override suspend operator fun invoke(
        accountName: String,
        scanResponse: ScanResponse
    ): AccountType.HardwareCard {
        val accountType = AccountType.HardwareCard(
            cardId = scanResponse.card.cardId,
            backupCardsCount = scanResponse.card.backupStatus?.linkedCardsCount ?: 0,
            walletPublicKey = scanResponse.card.cardPublicKey.toHexString(),
            signedHashes = scanResponse.card.totalSignedHashes()
        )
        val account = accountFactory.account(
            name = accountName,
            type = accountType,
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false,
        )

        val defaultTokens = TangemConfig.getDefaultTokens

        val blockchainTypes = defaultTokens.distinct()
        val publicKeys =
            BuildHardwarePublicKeyUseCase().invoke(scanResponse, account.id, blockchainTypes)
        // Save account first - it's the parent for FK constraints
        accountManager.save(account = account, updateActive = false)

        withContext(Dispatchers.IO) {
            // Save public keys after account exists (FK constraint)
            hardwarePublicKeyStorage.save(publicKeys)
        }

        // Activate wallets after account and public keys exist
        activateDefaultWallets(
            account = account,
            tokenQueries = defaultTokens.filter { defaultToken ->
                publicKeys.find { it.blockchainType == defaultToken.blockchainType.uid } != null
            }
        )

        accountManager.setActiveAccountId(account.id)
        return accountType
    }

    private suspend fun activateDefaultWallets(
        account: Account,
        tokenQueries: List<TokenQuery> = TangemConfig.getDefaultTokens
    ) = walletActivator.activateWalletsSuspended(account, tokenQueries)
}