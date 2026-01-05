package io.horizontalsystems.core

/**
 * Interface for login logging settings storage.
 * This is a subset of ILocalStorage used by the feature-logging module.
 * All settings are level-scoped - pass the user level explicitly.
 */
interface ILoggingSettings {
    fun hasEnabledAtLeastOneSettingsEnabled(level: Int): Boolean

    fun getLogSuccessfulLoginsEnabled(level: Int): Boolean
    fun setLogSuccessfulLoginsEnabled(level: Int, enabled: Boolean)

    fun getSelfieOnSuccessfulLoginEnabled(level: Int): Boolean
    fun setSelfieOnSuccessfulLoginEnabled(level: Int, enabled: Boolean)

    fun getLogUnsuccessfulLoginsEnabled(level: Int): Boolean
    fun setLogUnsuccessfulLoginsEnabled(level: Int, enabled: Boolean)

    fun getSelfieOnUnsuccessfulLoginEnabled(level: Int): Boolean
    fun setSelfieOnUnsuccessfulLoginEnabled(level: Int, enabled: Boolean)

    fun getLogIntoDuressModeEnabled(level: Int): Boolean
    fun setLogIntoDuressModeEnabled(level: Int, enabled: Boolean)

    fun getSelfieOnDuressLoginEnabled(level: Int): Boolean
    fun setSelfieOnDuressLoginEnabled(level: Int, enabled: Boolean)

    fun getDeleteAllAuthDataOnDuressEnabled(level: Int): Boolean
    fun setDeleteAllAuthDataOnDuressEnabled(level: Int, enabled: Boolean)

    fun getAutoDeleteLogsPeriod(level: Int): Int // 0 = Never, 1 = Month, 2 = Year
    fun setAutoDeleteLogsPeriod(level: Int, period: Int)
}
