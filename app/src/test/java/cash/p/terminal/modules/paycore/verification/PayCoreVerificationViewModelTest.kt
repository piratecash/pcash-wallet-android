package cash.p.terminal.modules.paycore.verification

import cash.p.terminal.R
import cash.p.terminal.modules.paycore.PAYCORE_COMPLETE_BACK_URL
import cash.p.terminal.modules.paycore.PayCoreApiService
import cash.p.terminal.modules.paycore.PayCoreLinkedWallet
import cash.p.terminal.modules.paycore.PayCoreSecureStorage
import cash.p.terminal.modules.paycore.PayCoreSecureStorage.VerificationStatus
import cash.p.terminal.modules.paycore.PayCoreSignatureHelper
import cash.p.terminal.modules.paycore.PayCoreWalletChangeRequest
import cash.p.terminal.modules.paycore.PayCoreWalletCreateRequest
import cash.p.terminal.modules.paycore.PayCoreWalletCreateResponse
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import java.math.BigInteger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class PayCoreVerificationViewModelTest {

    private val apiService = mockk<PayCoreApiService>()
    private val secureStorage = mockk<PayCoreSecureStorage>(relaxed = true)
    private val signatureHelper = mockk<PayCoreSignatureHelper>()
    private val accountManager = mockk<IAccountManager>(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { signatureHelper.getWalletAddress(any()) } returns CURRENT_ADDRESS
        every { signatureHelper.getWalletAddress(any(), any()) } returns CURRENT_ADDRESS
        every { signatureHelper.signPhone(any()) } returns "signed-key"
        every { secureStorage.getLinkedWallet() } returns null
        every { accountManager.activeAccount } returns activeAccount
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun onContinueClick_status0_showsKycRequired() = runTest(dispatcher) {
        val kycUrl = "https://kyc.example.com"
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 0, url = kycUrl
        )

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(VerificationScreen.KycRequired, viewModel.uiState.screen)
        assertEquals(kycUrl, viewModel.uiState.kycUrl)
        assertFalse(viewModel.uiState.loading)
    }

    @Test
    fun onContinueClick_status1_noStoredWallet_showsSupportRequired() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 1
        )

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(VerificationScreen.PhoneInput, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.supportRequired)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun onContinueClick_status1_linkedAccountMissing_showsSupportRequired() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 1
        )
        every { secureStorage.getLinkedWallet() } returns PayCoreLinkedWallet(
            accountId = OLD_ACCOUNT_ID,
            address = OLD_ADDRESS,
            networkType = NETWORK_TYPE
        )
        every { accountManager.account(OLD_ACCOUNT_ID) } returns null

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.supportRequired)
    }

    @Test
    fun onContinueClick_status1_recoveryAddressMismatch_showsSupportRequired() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 1
        )
        every { secureStorage.getLinkedWallet() } returns PayCoreLinkedWallet(
            accountId = OLD_ACCOUNT_ID,
            address = OLD_ADDRESS,
            networkType = NETWORK_TYPE
        )
        every { accountManager.account(OLD_ACCOUNT_ID) } returns oldAccount
        every { signatureHelper.getWalletAddress(NETWORK_TYPE, oldAccount) } returns "0xDIFFERENT"

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.supportRequired)
    }

    @Test
    fun onContinueClick_status1_changeWalletFails_showsSupportRequired() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 1
        )
        every { secureStorage.getLinkedWallet() } returns PayCoreLinkedWallet(
            accountId = OLD_ACCOUNT_ID,
            address = OLD_ADDRESS,
            networkType = NETWORK_TYPE
        )
        every { accountManager.account(OLD_ACCOUNT_ID) } returns oldAccount
        every { signatureHelper.getWalletAddress(NETWORK_TYPE, oldAccount) } returns OLD_ADDRESS
        coEvery { apiService.changeWallet(any(), any(), any()) } throws RuntimeException("boom")

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.supportRequired)
    }

    @Test
    fun onContinueClick_status1_autoRecoverySucceeds_showsWarning() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returnsMany listOf(
            PayCoreWalletCreateResponse(status = 1),
            PayCoreWalletCreateResponse(status = 2)
        )
        every { secureStorage.getLinkedWallet() } returns PayCoreLinkedWallet(
            accountId = OLD_ACCOUNT_ID,
            address = OLD_ADDRESS,
            networkType = NETWORK_TYPE
        )
        every { accountManager.account(OLD_ACCOUNT_ID) } returns oldAccount
        every { signatureHelper.getWalletAddress(NETWORK_TYPE, oldAccount) } returns OLD_ADDRESS
        val changeRequestSlot = slot<PayCoreWalletChangeRequest>()
        val signingAccountSlot = slot<Account>()
        coEvery {
            apiService.changeWallet(
                request = capture(changeRequestSlot),
                signingNetworkType = any(),
                signingAccount = capture(signingAccountSlot)
            )
        } returns mockk(relaxed = true)

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(VerificationScreen.VerificationWarning, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.supportRequired)
        assertEquals(CURRENT_ADDRESS, changeRequestSlot.captured.address)
        assertEquals(OLD_ACCOUNT_ID, signingAccountSlot.captured.id)
    }

    @Test
    fun onContinueClick_status2_showsWarningWithoutSavingVerified() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 2
        )

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(VerificationScreen.VerificationWarning, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.loading)
        verify(exactly = 0) { secureStorage.setVerificationStatus(VerificationStatus.VERIFIED) }
    }

    @Test
    fun onContinueClick_status0_savesLinkedWalletForActiveAccount() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 0, url = "https://kyc.example.com"
        )
        val linkedSlot = slot<PayCoreLinkedWallet>()
        every { secureStorage.setLinkedWallet(capture(linkedSlot)) } returns Unit

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(ACTIVE_ACCOUNT_ID, linkedSlot.captured.accountId)
        assertEquals(CURRENT_ADDRESS, linkedSlot.captured.address)
        assertEquals(NETWORK_TYPE, linkedSlot.captured.networkType)
    }

    @Test
    fun onContinueClick_status2_savesLinkedWalletForActiveAccount() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 2
        )
        val linkedSlot = slot<PayCoreLinkedWallet>()
        every { secureStorage.setLinkedWallet(capture(linkedSlot)) } returns Unit

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(ACTIVE_ACCOUNT_ID, linkedSlot.captured.accountId)
        assertEquals(CURRENT_ADDRESS, linkedSlot.captured.address)
        assertEquals(NETWORK_TYPE, linkedSlot.captured.networkType)
    }

    @Test
    fun onVerificationWarningAccepted_savesVerifiedAndCompletes() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(status = 2)

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        viewModel.onVerificationWarningAccepted()

        verify { secureStorage.setVerificationStatus(VerificationStatus.VERIFIED) }
        assertTrue(viewModel.uiState.completed)
    }

    @Test
    fun onContinueClick_passesVerificationBackUrl() = runTest(dispatcher) {
        val requestSlot = slot<PayCoreWalletCreateRequest>()
        coEvery { apiService.createWallet(capture(requestSlot), any()) } returns PayCoreWalletCreateResponse(
            status = 0, url = "https://kyc.example.com"
        )

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(PAYCORE_COMPLETE_BACK_URL, requestSlot.captured.backUrl)
    }

    @Test
    fun onKycCompleted_rechecksStatus() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(
            status = 0, url = "https://kyc.example.com"
        ) andThen PayCoreWalletCreateResponse(
            status = 2
        )
        every { secureStorage.getPhone() } returns "+79001234567"

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        viewModel.onKycCompleted()
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 2) { apiService.createWallet(any(), any()) }
        assertEquals(VerificationScreen.VerificationWarning, viewModel.uiState.screen)
    }

    @Test
    fun onContinueClick_setsLoadingWhileKeepingPhoneInputScreen() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { apiService.createWallet(any(), any()) } coAnswers {
            gate.await()
            PayCoreWalletCreateResponse(status = 0, url = "https://kyc.example.com")
        }

        val viewModel = createViewModel()
        viewModel.onPhoneChange("9001234567")
        viewModel.onContinueClick()

        assertEquals(VerificationScreen.PhoneInput, viewModel.uiState.screen)
        assertTrue(viewModel.uiState.loading)
        assertNull(viewModel.uiState.error)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun onPhoneChange_afterInlineError_clearsErrorAndSupportFlag() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } returns PayCoreWalletCreateResponse(status = 1)

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.supportRequired)

        viewModel.onPhoneChange("9112223344")

        assertEquals("9112223344", viewModel.uiState.phone)
        assertNull(viewModel.uiState.error)
        assertFalse(viewModel.uiState.supportRequired)
    }

    @Test
    fun onContinueClick_networkError_showsNoInternetMessage() = runTest(dispatcher) {
        coEvery { apiService.createWallet(any(), any()) } throws IllegalStateException(
            "host.not.found",
            UnknownHostException()
        )

        val viewModel = createViewModel()
        submitPhone(viewModel)
        advanceUntilIdle()

        assertEquals(VerificationScreen.PhoneInput, viewModel.uiState.screen)
        assertFalse(viewModel.uiState.loading)
        assertEquals(Translator.getString(R.string.Hud_Text_NoInternet), viewModel.uiState.error)
    }

    private fun createViewModel() = PayCoreVerificationViewModel(
        networkType = NETWORK_TYPE,
        apiService = apiService,
        secureStorage = secureStorage,
        signatureHelper = signatureHelper,
        accountManager = accountManager
    )

    private fun submitPhone(viewModel: PayCoreVerificationViewModel, digits: String = "9001234567") {
        viewModel.onPhoneChange(digits)
        viewModel.onContinueClick()
    }

    private companion object {
        const val NETWORK_TYPE = "ERC20"
        const val CURRENT_ADDRESS = "0xCurrentAddress"
        const val OLD_ADDRESS = "0xOldAddress"
        const val ACTIVE_ACCOUNT_ID = "active-account-id"
        const val OLD_ACCOUNT_ID = "old-account-id"

        val activeAccount = Account(
            id = ACTIVE_ACCOUNT_ID,
            name = "Active",
            type = AccountType.EvmPrivateKey(BigInteger.ONE),
            origin = AccountOrigin.Created,
            level = 0
        )

        val oldAccount = Account(
            id = OLD_ACCOUNT_ID,
            name = "Old",
            type = AccountType.EvmPrivateKey(BigInteger.valueOf(2)),
            origin = AccountOrigin.Created,
            level = 0
        )
    }
}
