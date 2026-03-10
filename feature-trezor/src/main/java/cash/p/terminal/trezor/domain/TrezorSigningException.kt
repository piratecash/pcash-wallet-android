package cash.p.terminal.trezor.domain

open class TrezorSigningException(message: String) : Exception(message)

class TrezorCancelledException : TrezorSigningException(
    "Trezor operation cancelled by user"
)

class TrezorSuiteNotInstalledException : TrezorSigningException(
    "Trezor Suite is not installed or disabled"
)
