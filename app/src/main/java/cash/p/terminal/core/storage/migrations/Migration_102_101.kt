package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_102_101 : Migration(102, 101) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `SwapProviderTransaction_tmp` (" +
                "`date` INTEGER NOT NULL, " +
                "`outgoingRecordUid` TEXT, " +
                "`transactionId` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`provider` TEXT NOT NULL, " +
                "`coinUidIn` TEXT NOT NULL, " +
                "`blockchainTypeIn` TEXT NOT NULL, " +
                "`amountIn` TEXT NOT NULL, " +
                "`addressIn` TEXT NOT NULL, " +
                "`coinUidOut` TEXT NOT NULL, " +
                "`blockchainTypeOut` TEXT NOT NULL, " +
                "`amountOut` TEXT NOT NULL, " +
                "`addressOut` TEXT NOT NULL, " +
                "`amountOutReal` TEXT, " +
                "`finishedAt` INTEGER, " +
                "`incomingRecordUid` TEXT, " +
                "PRIMARY KEY(`date`))"
        )
        db.execSQL(
            "INSERT INTO `SwapProviderTransaction_tmp` (" +
                "`date`, `outgoingRecordUid`, `transactionId`, `status`, `provider`, " +
                "`coinUidIn`, `blockchainTypeIn`, `amountIn`, `addressIn`, " +
                "`coinUidOut`, `blockchainTypeOut`, `amountOut`, `addressOut`, " +
                "`amountOutReal`, `finishedAt`, `incomingRecordUid`) " +
                "SELECT " +
                "`date`, `outgoingRecordUid`, `transactionId`, `status`, `provider`, " +
                "`coinUidIn`, `blockchainTypeIn`, `amountIn`, `addressIn`, " +
                "`coinUidOut`, `blockchainTypeOut`, `amountOut`, `addressOut`, " +
                "`amountOutReal`, `finishedAt`, `incomingRecordUid` " +
                "FROM `SwapProviderTransaction`"
        )
        db.execSQL("DROP TABLE `SwapProviderTransaction`")
        db.execSQL("ALTER TABLE `SwapProviderTransaction_tmp` RENAME TO `SwapProviderTransaction`")
    }
}
