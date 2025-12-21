package cash.p.terminal.core.managers

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Provides time-based passwords for QR code encryption.
 * Extracted for testability - can be mocked in unit tests.
 */
class TimePasswordProvider {

    /**
     * Generate a time-based password in format yyyyMMddHH (UTC).
     * @param offsetHours Hours to offset from current time (0 = now, -1 = previous hour, +1 = next hour)
     */
    fun generateTimePassword(offsetHours: Int = 0): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.HOUR_OF_DAY, offsetHours)
        return SimpleDateFormat("yyyyMMddHH", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(calendar.time)
    }
}
