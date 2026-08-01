package cash.p.terminal.modules.multiswap

enum class SwapAmountDirection {
    In,
    Out,
}

enum class SwapExecutionMode {
    ExactIn,
    NativeExactOut,
}

enum class SwapAmountAccuracy {
    Exact,
    AtLeast,
    Estimated,
}
