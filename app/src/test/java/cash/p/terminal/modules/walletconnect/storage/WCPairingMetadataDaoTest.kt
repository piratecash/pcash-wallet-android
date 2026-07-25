package cash.p.terminal.modules.walletconnect.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WCPairingMetadataDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WCPairingMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.wcPairingMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_getByTopics_returnsSavedRow() {
        dao.insert(metadata("topic-1", name = "Near", icon = "https://near/icon.png"))

        val result = dao.getByTopics(listOf("topic-1"))

        assertEquals(1, result.size)
        assertEquals("Near", result.single().name)
        assertEquals("https://near/icon.png", result.single().icon)
    }

    @Test
    fun insert_sameTopicTwice_replacesRow() {
        dao.insert(metadata("topic-1", name = "Old"))
        dao.insert(metadata("topic-1", name = "New"))

        val result = dao.getByTopics(listOf("topic-1"))

        assertEquals(1, result.size)
        assertEquals("New", result.single().name)
    }

    @Test
    fun insert_nullIcon_persistsNull() {
        dao.insert(metadata("topic-1", icon = null))

        assertNull(dao.getByTopics(listOf("topic-1")).single().icon)
    }

    @Test
    fun deleteAllExcept_keepsOnlyListedTopics() {
        dao.insert(metadata("keep"))
        dao.insert(metadata("drop"))

        dao.deleteAllExcept(listOf("keep"))

        val remaining = dao.getByTopics(listOf("keep", "drop"))
        assertEquals(1, remaining.size)
        assertEquals("keep", remaining.single().topic)
    }

    @Test
    fun getByTopics_multipleTopics_returnsOnlyMatching() {
        dao.insert(metadata("a"))
        dao.insert(metadata("b"))
        dao.insert(metadata("c"))

        val result = dao.getByTopics(listOf("a", "c")).map { it.topic }.sorted()

        assertEquals(listOf("a", "c"), result)
    }

    private fun metadata(
        topic: String,
        name: String = "dApp",
        url: String = "https://dapp.example",
        icon: String? = "https://dapp.example/icon.png",
    ) = WalletConnectV2PairingMetadata(topic = topic, name = name, url = url, icon = icon)
}
