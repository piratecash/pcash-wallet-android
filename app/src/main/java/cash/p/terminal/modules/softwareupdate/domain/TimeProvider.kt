package cash.p.terminal.modules.softwareupdate.domain

interface TimeProvider {
    fun now(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
