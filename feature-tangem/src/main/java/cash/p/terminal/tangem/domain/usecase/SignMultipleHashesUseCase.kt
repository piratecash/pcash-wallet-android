package cash.p.terminal.tangem.domain.usecase

import cash.p.terminal.tangem.domain.sdk.TangemSdkManager
import com.tangem.Message
import com.tangem.operations.sign.SignData

class SignMultipleHashesUseCase(
    private val tangemSdkManager: TangemSdkManager
) {
    suspend operator fun invoke(
        dataToSign: List<SignData>,
        walletPublicKey: ByteArray,
        message: Message? = null
    ) = tangemSdkManager.signMultiple(
        cardId = null,
        dataToSign = dataToSign,
        walletPublicKey = walletPublicKey,
        message = message
    )
}
