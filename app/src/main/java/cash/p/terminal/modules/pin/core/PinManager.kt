package cash.p.terminal.modules.pin.core

import io.reactivex.subjects.PublishSubject

class PinManager(private val pinDbStorage: PinDbStorage) {
    val pinSetSubject = PublishSubject.create<Unit>()

    val isPinSet: Boolean
        get() = pinDbStorage.isLastLevelPinSet()

    @Throws
    fun store(pin: String, level: Int) {
        pinDbStorage.store(pin, level)
        pinSetSubject.onNext(Unit)
    }

    fun getPinLevel(pin: String): Int? {
        return pinDbStorage.getLevel(pin)
    }

    fun getPinLevelLast(): Int {
        return pinDbStorage.getPinLevelLast()
    }

    fun disablePin(level: Int) {
        if (level < 0 || level == PinLevels.SECURE_RESET) {
            pinDbStorage.deleteForLevel(level)
        } else {
            // For Level 0 and above (regular, duress) - clear current, delete higher levels
            pinDbStorage.clearPasscode(level)
            pinDbStorage.deleteAllFromLevel(level + 1)
        }
        pinSetSubject.onNext(Unit)
    }

    fun disableDuressPin(level: Int) {
        pinDbStorage.deleteAllFromLevel(level)
        pinSetSubject.onNext(Unit)
    }

    fun isPinSetForLevel(level: Int): Boolean {
        return pinDbStorage.isPinSetForLevel(level)
    }

    fun isUnique(pin: String, userLevel: Int): Boolean {
        val pinLevel = pinDbStorage.getLevel(pin) ?: return true

        return pinLevel == userLevel
    }

    fun getNextHiddenWalletLevel(): Int {
        return pinDbStorage.getNextHiddenWalletLevel()
    }

}