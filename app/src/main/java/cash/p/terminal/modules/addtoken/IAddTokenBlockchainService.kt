package cash.p.terminal.modules.addtoken

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery

interface IAddTokenBlockchainService {
    fun isValid(reference: String): Boolean
    fun tokenQuery(reference: String): TokenQuery
    suspend fun token(reference: String): Token
}
