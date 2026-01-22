package cash.p.terminal.premium.data.config

object PremiumConfig {
    const val PIRATE_CONTRACT_ADDRESS = "0xaFCC12e4040615E7Afe9fb4330eB3D9120acAC05"
    const val COSANTA_CONTRACT_ADDRESS = "0x5F980533B994c93631A639dEdA7892fC49995839"

    const val COIN_TYPE_PIRATE = "PIRATE"
    const val COIN_TYPE_COSANTA = "COSA"

    const val MIN_PREMIUM_AMOUNT_PIRATE = 10_000
    const val MIN_PREMIUM_AMOUNT_COSANTA = 100

    internal const val PREMIUM_CHECK_INTERVAL = 1000L * 60 * 5 // 5 min
}