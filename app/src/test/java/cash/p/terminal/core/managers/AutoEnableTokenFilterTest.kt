package cash.p.terminal.core.managers

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ACCOUNT_ID = "acc"

class AutoEnableTokenFilterTest {

    private val ethBlockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
    private val bscBlockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
    private val stellarBlockchain = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val tronBlockchain = Blockchain(BlockchainType.Tron, "Tron", null)
    private val solanaBlockchain = Blockchain(BlockchainType.Solana, "Solana", null)

    private fun token(
        type: TokenType,
        coinName: String,
        coinCode: String,
        coinImage: String? = null,
        decimals: Int = 18,
        blockchain: Blockchain = ethBlockchain,
    ) = Token(
        coin = Coin(
            uid = "$coinCode-uid",
            name = coinName,
            code = coinCode,
            image = coinImage,
        ),
        blockchain = blockchain,
        type = type,
        decimals = decimals,
    )

    /** Mirrors MarketKit: a query resolves to a token, which may carry a different reference. */
    private fun marketKitResolving(vararg resolutions: Pair<TokenQuery, Token>) =
        mockk<MarketKitWrapper> {
            val byQuery = resolutions.toMap()
            every { tokens(any<List<TokenQuery>>()) } answers {
                firstArg<List<TokenQuery>>().mapNotNull { byQuery[it] }
            }
        }

    private suspend fun MarketKitWrapper.curatedIds(storedIds: Collection<String>) =
        curatedEnabledWallets(
            ACCOUNT_ID,
            storedIds.map { StoredToken(it, metadata = null, trustedDecimals = false) },
            approvedTokenQueryIds = emptySet(),
        ).enabled.map { it.tokenQueryId }

    // Distinctive name/code: a leaked stored value would fail loudly instead of coincidentally matching.
    private fun trustedDecimals(decimals: Int?) =
        TrustedTokenMetadata(coinName = "stored-name", coinCode = "STORED", decimals = decimals)

    @Test
    fun filterKnown_typeInKnownTokens_includesItWithMarketKitMetadata() {
        val type = TokenType.Eip20("0xUSDT")
        val known = listOf(token(type, "Tether", "USDT", coinImage = "img-url", decimals = 6))

        val result = filterKnownAutoEnableTokens(listOf(type), known)

        assertEquals(1, result.size)
        val info = result.first()
        assertEquals(type, info.type)
        assertEquals("Tether", info.coinName)
        assertEquals("USDT", info.coinCode)
        assertEquals(6, info.coinDecimals)
        assertEquals("img-url", info.coinImage)
    }

    @Test
    fun filterKnown_typeNotInKnownTokens_dropsIt() {
        val knownType = TokenType.Eip20("0xLEGIT")
        val unknownScamType = TokenType.Eip20("0xSCAM")
        val known = listOf(token(knownType, "Legit", "LGT"))

        val result = filterKnownAutoEnableTokens(
            listOf(knownType, unknownScamType),
            known,
        )

        assertEquals(1, result.size)
        assertEquals(knownType, result.first().type)
    }

    @Test
    fun filterKnown_allTypesUnknown_returnsEmpty() {
        val scamType = TokenType.Eip20("0xSCAM")

        val result = filterKnownAutoEnableTokens(listOf(scamType), emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterKnown_duplicateTypes_returnsDistinct() {
        val type = TokenType.Eip20("0xUSDT")
        val known = listOf(token(type, "Tether", "USDT"))

        val result = filterKnownAutoEnableTokens(listOf(type, type, type), known)

        assertEquals(1, result.size)
    }

    @Test
    fun filterKnown_emptyTokenTypes_returnsEmpty() {
        val known = listOf(token(TokenType.Eip20("0xUSDT"), "Tether", "USDT"))

        val result = filterKnownAutoEnableTokens(emptyList(), known)

        assertTrue(result.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_tokenInMarketKit_keepsIt() = runTest {
        val query = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xUSDT"))
        val marketKit = marketKitResolving(query to token(query.tokenType, "Tether", "USDT"))

        val result = marketKit.curatedIds(listOf(query.id))

        assertEquals(listOf(query.id), result)
    }

    @Test
    fun curatedEnabledWallets_curatedToken_takesMetadataFromTheCatalog() = runTest {
        val query = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xUSDT"))
        val curated = token(query.tokenType, "Tether", "USDT", coinImage = "img-url", decimals = 6)
        val marketKit = marketKitResolving(query to curated)

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(StoredToken(query.id, metadata = null, trustedDecimals = false)),
            approvedTokenQueryIds = emptySet()
        ).enabled.single()

        assertEquals(ACCOUNT_ID, wallet.accountId)
        assertEquals("Tether", wallet.coinName)
        assertEquals("USDT", wallet.coinCode)
        assertEquals(6, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun curatedEnabledWallets_tokenAbsentFromMarketKit_dropsIt() = runTest {
        val legit = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xLEGIT"))
        val scam = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xSCAM"))
        val marketKit = marketKitResolving(legit to token(legit.tokenType, "Legit", "LGT"))

        val result = marketKit.curatedIds(listOf(legit.id, scam.id))

        assertEquals(listOf(legit.id), result)
    }

    @Test
    fun curatedEnabledWallets_sameContractOnAnotherChain_dropsIt() = runTest {
        val address = "0xsame"
        val onEthereum = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20(address))
        val onBsc = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20(address))
        val marketKit = marketKitResolving(onEthereum to token(onEthereum.tokenType, "Same", "SAME"))

        val result = marketKit.curatedIds(listOf(onEthereum.id, onBsc.id))

        assertEquals(listOf(onEthereum.id), result)
    }

    @Test
    fun curatedEnabledWallets_nativeCoinInMarketKit_keepsIt() = runTest {
        val query = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native)
        val marketKit = marketKitResolving(
            query to token(TokenType.Native, "BNB", "BNB", blockchain = bscBlockchain)
        )

        val result = marketKit.curatedIds(listOf(query.id))

        assertEquals(listOf(query.id), result)
    }

    // MarketKit's suffix fallback resolves an old checksummed reference to the lowercase curated token.
    @Test
    fun curatedEnabledWallets_checksummedReference_rewritesItToTheCuratedId() = runTest {
        val stored = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xAbCd"))
        val canonical = token(TokenType.Eip20("0xabcd"), "Canonical", "CAN", blockchain = bscBlockchain)
        val marketKit = marketKitResolving(stored to canonical)

        val result = marketKit.curatedIds(listOf(stored.id))

        assertEquals(listOf(canonical.tokenQuery.id), result)
    }

    // TokenType.Unsupported marks a catalog row without verified decimals.
    @Test
    fun curatedEnabledWallets_curatedTokenTypeUnsupportedNoTrustedDecimals_dropsIt() = runTest {
        val address = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3"
        val stored = TokenQuery(BlockchainType.Solana, TokenType.Spl(address))
        val curated = token(
            TokenType.Unsupported("spl", address), "Pyth Network", "PYTH",
            decimals = 0, blockchain = solanaBlockchain,
        )
        val marketKit = marketKitResolving(stored to curated)

        val result = marketKit.curatedIds(listOf(stored.id))

        assertTrue(result.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_curatedTokenTypeUnsupportedWithTrustedDecimals_keepsItWithTrustedDecimals() = runTest {
        val address = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3"
        val stored = TokenQuery(BlockchainType.Solana, TokenType.Spl(address))
        val curated = token(
            TokenType.Unsupported("spl", address), "Pyth Network", "PYTH",
            decimals = 0, blockchain = solanaBlockchain,
        )
        val marketKit = marketKitResolving(stored to curated)

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(stored.id, metadata = trustedDecimals(6), trustedDecimals = true)),
            approvedTokenQueryIds = emptySet(),
        ).enabled.single()

        assertEquals(stored.id, wallet.tokenQueryId)
        assertEquals(6, wallet.coinDecimals)
    }

    @Test
    fun curatedEnabledWallets_manuallyAddedTokenTrustedDecimals_landsInDeclinedNotEnabled() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, TrustedTokenMetadata("My Token", "MYT", 8), trustedDecimals = true)),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        val declined = result.declined.single()
        assertEquals("ethereum|eip20:0xcustom", declined.tokenQueryId)
        assertEquals("My Token", declined.coinName)
        assertEquals("MYT", declined.coinCode)
        assertEquals(8, declined.decimals)
    }

    @Test
    fun curatedEnabledWallets_manuallyAddedTokenFromUntrustedSource_dropsIt() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(StoredToken(custom.id, metadata = null, trustedDecimals = false)),
            approvedTokenQueryIds = emptySet()
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_trustedRowMissingDecimals_dropsIt() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken(custom.id, TrustedTokenMetadata("My Token", "MYT", decimals = null), trustedDecimals = true)
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_trustedRowWithNonRoundTrippingId_dropsIt() = runTest {
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken("solana|unsupported:spl:ref", TrustedTokenMetadata("X", "X", 6), trustedDecimals = true)
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_nonCatalogRowWithUntrustedDecimals_landsInDeclinedNotEnabled() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, TrustedTokenMetadata("My Token", "MYT", 8), trustedDecimals = false)),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        val declined = result.declined.single()
        assertEquals("My Token", declined.coinName)
        assertEquals("MYT", declined.coinCode)
        assertEquals(8, declined.decimals)
    }

    @Test
    fun curatedEnabledWallets_nonCatalogRowApproved_landsInEnabledWithRowMetadata() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()
        val stored = StoredToken(custom.id, TrustedTokenMetadata("My Token", "MYT", 8), trustedDecimals = false)
        val declinedId = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(stored), approvedTokenQueryIds = emptySet()
        ).declined.single().tokenQueryId

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(stored), approvedTokenQueryIds = setOf(declinedId)
        )

        assertTrue(result.declined.isEmpty())
        val wallet = result.enabled.single()
        assertEquals(declinedId, wallet.tokenQueryId)
        assertEquals("My Token", wallet.coinName)
        assertEquals("MYT", wallet.coinCode)
        assertEquals(8, wallet.coinDecimals)
    }

    @Test
    fun curatedEnabledWallets_nonCatalogRowMissingDecimals_landsInNeitherList() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xCUSTOM"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken(
                    custom.id, TrustedTokenMetadata("My Token", "MYT", decimals = null), trustedDecimals = false
                )
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_nonCatalogRowWithNonRoundTrippingId_landsInNeitherList() = runTest {
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken("solana|unsupported:spl:ref", TrustedTokenMetadata("X", "X", 6), trustedDecimals = false)
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_approvalMatchesAcrossEvmCasing_landsInEnabled() = runTest {
        val checksummedStored = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xAbCd"))
        val canonicalId = TokenQuery.eip20(BlockchainType.Ethereum, "0xAbCd").id
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(checksummedStored.id, TrustedTokenMetadata("Token", "TKN", 8), trustedDecimals = false)),
            approvedTokenQueryIds = setOf(canonicalId),
        )

        assertTrue(result.declined.isEmpty())
        assertEquals(canonicalId, result.enabled.single().tokenQueryId)
    }

    @Test
    fun curatedEnabledWallets_twoCaseVariantsWithConflictingMetadata_declinesExactlyOneWithFirstRowsMetadata() =
        runTest {
            val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xAbCd"))
            val lowercased = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xabcd"))
            val marketKit = marketKitResolving()

            val result = marketKit.curatedEnabledWallets(
                ACCOUNT_ID,
                listOf(
                    StoredToken(checksummed.id, TrustedTokenMetadata("First", "FST", 8), trustedDecimals = false),
                    StoredToken(lowercased.id, TrustedTokenMetadata("Second", "SND", 10), trustedDecimals = false),
                ),
                approvedTokenQueryIds = emptySet(),
            )

            assertTrue(result.enabled.isEmpty())
            val declined = result.declined.single()
            assertEquals("First", declined.coinName)
            assertEquals("FST", declined.coinCode)
        }

    @Test
    fun curatedEnabledWallets_firstCaseVariantUnrestorableSecondRestorable_keepsSecondDeclined() = runTest {
        val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xAbCd"))
        val lowercased = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xabcd"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken(
                    checksummed.id, TrustedTokenMetadata("First", "FST", decimals = null), trustedDecimals = false
                ),
                StoredToken(lowercased.id, TrustedTokenMetadata("Second", "SND", 10), trustedDecimals = false),
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        val declined = result.declined.single()
        assertEquals("Second", declined.coinName)
        assertEquals("SND", declined.coinCode)
    }

    @Test
    fun curatedEnabledWallets_unusualDecimalsValues_stayRestorableAndLandInDeclined() = runTest {
        val large = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xLARGE"))
        val negative = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xNEG"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(
                StoredToken(large.id, TrustedTokenMetadata("Large", "LRG", 37), trustedDecimals = false),
                StoredToken(negative.id, TrustedTokenMetadata("Negative", "NEG", -5), trustedDecimals = false),
            ),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertEquals(setOf("Large", "Negative"), result.declined.map { it.coinName }.toSet())
    }

    // "\n" becomes blank once control characters are stripped.
    @Test
    fun curatedEnabledWallets_blankNameAndCode_landsInNeitherList() = runTest {
        val custom = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xBLANK"))
        val marketKit = marketKitResolving()

        val result = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(custom.id, TrustedTokenMetadata("\n", "", 8), trustedDecimals = false)),
            approvedTokenQueryIds = emptySet(),
        )

        assertTrue(result.enabled.isEmpty())
        assertTrue(result.declined.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_nonUnsupportedTypeWithDifferentTrustedDecimals_persistsCatalogDecimals() = runTest {
        val query = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xUSDT"))
        val curated = token(query.tokenType, "Tether", "USDT", decimals = 6)
        val marketKit = marketKitResolving(query to curated)

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID,
            listOf(StoredToken(query.id, metadata = trustedDecimals(18), trustedDecimals = true)),
            approvedTokenQueryIds = emptySet(),
        ).enabled.single()

        assertEquals(6, wallet.coinDecimals)
    }

    @Test
    fun curatedEnabledWallets_twoCaseVariantsOfUnsupportedTypeToken_dropsBoth() = runTest {
        val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xAbCd"))
        val lowercased = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xabcd"))
        val curated = token(TokenType.Unsupported("eip20", "0xabcd"), "Unlisted", "UNL", decimals = 0)
        val marketKit = marketKitResolving(checksummed to curated, lowercased to curated)

        val result = marketKit.curatedIds(listOf(checksummed.id, lowercased.id))

        assertTrue(result.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_twoCaseVariantsOfOneContract_yieldsOneCuratedRow() = runTest {
        val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xAbCd"))
        val lowercased = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xabcd"))
        val canonical = token(TokenType.Eip20("0xabcd"), "Canonical", "CAN")
        val marketKit = marketKitResolving(checksummed to canonical, lowercased to canonical)

        val result = marketKit.curatedIds(listOf(checksummed.id, lowercased.id))

        assertEquals(listOf(lowercased.id), result)
    }

    // MarketKit's LIKE '%reference' fallback matches a truncated reference against any curated address ending in it.
    @Test
    fun curatedEnabledWallets_truncatedReferenceSuffixMatchesCuratedToken_dropsIt() = runTest {
        val truncated = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("b78"))
        val curated = token(TokenType.Eip20("0xb9ef770b6a5e12e45983c5d80545258aa38f3b78"), "0chain", "ZCN")
        val marketKit = marketKitResolving(truncated to curated)

        val result = marketKit.curatedIds(listOf(truncated.id))

        assertTrue(result.isEmpty())
    }

    // SQLite LIKE ignores ASCII case, so a lowercased Stellar issuer still matches the case-significant curated asset.
    @Test
    fun curatedEnabledWallets_stellarReferenceCaseVariant_dropsIt() = runTest {
        val issuer = "GBX6YI45VU7WNAAKA3RBFDR3I3UKNFHTJPQ5F6KOOKSGYIAM4TRQN54W"
        val stored = TokenQuery(BlockchainType.Stellar, TokenType.Asset("afr", issuer.lowercase()))
        val curated = token(
            TokenType.Asset("AFR", issuer), "Afreum", "AFR",
            decimals = 7, blockchain = stellarBlockchain,
        )
        val marketKit = marketKitResolving(stored to curated)

        val result = marketKit.curatedIds(listOf(stored.id))

        assertTrue(result.isEmpty())
    }

    // Tron uses the eip20 token type too, but its base58 addresses are case-significant, unlike EVM.
    @Test
    fun curatedEnabledWallets_tronContractCaseVariant_dropsIt() = runTest {
        val stored = TokenQuery(BlockchainType.Tron, TokenType.Eip20("tfczxzphnthnsqr5by8tvxsdcfrrz6cpnq"))
        val curated = token(
            TokenType.Eip20("TFczxzPhnThNSqr5by8tvxsdCFRRz6cPNq"), "Tether", "USDT",
            decimals = 6, blockchain = tronBlockchain,
        )
        val marketKit = marketKitResolving(stored to curated)

        val result = marketKit.curatedIds(listOf(stored.id))

        assertTrue(result.isEmpty())
    }

    // Legacy "zcash|native" isn't in MarketKit's catalog, but WalletStorage still expands it into the live Zcash
    // wallet group.
    @Test
    fun curatedEnabledWallets_legacyZcashNativeAbsentFromMarketKit_keepsItWithoutMetadata() = runTest {
        val legacyZcash = TokenQuery(BlockchainType.Zcash, TokenType.Native)
        val marketKit = marketKitResolving()

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(StoredToken(legacyZcash.id, metadata = null, trustedDecimals = false)),
            approvedTokenQueryIds = emptySet()
        ).enabled.single()

        assertEquals(legacyZcash.id, wallet.tokenQueryId)
        assertNull(wallet.coinName)
        assertNull(wallet.coinCode)
        assertNull(wallet.coinDecimals)
        assertNull(wallet.coinImage)
    }

    // TokenType.fromId ignores anything past the first ":" segment, so a noisy suffix still parses as legacy-Zcash
    // native.
    @Test
    fun curatedEnabledWallets_legacyZcashNoisyId_persistsCanonicalId() = runTest {
        val marketKit = marketKitResolving()

        val wallet = marketKit.curatedEnabledWallets(
            ACCOUNT_ID, listOf(StoredToken("zcash|native:ignored", metadata = null, trustedDecimals = false)),
            approvedTokenQueryIds = emptySet()
        ).enabled.single()

        assertEquals("zcash|native", wallet.tokenQueryId)
    }

    // TokenType.fromId reads only the first two ":" segments, so an appended segment is silently dropped when reparsed.
    @Test
    fun curatedEnabledWallets_storedIdWithInjectedSuffixResolvesToUnsupportedType_dropsIt() = runTest {
        val address = "0x2598c30330d5771ae9f983979209486ae26de875"
        val canonical = TokenQuery(BlockchainType.Base, TokenType.Eip20(address))
        val curated = token(
            TokenType.Unsupported("eip20", address), "Any Inu", "ANYINU",
            decimals = 0, blockchain = Blockchain(BlockchainType.Base, "Base", null),
        )
        val marketKit = marketKitResolving(canonical to curated)

        val result = marketKit.curatedIds(listOf("base|eip20:$address:INJECTED"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_manyDuplicateIds_resolvesInBoundedDeduplicatedBatches() = runTest {
        val queries = List(700) { TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0x$it")) }
        val batches = mutableListOf<List<TokenQuery>>()
        val marketKit = mockk<MarketKitWrapper> {
            every { tokens(any<List<TokenQuery>>()) } answers {
                batches.add(firstArg())
                emptyList()
            }
        }

        val result = marketKit.curatedIds((queries + queries).map { it.id })

        assertTrue(result.isEmpty())
        assertEquals(queries.size, batches.sumOf { it.size })
        assertEquals(3, batches.size)
        assertTrue(batches.all { it.size <= 300 })
    }

    @Test
    fun curatedEnabledWallets_curatedTokensInDifferentChunks_keepsAllOfThem() = runTest {
        val padding = List(400) { TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xpad$it")) }
        val firstChunk = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xaaa"))
        val lastChunk = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xbbb"))
        val marketKit = marketKitResolving(
            firstChunk to token(firstChunk.tokenType, "First", "AAA"),
            lastChunk to token(lastChunk.tokenType, "Last", "BBB"),
        )

        val result = marketKit.curatedIds(
            (listOf(firstChunk) + padding + lastChunk).map { it.id }
        )

        assertEquals(listOf(firstChunk.id, lastChunk.id), result)
    }

    // Litecoin's native and MWEB wallets share chain and an empty reference — only the token type differs.
    @Test
    fun curatedEnabledWallets_sameChainOtherTokenType_dropsIt() = runTest {
        val litecoin = Blockchain(BlockchainType.Litecoin, "Litecoin", null)
        val native = TokenQuery(BlockchainType.Litecoin, TokenType.Native)
        val marketKit = marketKitResolving(
            native to token(TokenType.Mweb, "Litecoin", "LTC", blockchain = litecoin)
        )

        val result = marketKit.curatedIds(listOf(native.id))

        assertTrue(result.isEmpty())
    }

    @Test
    fun curatedEnabledWallets_unparsableId_dropsIt() = runTest {
        val marketKit = marketKitResolving()

        val result = marketKit.curatedIds(listOf("not-a-token-query-id"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun toEnabledWallets_emptyInput_returnsEmpty() = runTest {
        val userDeleted = mockk<UserDeletedWalletManager>()

        val result = emptyList<AutoEnableTokenInfo>().toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun toEnabledWallets_oneInfo_buildsOneWalletWithMetadata() = runTest {
        val type = TokenType.Eip20("0xUSDT")
        val info = AutoEnableTokenInfo(
            type = type,
            coinName = "Tether",
            coinCode = "USDT",
            coinDecimals = 6,
            coinImage = "img-url",
        )
        val userDeleted = mockk<UserDeletedWalletManager> {
            coEvery { isDeletedByUser(any(), any()) } returns false
        }

        val result = listOf(info).toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertEquals(1, result.size)
        val wallet = result.single()
        assertEquals(TokenQuery(BlockchainType.Ethereum, type).id, wallet.tokenQueryId)
        assertEquals("acc", wallet.accountId)
        assertEquals("Tether", wallet.coinName)
        assertEquals("USDT", wallet.coinCode)
        assertEquals(6, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun toEnabledWallets_deletedByUser_skipsThatInfo() = runTest {
        val keepType = TokenType.Eip20("0xKEEP")
        val skipType = TokenType.Eip20("0xSKIP")
        val infos = listOf(
            AutoEnableTokenInfo(keepType, "Keep", "KEP", 18, null),
            AutoEnableTokenInfo(skipType, "Skip", "SKP", 18, null),
        )
        val userDeleted = mockk<UserDeletedWalletManager> {
            coEvery { isDeletedByUser("acc", TokenQuery(BlockchainType.Ethereum, keepType).id) } returns false
            coEvery { isDeletedByUser("acc", TokenQuery(BlockchainType.Ethereum, skipType).id) } returns true
        }

        val result = infos.toEnabledWallets(
            accountId = "acc",
            blockchainType = BlockchainType.Ethereum,
            userDeletedWalletManager = userDeleted,
        )

        assertEquals(1, result.size)
        assertEquals(TokenQuery(BlockchainType.Ethereum, keepType).id, result.single().tokenQueryId)
    }
}
