package cash.p.terminal.modules.restorelocal

import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.BackupLocalModule.WalletBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.BackupViewItemFactory
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreException
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.OtherBackupViewItem
import cash.p.terminal.modules.backuplocal.fullbackup.SelectBackupItemsViewModel.WalletBackupViewItem
import cash.p.terminal.modules.declinedtokens.DeclinedTokensReview
import cash.p.terminal.modules.declinedtokens.DeclinedTokensReviewHost
import cash.p.terminal.modules.declinedtokens.DeclinedTokensStage
import cash.p.terminal.modules.restorelocal.RestoreLocalModule.UiState
import cash.p.terminal.strings.helpers.Translator
import com.google.gson.JsonParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RestoreLocalViewModel(
    private val backupFilePath: String?,
    private val accountFactory: IAccountFactory,
    private val backupProvider: BackupProvider,
    private val backupViewItemFactory: BackupViewItemFactory,
    private val dispatcherProvider: DispatcherProvider,
    fileName: String?,
) : ViewModelUiState<UiState>(), DeclinedTokensReviewHost {

    private var passphrase = ""
    private var passphraseState: DataState.Error? = null
    private var showButtonSpinner = false
    private var walletBackup: WalletBackup? = null
    private var fullBackup: FullBackup? = null
    private var backupV3: BackupLocalModule.BackupV3? = null
    private var backupV4Binary: ByteArray? = null  // V4 binary format
    private var parseError: Exception? = null
    private var showSelectCoins: cash.p.terminal.wallet.AccountType? = null
    private var manualBackup = false
    private var restored = false
    private var pendingReview: DeclinedTokensReview? = null
    private var pendingRestore: PendingRestore? = null

    private var decryptedFullBackup: DecryptedFullBackup? = null
    private var walletBackupViewItems: List<WalletBackupViewItem> = emptyList()
    private var otherBackupViewItems: List<OtherBackupViewItem> = emptyList()
    private var showBackupItems = false

    val accountName by lazy {
        val baseName = fileName?.let { name ->
            name.replace(".json", "")
                .replace("UW_Backup_", "")
                .replace("_", " ")
        } ?: return@lazy accountFactory.getNextAccountName()
        accountFactory.getUniqueName(baseName)
    }

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val file = backupFilePath?.let { File(it) }
                val bytes = file?.readBytes()
                file?.delete()

                if (bytes != null && BackupLocalModule.BackupV4Binary.isBinaryFormat(bytes)) {
                    backupV4Binary = bytes
                } else {
                    val jsonString = bytes?.let { String(it, Charsets.UTF_8) }

                    val gson = GsonBuilder()
                        .disableHtmlEscaping()
                        .enableComplexMapKeySerialization()
                        .create()

                    backupV3 = jsonString?.let { backupProvider.parseV3Backup(it) }

                    if (backupV3 == null) {
                        fullBackup = try {
                            val backup = gson.fromJson(jsonString, FullBackup::class.java)
                            backup.version
                            if (JsonParser.parseString(jsonString).asJsonObject.has("crypto")) {
                                error("Single wallet")
                            }
                            backup
                        } catch (ex: Exception) {
                            null
                        }

                        walletBackup = gson.fromJson(jsonString, WalletBackup::class.java)
                        manualBackup = walletBackup?.manualBackup ?: false
                    }
                }
            } catch (e: Exception) {
                parseError = e
                emitState()
            }
        }
    }

    override fun createState() = UiState(
        passphraseState = passphraseState,
        showButtonSpinner = showButtonSpinner,
        parseError = parseError,
        showSelectCoins = showSelectCoins,
        manualBackup = manualBackup,
        restored = restored,
        walletBackupViewItems = walletBackupViewItems,
        otherBackupViewItems = otherBackupViewItems,
        showBackupItems = showBackupItems,
        tokenReview = pendingReview,
    )

    fun onChangePassphrase(v: String) {
        passphrase = v
        passphraseState = null
        emitState()
    }

    fun onImportClick() {
        if (uiState.showButtonSpinner) return

        when {
            backupV4Binary != null -> {
                backupV4Binary?.let { restoreV4BinaryBackup(it) }
            }

            backupV3 != null -> {
                backupV3?.let { restoreV3Backup(it) }
            }

            fullBackup != null -> {
                fullBackup?.let { showFullBackupItems(it) }
            }

            walletBackup != null -> {
                walletBackup?.let { restoreSingleWallet(it, accountName, BackupSource.Legacy) }
            }
        }
    }

    /**
     * Restore V4 binary backup with deniable encryption.
     * The password determines which payload is decrypted from the container.
     * Wrong password returns null (no data found), not an error - this is by design
     * to support plausible deniability.
     */
    private fun restoreV4BinaryBackup(binaryData: ByteArray): Job {
        showButtonSpinner = true
        emitState()

        return viewModelScope.launch(dispatcherProvider.io) {
            try {
                val decrypted = backupProvider.restoreFromV4BinaryBackup(binaryData, passphrase)

                if (decrypted == null) {
                    // No data found for this password - could be wrong password
                    // or this password's slot is empty (deniable encryption)
                    passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
                } else if (isSingleWalletBackup(decrypted)) {
                    // Single wallet backup - restore directly like V3
                    val walletItem = decrypted.wallets.first()
                    manualBackup = walletItem.enabledWallets.any { it.settings?.isNotEmpty() == true }

                    if (walletItem.enabledWallets.isEmpty()) {
                        showSelectCoins = walletItem.account.type
                    } else {
                        // isSingleWalletBackup guarantees no watchlist/settings/contacts, so Full's restore is exactly this wallet.
                        val outcome = backupProvider.restoreSingleWalletBackup(walletItem)
                        handleOutcome(outcome, PendingRestore.Full(decrypted))
                    }
                } else {
                    // Full backup - show preview screen
                    val backupItems = backupProvider.fullBackupItems(decrypted)
                    val backupViewItems = backupViewItemFactory.backupViewItems(backupItems)

                    walletBackupViewItems = backupViewItems.first
                    otherBackupViewItems = backupViewItems.second
                    decryptedFullBackup = decrypted
                    showBackupItems = true
                }
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (e: Exception) {
                parseError = e
            }

            withContext(dispatcherProvider.main) {
                showButtonSpinner = false
                emitState()
            }
        }
    }

    /**
     * Checks if decrypted backup is a single wallet backup (not full backup).
     * Single wallet: 1 wallet, no settings, no contacts, no watchlist.
     */
    private fun isSingleWalletBackup(backup: DecryptedFullBackup): Boolean {
        return backup.wallets.size == 1 &&
                backup.settings == null &&
                backup.contacts.isEmpty() &&
                backup.watchlist.isEmpty()
    }

    private fun restoreV3Backup(backup: BackupLocalModule.BackupV3): Job {
        showButtonSpinner = true
        emitState()

        return viewModelScope.launch(dispatcherProvider.io) {
            try {
                val gson = GsonBuilder()
                    .disableHtmlEscaping()
                    .enableComplexMapKeySerialization()
                    .create()

                // Get inner JSON, cached key, and kdfParams (single Scrypt call)
                val (innerJson, cachedKey, cachedKdfParams) = backupProvider.unwrapV3FormatWithKey(backup, passphrase)

                // Try to parse as FullBackup first
                val parsedFullBackup = try {
                    val fb = gson.fromJson(innerJson, FullBackup::class.java)
                    fb.version
                    if (JsonParser.parseString(innerJson).asJsonObject.has("crypto")) {
                        error("Single wallet")
                    }
                    fb
                } catch (ex: Exception) {
                    null
                }

                if (parsedFullBackup != null) {
                    showV3FullBackupItems(parsedFullBackup, cachedKey, cachedKdfParams)
                } else {
                    // Parse as single wallet backup
                    val parsedWalletBackup = gson.fromJson(innerJson, WalletBackup::class.java)
                    manualBackup = parsedWalletBackup.manualBackup

                    // Use cached key to avoid another Scrypt call (with kdfParams check)
                    val type = backupProvider.accountTypeWithKey(parsedWalletBackup, cachedKey, cachedKdfParams, passphrase)

                    if (parsedWalletBackup.enabledWallets.isNullOrEmpty()) {
                        showSelectCoins = type
                    } else {
                        requireNotNull(type) {
                            "This account type is not supported for restoration."
                        }
                        // Same V3 ciphertext as the full-backup branch: decimals are authenticated, but a manually-added row can still need approval.
                        val outcome = backupProvider.restoreSingleWalletBackup(
                            type, accountName, parsedWalletBackup, BackupSource.Authenticated
                        )
                        handleOutcome(
                            outcome,
                            PendingRestore.SingleWallet(parsedWalletBackup, accountName, BackupSource.Authenticated)
                        )
                    }
                }
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }

            withContext(dispatcherProvider.main) {
                showButtonSpinner = false
                emitState()
            }
        }
    }

    /** [parsedFullBackup] came from the same MAC'd V3 ciphertext already verified above, hence [BackupSource.Authenticated]. */
    private suspend fun showV3FullBackupItems(
        parsedFullBackup: FullBackup,
        cachedKey: ByteArray,
        cachedKdfParams: BackupLocalModule.KdfParams
    ) {
        // Use cached key to avoid Scrypt calls for each wallet.
        val decrypted = backupProvider.decryptedFullBackupWithKey(
            parsedFullBackup, cachedKey, cachedKdfParams, passphrase, BackupSource.Authenticated
        )

        val backupItems = backupProvider.fullBackupItems(decrypted)
        val backupViewItems = backupViewItemFactory.backupViewItems(backupItems)

        walletBackupViewItems = backupViewItems.first
        otherBackupViewItems = backupViewItems.second
        decryptedFullBackup = decrypted
        showBackupItems = true
    }

    private fun showFullBackupItems(it: FullBackup): Job {
        showButtonSpinner = true
        emitState()

        return viewModelScope.launch(dispatcherProvider.io) {
            try {
                // Raw legacy envelope: enabled_wallets sits outside the MAC'd blob, so its metadata is forgeable.
                val decrypted = backupProvider.decryptedFullBackup(it, passphrase, BackupSource.Legacy)
                val backupItems = backupProvider.fullBackupItems(decrypted)
                val backupViewItems = backupViewItemFactory.backupViewItems(backupItems)

                walletBackupViewItems = backupViewItems.first
                otherBackupViewItems = backupViewItems.second
                decryptedFullBackup = decrypted
                showBackupItems = true
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }

            withContext(dispatcherProvider.main) {
                showButtonSpinner = false
                emitState()
            }
        }
    }

    fun shouldShowReplaceWarning(): Boolean {
        return backupProvider.shouldShowReplaceWarning(decryptedFullBackup)
    }

    fun restoreFullBackup() {
        decryptedFullBackup?.let { restoreFullBackup(it) }
    }

    private fun restoreFullBackup(
        decryptedFullBackup: DecryptedFullBackup,
        approved: Map<String, Set<String>>? = null,
    ) {
        showButtonSpinner = true
        emitState()

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val outcome = backupProvider.restoreFullBackup(decryptedFullBackup, passphrase, approved)
                handleOutcome(outcome, PendingRestore.Full(decryptedFullBackup))
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }

            showButtonSpinner = false
            withContext(dispatcherProvider.main) {
                emitState()
            }
        }
    }

    @Throws
    private fun restoreSingleWallet(
        backup: WalletBackup,
        accountName: String,
        source: BackupSource,
        approved: Set<String>? = null,
    ) {
        showButtonSpinner = true
        emitState()
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val type = backupProvider.accountType(backup, passphrase)
                if (backup.enabledWallets.isNullOrEmpty()) {
                    showSelectCoins = type
                } else {
                    requireNotNull(type) {
                        "This account type is not supported for restoration."
                    }
                    val outcome = backupProvider.restoreSingleWalletBackup(
                        type, accountName, backup, source, approved
                    )
                    handleOutcome(outcome, PendingRestore.SingleWallet(backup, accountName, source))
                }
            } catch (keyException: RestoreException.EncryptionKeyException) {
                parseError = keyException
            } catch (invalidPassword: RestoreException.InvalidPasswordException) {
                passphraseState = DataState.Error(Exception(Translator.getString(R.string.ImportBackupFile_Error_InvalidPassword)))
            } catch (e: Exception) {
                parseError = e
            }
            showButtonSpinner = false
            withContext(dispatcherProvider.main) {
                emitState()
            }
        }
    }

    fun onSelectCoinsShown() {
        showSelectCoins = null
        emitState()
    }

    fun onBackupItemsShown() {
        showBackupItems = false
        emitState()
    }

    /** Sets `restored`, or parks the declined tokens for review. Returns nothing else. */
    private fun handleOutcome(outcome: RestoreOutcome, pending: PendingRestore?) {
        when (outcome) {
            is RestoreOutcome.Restored -> {
                restored = true
                pendingReview = null
                pendingRestore = null
            }

            is RestoreOutcome.TokensNeedReview -> {
                pendingReview = DeclinedTokensReview(outcome.wallets)
                pendingRestore = pending
            }
        }
    }

    // Reads uiState, not the backing field, so this is what recomposes the sheets.
    override val tokenReview: DeclinedTokensReview?
        get() = uiState.tokenReview

    override fun onReviewTokens() {
        pendingReview = pendingReview?.copy(stage = DeclinedTokensStage.Select)
        emitState()
    }

    override fun onApproveTokens(approvals: Map<String, Set<String>>) {
        val pending = pendingRestore
        pendingReview = null
        pendingRestore = null
        when (pending) {
            is PendingRestore.SingleWallet -> restoreSingleWallet(
                pending.backup, pending.accountName, pending.source, approvals.values.firstOrNull().orEmpty()
            )

            is PendingRestore.Full -> restoreFullBackup(pending.backup, approvals)
            null -> emitState()
        }
    }

    override fun onDismissTokenReview() {
        pendingReview = null
        pendingRestore = null
        showButtonSpinner = false
        emitState()
    }
}

/** Restore call parked while the user reviews declined tokens; every restore path can produce one. */
private sealed interface PendingRestore {
    data class SingleWallet(val backup: WalletBackup, val accountName: String, val source: BackupSource) : PendingRestore
    data class Full(val backup: DecryptedFullBackup) : PendingRestore
}
