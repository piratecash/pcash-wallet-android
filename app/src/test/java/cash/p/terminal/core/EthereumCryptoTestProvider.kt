package cash.p.terminal.core

import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import java.security.Security

/**
 * Installs EthereumKit's BouncyCastle provider (which carries the custom `ETH-KECCAK-256` digest
 * used by `CryptoUtils.sha3`) for tests that exercise EVM signing.
 *
 * Unit tests share a JVM fork (`forkEvery`). A Robolectric test with Conscrypt enabled — the default
 * everywhere except macOS/aarch64, so on Linux CI — replaces the `"BC"` provider with one lacking
 * `ETH-KECCAK-256`. A plain `Security.addProvider` is then a no-op on the name clash, and later EVM
 * tests fail with `NoSuchAlgorithmException`. Force our provider to the front instead.
 */
internal fun installEthereumCryptoProviderForTest() {
    val provider = InternalBouncyCastleProvider.getInstance()
    Security.removeProvider(provider.name)
    check(Security.insertProviderAt(provider, 1) > 0) {
        "Failed to install ${provider.name} provider for tests"
    }
}
