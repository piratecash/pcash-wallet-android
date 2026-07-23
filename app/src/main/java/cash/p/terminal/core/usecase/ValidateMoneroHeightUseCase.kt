package cash.p.terminal.core.usecase

import com.m2049r.xmrwallet.util.RestoreHeight
import timber.log.Timber
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

class ValidateMoneroHeightUseCase {

    private val parser = SimpleDateFormat("yyyy-MM-dd").apply {
        isLenient = false
    }
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getHeight(date: LocalDate): Long = invoke(date.format(dateFormatter))

    operator fun invoke(heightStr: String): Long {
        var height: Long = -1

        val restoreHeight = heightStr.trim { it <= ' ' }
        if (restoreHeight.isEmpty()) return -1
        try {
            height = RestoreHeight.getInstance().getHeight(parser.parse(restoreHeight))
        } catch (_: ParseException) {
        }
        if ((height < 0) && (restoreHeight.length == 8)) try {
            // is it a date without dashes?
            height = RestoreHeight.getInstance().getHeight(parser.parse(restoreHeight))
        } catch (_: ParseException) {
        }
        if (height < 0) try {
            // or is it a height?
            height = restoreHeight.toLong()
        } catch (ex: NumberFormatException) {
            return -1
        }
        Timber.d("Using Restore Height = %d", height)
        return height
    }

    fun getTodayHeight(): Long {
        return try {
            RestoreHeight.getInstance().getHeight(Date())
        } catch (e: Exception) {
            Timber.e(e, "Error while getting today's restore height")
            -1
        }
    }

    /**
     * Reverse lookup: approximates the calendar date for a given block height by binary
     * searching over [GENESIS_DATE, today], relying on [getHeight] being monotonically
     * non-decreasing with date.
     */
    fun getDateForHeight(height: Long): LocalDate? {
        if (height <= 0) return null

        var low = GENESIS_DATE
        var high = LocalDate.now()
        while (ChronoUnit.DAYS.between(low, high) > 1) {
            val mid = low.plusDays(ChronoUnit.DAYS.between(low, high) / 2)
            if (getHeight(mid) <= height) {
                low = mid
            } else {
                high = mid
            }
        }
        return low
    }

    private companion object {
        val GENESIS_DATE: LocalDate = LocalDate.of(2014, 4, 18)
    }
}