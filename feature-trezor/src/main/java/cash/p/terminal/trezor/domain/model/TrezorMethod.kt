package cash.p.terminal.trezor.domain.model

enum class TrezorMethod(val value: String) {
    // Device
    GetFeatures("getFeatures"),
    GetCoinInfo("getCoinInfo"),

    // Bitcoin (generic names — bitcoin is the default namespace)
    BtcAuthorizeCoinjoin("authorizeCoinjoin"),
    BtcCancelCoinjoinAuthorization("cancelCoinjoinAuthorization"),
    BtcComposeTransaction("composeTransaction"),
    BtcGetAccountInfo("getAccountInfo"),
    BtcGetAddress("getAddress"),
    BtcGetPublicKey("getPublicKey"),
    BtcPushTransaction("pushTransaction"),
    BtcSignMessage("signMessage"),
    BtcSignTransaction("signTransaction"),
    BtcVerifyMessage("verifyMessage"),

    // Ethereum / EVM
    EthGetAddress("ethereumGetAddress"),
    EthGetPublicKey("ethereumGetPublicKey"),
    EthSignMessage("ethereumSignMessage"),
    EthSignTransaction("ethereumSignTransaction"),
    EthSignTypedData("ethereumSignTypedData"),
    EthVerifyMessage("ethereumVerifyMessage"),

    // Solana
    SolComposeTransaction("solanaComposeTransaction"),
    SolGetAddress("solanaGetAddress"),
    SolGetPublicKey("solanaGetPublicKey"),
    SolSignTransaction("solanaSignTransaction"),

    // Stellar
    XlmGetAddress("stellarGetAddress"),
    XlmSignTransaction("stellarSignTransaction"),

    // Tron
    TrxGetAddress("tronGetAddress"),
    TrxSignTransaction("tronSignTransaction"),

    // Monero
    XmrGetAddress("moneroGetAddress"),
    XmrGetWatchKey("moneroGetWatchKey"),
    XmrKeyImageSync("moneroKeyImageSync"),
    XmrSignTransaction("moneroSignTransaction"),

    // Cardano
    AdaComposeTransaction("cardanoComposeTransaction"),
    AdaGetAddress("cardanoGetAddress"),
    AdaGetNativeScriptHash("cardanoGetNativeScriptHash"),
    AdaGetPublicKey("cardanoGetPublicKey"),
    AdaSignMessage("cardanoSignMessage"),
    AdaSignTransaction("cardanoSignTransaction"),

    // Binance
    BnbGetAddress("binanceGetAddress"),
    BnbGetPublicKey("binanceGetPublicKey"),
    BnbSignTransaction("binanceSignTransaction"),

    // Ripple
    XrpGetAddress("rippleGetAddress"),
    XrpSignTransaction("rippleSignTransaction"),

    // Tezos
    XtzGetAddress("tezosGetAddress"),
    XtzGetPublicKey("tezosGetPublicKey"),
    XtzSignTransaction("tezosSignTransaction"),

    // Eos
    EosGetPublicKey("eosGetPublicKey"),
    EosSignTransaction("eosSignTransaction"),

    // Nem
    NemGetAddress("nemGetAddress"),
    NemSignTransaction("nemSignTransaction"),
}
