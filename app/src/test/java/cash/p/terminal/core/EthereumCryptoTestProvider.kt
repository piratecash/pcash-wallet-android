package cash.p.terminal.core

import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import java.security.Security

/**
 * Installs EthereumKit's BouncyCastle provider (which carries the custom `ETH-KECCAK-256` digest
 * used by `CryptoUtils.sha3`) for tests that exercise EVM signing.
 *
 * Unit tests share a JVM fork (`forkEvery`). A Robolectric test with Conscrypt enabled — the default
 * everywhere except macOS/aarch64, so on Linux CI — replaces the `"BC"` provider with one lacking
 * `ETH-KECCAK-256`, and a plain `Security.addProvider` is a no-op on the name clash, so the old
 * provider is removed first.
 *
 * Appended last, not inserted at front: `Security` is JVM-global, so a front-inserted BouncyCastle
 * would also back every later `SecureRandom()` in the fork, whose DRBG rejects `nextBytes` calls
 * above 262144 bits. Lookup order doesn't matter here since JCA scans all providers.
 */
internal fun installEthereumCryptoProviderForTest() {
    val provider = InternalBouncyCastleProvider.getInstance()
    Security.removeProvider(provider.name)
    check(Security.addProvider(provider) > 0) {
        "Failed to install ${provider.name} provider for tests"
    }
}
