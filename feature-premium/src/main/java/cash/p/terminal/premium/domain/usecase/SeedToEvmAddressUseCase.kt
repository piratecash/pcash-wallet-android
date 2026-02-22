package cash.p.terminal.premium.domain.usecase

import io.horizontalsystems.hdwalletkit.Mnemonic
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.HDPath.parsePath
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.math.BigInteger

class SeedToEvmAddressUseCase {

    operator fun invoke(
        words: List<String>,
        passphrase: String = "",
        accountIndex: Int = 0
    ): String {
        val seed = Mnemonic().toSeed(words, passphrase)
        val privateKey = derivePath(seed, "m/44'/60'/0'/0/$accountIndex")
        val publicKey = generatePublicKey(privateKey.privKeyBytes)
        return generateEvmAddress(publicKey)
    }

    fun generateEvmAddress(publicKey: ByteArray): String {
        val pubKeyForHash = if (publicKey.size == 65 && publicKey[0] == 0x04.toByte()) {
            publicKey.sliceArray(1..64)
        } else if (publicKey.size == 64) {
            // Уже без префикса
            publicKey
        } else {
            throw IllegalArgumentException("Invalid public key format for Ethereum address generation")
        }

        val keccak = Keccak.Digest256()
        val hash = keccak.digest(pubKeyForHash)

        val addressBytes = hash.sliceArray(12..31)

        return "0x" + addressBytes.joinToString("") {
            String.format("%02x", it.toInt() and 0xFF)
        }
    }

    private fun generatePublicKey(privateKey: ByteArray): ByteArray {
        val ecKey = ECKey.fromPrivate(BigInteger(1, privateKey))
        return ecKey.pubKeyPoint.getEncoded(false)
    }

    private fun derivePath(seed: ByteArray, path: String): DeterministicKey {
        val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)
        val pathParts = parsePath(path.replace("'", "H"))

        var currentKey = masterKey
        for (childNumber in pathParts.list()) {
            currentKey = HDKeyDerivation.deriveChildKey(currentKey, childNumber)
        }

        return currentKey
    }
}