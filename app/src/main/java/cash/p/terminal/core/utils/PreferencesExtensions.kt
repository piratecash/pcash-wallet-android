package cash.p.terminal.core.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import java.lang.Enum.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <reified T : Any> SharedPreferences.delegate(
    key: String,
    default: T,
    commit: Boolean = false
): ReadWriteProperty<Any, T> = object : ReadWriteProperty<Any, T> {

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return when (T::class) {
            Boolean::class -> getBoolean(key, default as Boolean) as T
            Int::class -> getInt(key, default as Int) as T
            Float::class -> getFloat(key, default as Float) as T
            Long::class -> getLong(key, default as Long) as T
            String::class -> getString(key, default as String) as T
            Set::class -> getStringSet(key, default as Set<String>) as T
            else -> {
                if (T::class.java.isEnum) {
                    val enumName = getString(key, (default as Enum<*>).name)
                    enumName?.let { name ->
                        valueOf(T::class.java as Class<out Enum<*>>, name) as T
                    } ?: default
                } else {
                    throw IllegalArgumentException("Unsupported type ${T::class}")
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        edit(commit = commit) {
            when (T::class) {
                Boolean::class -> putBoolean(key, value as Boolean)
                Int::class -> putInt(key, value as Int)
                Float::class -> putFloat(key, value as Float)
                Long::class -> putLong(key, value as Long)
                String::class -> putString(key, value as String)
                Set::class -> putStringSet(key, value as Set<String>)
                else -> {
                    if (T::class.java.isEnum) {
                        putString(key, (value as Enum<*>).name)
                    } else {
                        throw IllegalArgumentException("Unsupported type ${T::class}")
                    }
                }
            }
        }
    }
}