package cash.p.terminal.trezor.client

/**
 * Thrown when a public key returned by Trezor does not match the requested derivation - e.g. the
 * xpub version is incompatible with the expected BIP purpose. Fail-loud: the wallet is not created
 * rather than silently producing wrong addresses.
 */
class TrezorKeyValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
