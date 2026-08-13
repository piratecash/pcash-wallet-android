package cash.p.terminal.network.pirate.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import cash.p.terminal.network.pirate.data.database.converter.CoinAssociationListConverter
import cash.p.terminal.network.pirate.data.database.entity.ChangeNowAssociationCoin

@Database(entities = [ChangeNowAssociationCoin::class], version = 1, exportSchema = false)
@TypeConverters(CoinAssociationListConverter::class)
@ConstructedBy(CacheAppDatabaseConstructor::class)
internal abstract class CacheAppDatabase : RoomDatabase() {
    abstract fun changeNowCoinDao(): CacheChangeNowCoinAssociationDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object CacheAppDatabaseConstructor : RoomDatabaseConstructor<CacheAppDatabase> {
    override fun initialize(): CacheAppDatabase
}

internal const val CACHE_DATABASE_NAME = "db_cache"
