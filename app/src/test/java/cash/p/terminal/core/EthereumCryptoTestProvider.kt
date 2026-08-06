package cash.p.terminal.core

import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import java.security.Security

/**
 * Installs EthereumKit's BouncyCastle provider (which carries the custom `ETH-KECCAK-256` digest
 * used by `CryptoUtils.sha3`) for tests that exercise EVM signing.
 *
 * Unit tests share a JVM fork (`forkEvery`). A Robolectric test with Conscrypt enabled — the default
 * everywhere except macOS/aarch64, so on Linux CI — replaces the `"BC"` provider with one lacking
 * `ETH-KECCAK-256`, and a plain `Security.addProvider` is a no-op on the name clash. Dropping the
 * clashing provider first makes the registration succeed.
 *
 * Appended last rather than forced to the front: `java.security.Security` is JVM-global and never
 * rolled back, so a front-inserted BouncyCastle also becomes the provider behind every later
 * `SecureRandom()` in the fork — and its DRBG rejects a single `nextBytes` above 262144 bits.
 * Position does not affect the lookup here, since JCA scans every provider for `ETH-KECCAK-256`.
 */
internal fun installEthereumCryptoProviderForTest() {
    val provider = InternalBouncyCastleProvider.getInstance()
    Security.removeProvider(provider.name)
    check(Security.addProvider(provider) > 0) {
        "Failed to install ${provider.name} provider for tests"
    }
}
