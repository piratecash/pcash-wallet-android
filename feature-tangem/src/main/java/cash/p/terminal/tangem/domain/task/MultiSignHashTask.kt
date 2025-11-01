package cash.p.terminal.tangem.domain.task

import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.operations.sign.MultipleSignCommand
import com.tangem.operations.sign.SignData
import com.tangem.operations.sign.SignHashResponse

class MultiSignHashTask(
    private val dataToSign: List<SignData>,
    private val walletPublicKey: ByteArray
) : CardSessionRunnable<List<SignHashResponse>> {

    override fun run(
        session: CardSession,
        callback: CompletionCallback<List<SignHashResponse>>
    ) {
        MultipleSignCommand(dataToSign, walletPublicKey).run(session) { result ->
            callback(result)
        }
    }
}
