package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.AddressLabel
import cash.p.terminal.entities.AddressLabelSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddressLabelDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AddressLabelDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
        dao = database.addressLabelDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAll_multipleSources_returnsAllRows() = runTest {
        dao.insert(
            listOf(
                label(AddressLabelSource.LEGACY_API, "Legacy"),
                label(AddressLabelSource.REMOTE, "Remote"),
                label(AddressLabelSource.BUILT_IN, "Built-in"),
            )
        )

        assertEquals(
            setOf(
                AddressLabelSource.BUILT_IN,
                AddressLabelSource.REMOTE,
                AddressLabelSource.LEGACY_API,
            ),
            dao.getAll().mapTo(mutableSetOf()) { it.source },
        )
    }

    @Test
    fun replaceAndGetAll_legacySource_preservesBuiltInAndRemovesOnlyStaleLegacyRows() = runTest {
        dao.insert(
            listOf(
                label(AddressLabelSource.BUILT_IN, "Built-in"),
                label(
                    source = AddressLabelSource.LEGACY_API,
                    value = "Stale",
                    address = STALE_ADDRESS,
                ),
            )
        )

        val storedLabels = dao.replaceAndGetAll(
            AddressLabelSource.LEGACY_API,
            listOf(
                label(
                    source = AddressLabelSource.LEGACY_API,
                    value = "Fresh",
                    address = FRESH_ADDRESS,
                )
            ),
        )

        assertEquals(
            setOf(ADDRESS, FRESH_ADDRESS),
            storedLabels.mapTo(mutableSetOf()) { it.normalizedAddress },
        )
        assertEquals(
            setOf(AddressLabelSource.BUILT_IN, AddressLabelSource.LEGACY_API),
            storedLabels.mapTo(mutableSetOf()) { it.source },
        )
    }

    private fun label(
        source: AddressLabelSource,
        value: String,
        scope: String = SCOPE,
        address: String = ADDRESS,
    ) = AddressLabel(
        scope = scope,
        normalizedAddress = address,
        source = source,
        label = value,
    )

    private companion object {
        const val SCOPE = "binance-smart-chain"
        const val ADDRESS = "0x579fedb9253cca1b3114d5e2fa44f8158d61e436"
        const val STALE_ADDRESS = "0x0000000000000000000000000000000000000001"
        const val FRESH_ADDRESS = "0x0000000000000000000000000000000000000002"
    }
}
