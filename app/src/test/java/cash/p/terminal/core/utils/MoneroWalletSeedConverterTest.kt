package cash.p.terminal.core.utils

import cash.p.terminal.wallet.normalizeNFKD
import com.m2049r.xmrwallet.util.ledger.Monero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroWalletSeedConverterTest {

    private val moneroWordSet by lazy { Monero.ENGLISH_WORDS.toSet() }

    @Test
    fun test12To25Convert() {
        val bip39Seed =
            "meadow tip best belt boss eyebrow control affair eternal piece very shiver".split(" ")
        val expectedLegacySeed0 =
            "tasked eight afraid laboratory tail feline rift reinvest vane cafe bailed foggy dormant paper jigsaw king hazard suture king dapper dummy jolted dating dwindling king"
                .split(" ")
        val expectedLegacySeed1 =
            "palace pairing axes mohawk rekindle excess awful juvenile shipped talent nibs efficient dapper biggest swung fight pact innocent emerge issued titans affair nearby noises emerge"
                .split(" ")

        val legacySeed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(bip39Seed, accountIndex = 0)
        assertEquals(expectedLegacySeed0, legacySeed)

        val legacySeed1 =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(bip39Seed, accountIndex = 1)
        assertEquals(expectedLegacySeed1, legacySeed1)
    }

    // ==================== Non-English BIP39 input fixtures (#8) ====================
    //
    // BIP39 all-zero entropy 12-word fixtures (canonical "all zeros" mnemonic per language):
    // 11 × wordlist[0] + wordlist[3] (checksum index for entropy=0x00*16 is 3, since
    // SHA256(0x00*16)[0] = 0x37 = 0b00110111, top 4 bits = 0b0011 = 3).
    //
    // These tests verify the conversion is BIP39-language-agnostic on the input side and
    // always produces deterministic output drawn from the Monero English wordlist.

    @Test
    fun getLegacySeedFromBip39_japaneseAllZeroEntropy_producesValidMoneroSeed() {
        val input = (List(11) { "あいこくしん" } + "あおぞら").map { it.normalizeNFKD() }

        val moneroSeed = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)

        assertMoneroSeedShape(moneroSeed)
    }

    @Test
    fun getLegacySeedFromBip39_simplifiedChineseAllZeroEntropy_producesValidMoneroSeed() {
        val input = (List(11) { "的" } + "在").map { it.normalizeNFKD() }

        val moneroSeed = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)

        assertMoneroSeedShape(moneroSeed)
    }

    @Test
    fun getLegacySeedFromBip39_koreanAllZeroEntropy_producesValidMoneroSeed() {
        val input = (List(11) { "가격" } + "가능").map { it.normalizeNFKD() }

        val moneroSeed = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)

        assertMoneroSeedShape(moneroSeed)
    }

    @Test
    fun getLegacySeedFromBip39_spanishAllZeroEntropy_producesValidMoneroSeed() {
        val input = (List(11) { "ábaco" } + "abierto").map { it.normalizeNFKD() }

        val moneroSeed = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)

        assertMoneroSeedShape(moneroSeed)
    }

    @Test
    fun getLegacySeedFromBip39_japaneseInput_isDeterministic() {
        val input = (List(11) { "あいこくしん" } + "あおぞら").map { it.normalizeNFKD() }

        val first = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)
        val second = MoneroWalletSeedConverter.getLegacySeedFromBip39(input, accountIndex = 0)

        assertEquals("Same input must produce same output", first, second)
    }

    @Test
    fun getLegacySeedFromBip39_differentLanguages_produceDifferentMoneroSeeds() {
        // Different BIP39 wordlists encoding "all-zero entropy" intentionally produce
        // different bytes through PBKDF2 — they are different mnemonic sentences.
        val englishInput = (List(11) { "abandon" } + "about").map { it.normalizeNFKD() }
        val japaneseInput = (List(11) { "あいこくしん" } + "あおぞら").map { it.normalizeNFKD() }

        val englishSeed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(englishInput, accountIndex = 0)
        val japaneseSeed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(japaneseInput, accountIndex = 0)

        assertTrue(
            "Different BIP39 languages must yield different Monero seeds",
            englishSeed != japaneseSeed
        )
    }

    @Test
    fun getLegacySeedFromBip39_precomposedAndDecomposedSpanish_produceSameSeed() {
        // BIP39 spec mandates NFKD on the mnemonic before PBKDF2. After NFKD,
        // precomposed "á" (U+00E1) and decomposed "a" + COMBINING ACUTE (U+0301) must
        // collapse to identical bytes — otherwise users with different keyboard inputs
        // would silently land on different wallets.
        val precomposedAcute = "á"             // á
        val decomposedAcute = "á"             // a + ◌́
        check(precomposedAcute != decomposedAcute) {
            "Test inputs must differ before NFKD"
        }

        val precomposed = (List(11) { "${precomposedAcute}baco" } + "abierto")
            .map { it.normalizeNFKD() }
        val decomposed = (List(11) { "${decomposedAcute}baco" } + "abierto")
            .map { it.normalizeNFKD() }

        val seedFromPrecomposed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(precomposed, accountIndex = 0)
        val seedFromDecomposed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(decomposed, accountIndex = 0)

        assertEquals(
            "Precomposed and decomposed Spanish input must yield the same Monero seed",
            seedFromPrecomposed,
            seedFromDecomposed
        )
    }

    @Test
    fun getLegacySeedFromBip39_unnormalizedDecomposedInput_yieldsDifferentSeed() {
        // Reverse contract: if the caller skips NFKD, decomposed input MUST drift away
        // from precomposed (PBKDF2 hashes raw UTF-8 bytes). This test guards the KDoc
        // contract — it's the user-visible failure mode the comment warns about.
        val precomposed = List(11) { "ábaco" } + "abierto"           // skipped NFKD
        val decomposed = List(11) { "ábaco" } + "abierto"           // skipped NFKD

        val seedFromPrecomposed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(precomposed, accountIndex = 0)
        val seedFromDecomposed =
            MoneroWalletSeedConverter.getLegacySeedFromBip39(decomposed, accountIndex = 0)

        assertNotEquals(
            "Without NFKD, precomposed and decomposed inputs MUST differ — proves the " +
                    "normalization contract is load-bearing, not cosmetic.",
            seedFromPrecomposed,
            seedFromDecomposed
        )
    }

    private fun assertMoneroSeedShape(moneroSeed: List<String>) {
        assertEquals("Monero seed must have exactly 25 words", 25, moneroSeed.size)
        moneroSeed.forEach { word ->
            assertTrue(
                "Word '$word' must be in Monero English wordlist",
                word in moneroWordSet
            )
        }
    }
}
