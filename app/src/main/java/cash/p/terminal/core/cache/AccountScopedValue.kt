package cash.p.terminal.core.cache

import cash.p.terminal.wallet.IAccountManager
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AccountScopedValue<T>(
    private val accountIdProvider: () -> String?,
) : ReadWriteProperty<Any?, T?> {
    private var cachedAccountId: String? = null
    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        val current = accountIdProvider()
        if (current != cachedAccountId) {
            value = null
            cachedAccountId = current
        }
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        cachedAccountId = accountIdProvider()
        this.value = value
    }
}

fun <T> IAccountManager.accountScoped(): AccountScopedValue<T> =
    AccountScopedValue { activeAccount?.id }
