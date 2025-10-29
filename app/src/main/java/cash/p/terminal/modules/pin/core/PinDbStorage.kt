package cash.p.terminal.modules.pin.core

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.wallet.entities.SecretString

class PinDbStorage(private val pinDao: PinDao) {

    fun isLastLevelPinSet(): Boolean {
        val lastLevelPin = pinDao.getLastLevelPin()
        return lastLevelPin?.passcode != null
    }

    fun store(passcode: String, level: Int) {
        val pin = Pin(level, SecretString(passcode))
        pinDao.insert(pin)
    }

    fun clearPasscode(level: Int) {
        val pin = Pin(level, null)
        pinDao.insert(pin)
    }

    fun getLevel(passcode: String): Int? {
        return pinDao.getAll().find {
            it.passcode?.value == passcode
        }?.level
    }

    fun getPinLevelLast(): Int {
        return pinDao.getLastLevelPin()?.level ?: 0
    }

    fun deleteAllFromLevel(level: Int) {
        pinDao.deleteAllFromLevel(level)
    }

    fun deleteForLevel(level: Int) {
        pinDao.deleteForLevel(level)
    }

    fun isPinSetForLevel(level: Int): Boolean {
        return pinDao.get(level)?.passcode != null
    }

    fun getNextHiddenWalletLevel(): Int {
        val minLevel = pinDao.getMinLevel() ?: 0
        return if (minLevel < 0) minLevel - 1 else -1
    }
}

@Dao
interface PinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pin: Pin)

    @Query("SELECT * FROM Pin WHERE level = :level")
    fun get(level: Int): Pin?

    @Query("SELECT * FROM Pin")
    fun getAll() : List<Pin>

    /** Get the last user level PIN, excluding Secure Reset PIN at level ${PinLevels.SECURE_RESET} */
    @Query("SELECT * FROM Pin WHERE level != ${PinLevels.SECURE_RESET} ORDER BY level DESC LIMIT 1")
    fun getLastLevelPin(): Pin?

    @Query("DELETE FROM Pin WHERE level >= :level")
    fun deleteAllFromLevel(level: Int)

    @Query("DELETE FROM Pin WHERE level = :level")
    fun deleteForLevel(level: Int)

    @Query("SELECT MIN(level) FROM Pin")
    fun getMinLevel(): Int?
}

object PinLevels {
    const val SECURE_RESET = 10000
}

@Entity(primaryKeys = ["level"])
data class Pin(val level: Int, val passcode: SecretString?)
