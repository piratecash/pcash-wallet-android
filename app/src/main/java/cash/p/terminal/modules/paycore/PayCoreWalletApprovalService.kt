package cash.p.terminal.modules.paycore

class PayCoreWalletApprovalService(
    private val apiService: PayCoreApiService,
    private val secureStorage: PayCoreSecureStorage,
) {
    suspend fun requestApproval(
        phone: String,
        walletAddress: String,
        networkType: PayCoreTicker,
    ): PayCoreWalletApprovalResult {
        if (walletAddress.isBlank()) return PayCoreWalletApprovalResult.MissingWalletAddress

        val response = apiService.createWallet(
            PayCoreWalletCreateRequest(
                phone = phone,
                address = walletAddress,
                networkType = networkType,
                backUrl = PAYCORE_COMPLETE_BACK_URL,
            )
        )
        return response.toApprovalResult()
    }

    suspend fun ensureApprovedForSavedPhone(
        walletAddress: String,
        networkType: PayCoreTicker,
    ) {
        val phone = secureStorage.getPhone()
            ?: throw PayCoreWalletNotApprovedException(PayCoreWalletApprovalResult.MissingPhone)
        val result = requestApproval(
            phone = phone,
            walletAddress = walletAddress,
            networkType = networkType,
        )
        if (result != PayCoreWalletApprovalResult.Approved) {
            throw PayCoreWalletNotApprovedException(result)
        }
    }

    private fun PayCoreWalletCreateResponse.toApprovalResult(): PayCoreWalletApprovalResult {
        return when (status) {
            PayCoreWalletCreateStatus.NO_ACCESS -> PayCoreWalletApprovalResult.NoAccess
            PayCoreWalletCreateStatus.NOT_REGISTERED -> PayCoreWalletApprovalResult.NotRegistered(url)
            PayCoreWalletCreateStatus.PENDING -> PayCoreWalletApprovalResult.Pending
            PayCoreWalletCreateStatus.APPROVED -> PayCoreWalletApprovalResult.Approved
            PayCoreWalletCreateStatus.REJECTED -> PayCoreWalletApprovalResult.Rejected
            PayCoreWalletCreateStatus.SUSPENDED -> PayCoreWalletApprovalResult.Suspended
            else -> PayCoreWalletApprovalResult.Unknown(status)
        }
    }
}

sealed interface PayCoreWalletApprovalResult {
    data object Approved : PayCoreWalletApprovalResult
    data object NoAccess : PayCoreWalletApprovalResult
    data object Pending : PayCoreWalletApprovalResult
    data object Rejected : PayCoreWalletApprovalResult
    data object Suspended : PayCoreWalletApprovalResult
    data object MissingPhone : PayCoreWalletApprovalResult
    data object MissingWalletAddress : PayCoreWalletApprovalResult
    data class NotRegistered(val url: String?) : PayCoreWalletApprovalResult
    data class Unknown(val status: String) : PayCoreWalletApprovalResult
}

class PayCoreWalletNotApprovedException(
    val result: PayCoreWalletApprovalResult,
) : IllegalStateException(result.toString())
