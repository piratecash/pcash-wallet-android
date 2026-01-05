package io.horizontalsystems.core

import java.math.BigDecimal

/**
 * Interface for SMS notification settings storage via ZEC transactions.
 * This is a subset of ILocalStorage used by the feature-logging module.
 * All settings are level-scoped - pass the user level explicitly.
 */
interface ISmsNotificationSettings {
    fun getSmsNotificationAccountId(level: Int): String?
    fun setSmsNotificationAccountId(level: Int, accountId: String?)

    fun getSmsNotificationAddress(level: Int): String?
    fun setSmsNotificationAddress(level: Int, address: String?)

    fun getSmsNotificationMemo(level: Int): String?
    fun setSmsNotificationMemo(level: Int, memo: String?)

    companion object {
        val AMOUNT_TO_SEND_ZEC: BigDecimal = BigDecimal("0.0001")
    }
}
