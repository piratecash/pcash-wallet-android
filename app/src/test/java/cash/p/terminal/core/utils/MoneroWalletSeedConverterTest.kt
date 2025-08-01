package cash.p.terminal.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneroWalletSeedConverterTest {
    @Test
    fun test12To25Convert() {
        val bip39Seed = "meadow tip best belt boss eyebrow control affair eternal piece very shiver".split(" ")
        val expectedLegacySeed0 =
            "tasked eight afraid laboratory tail feline rift reinvest vane cafe bailed foggy dormant paper jigsaw king hazard suture king dapper dummy jolted dating dwindling king"
                .split(" ")
        val expectedLegacySeed1 =
            "palace pairing axes mohawk rekindle excess awful juvenile shipped talent nibs efficient dapper biggest swung fight pact innocent emerge issued titans affair nearby noises emerge"
                .split(" ")

        val legacySeed = MoneroWalletSeedConverter.getLegacySeedFromBip39(bip39Seed, accountIndex = 0)
        assertEquals(expectedLegacySeed0, legacySeed)

        val legacySeed1 = MoneroWalletSeedConverter.getLegacySeedFromBip39(bip39Seed, accountIndex = 1)
        assertEquals(expectedLegacySeed1, legacySeed1)
    }

}