package cash.p.terminal.network.data

import org.koin.core.component.KoinComponent
import org.koin.core.component.get

// generated file — do not edit manually
object EncodedSecrets : KoinComponent {
    private val decoder by lazy { get<Decoder>() }

    val OPEN_SEA_API_KEY = decoder.decode(listOf(
        "EgUDF14dRkQDX1oGGUVUEEtCV0MGVFddWkVREksTTFs="
    ))
    val TWITTER_BEARER_TOKEN = decoder.decode(listOf(
        "MSIgMilsMTQjLSgibDEgMjI2LjMlKwkMIwUgNW8xbylFGSYTM0UQYTshEBQgIh4bCiAKJS5LVi0qGSw="
    ))
    val BSCSCAN_KEY = decoder.decode(listOf(
        "OSonPS0URz8xNi8qFSBXPSA1KiMzKFk+Nzg8Jh8hFjEpIA==",
        "RTJZIiEVSCAnVF0tGCBXRkUxNkApLCctPCtcPHsgdF4jNFwrMyVZaCRAVzkvO2chLCJAPzowJ1AkPVUqMUd0JBw5Jlc9Tyg2UWYxLS9aOFtmOypCJTY1IycxPlslSjRNaiNtMDtVPi5NOjx7JD9UPCtaFDQsOzomOCEpVToiPSUrLWkmaT46KDQrMg=="
    ))
    val ETHERSCAN_KEY = decoder.decode(listOf(
        "NygvOzB5QkcnKF4zfyYiOCk2OzQ+MCpYNDtSMmVJbykqJA==",
        "OSY5JyoUIjBVITw1HyUwSitFXEo2MV9dWzsgPmxBZF07MlxRViBce0MyOyYuLm4gNiIpRTtGNydXWlhHNDZ/KX9bNShHTzg4XGY1NFE4KC1gSCopRj1ZN1YwXzMkP1Mtaj0WPDYgMi4="
    ))
    val POLYGONSCAN_KEY = decoder.decode(listOf(
        "JC0wR1xvMzNTITpQfkdUKkU2XTBSMiBRVTtdIXc2ZC4hLA==",
        "IFowNjllKCY7KiIuFDspIUEjJ0omJjwoPCdTIB09GT0yVVwnLDQgfSQlMTYqJhkmVSAqOjgmLSpcUC4xVCZnNHomRDRJ"
    ))
    val SNOWTRACE_API_KEY = decoder.decode(listOf(
        "NCdZJTAaRyEzW1oofj4lNTQ1PiNXUCReJkcnQR8zdjAjKQ==",
        "RFQoKzx/MTQkOFgmHDpVIT0kPyg0LyxcKCUwPX8iH141Jg=="
    ))
    val OPTIMISTIC_ETHERSCAN_API_KEY = decoder.decode(listOf(
        "MVckRSxsKEFUKi8lekQiNCknWTspO1hdLDYsR3o5fConMg==",
        "R1dUNj1kREJaXT1SGUcsSiI9PTw3VClaPEcrMmhCfSIrMQ=="
    ))
    val ARBISCAN_API_KEY = decoder.decode(listOf(
        "KldSOSYYREZWND8tbEUlREAiKCIzKihbWzVQMBc9aSwjOw==",
        "RDI2JF0fQjc0XVohZzM7Pis/XjgtI1wsPjhSOXQkfSIqKA=="
    ))
    val GNOSISSCAN_API_KEY = decoder.decode(listOf(
        "JlErSzF4QUA4NFAwHCdSNCciOUAsOSNYXCY1RnolbDokVQ==",
        "OyY5NSl8OzE3KSc5GCVYMCRENTk9KysjVUY/PWY4FzknOA=="
    ))
    val FTMSCAN_API_KEY = decoder.decode(listOf(
        "RVQ4IlpqOScjNic1Gz1UOzo9NisjUjY4KjUrPX4meFA+Jw==",
        "OiI2ITh6QkIpKSQ1dT0rOUoiJCtSUi0/PTpWLBsmFzs+MQ=="
    ))
    val BASESCAN_API_KEY = decoder.decode(listOf(
        "MSgkJDseRUQkIlAzFDVTMDUnOCAzMDguJSswJBknFjsmJw==",
        "ITZVISJ7Oi0zLz5bHEIrQEFEWzczWCs/VUNQIG9GditGVA=="
    ))
    val ERA_ZK_SYNC_API_KEY = decoder.decode(listOf(
        "OCRTRyIZRDQgOS4udD1WI0QkK0Y3LzZYLzQzOh1IfSFHLw=="
    ))
    val WALLET_CONNECT_V2_KEY = decoder.decode(listOf(
        "SAFVFVwcE0NSVFFTTEMEQBITWkUAWVwPCRYHRx5FGFA=",
        "QABUEAkcRUABXg9SG0UARBdHDEpcV1ZfC0BURR0RGVo="
    ))
    val SOLSCAN_API_KEY = decoder.decode(listOf(
        "FRorGwpqExwtBSMqeAooQj0eJgEtDzxcDjEsQmcbXjAlIjpaTxYRZxoWDzoBB2omCiIrJgY9DiRcJxcZVTpEIVclJzBELSUyG2QdIxY1Pg9eOQscGhYnOFICXQUXFiIiWhNULQQsJSEPET9rABchWQMBH0AIPzA9BytXMx4LX0YMO0c6HgpBFRwBCEIATjceCyAqKV0pOSIaOAU3Vi8UAl08DyVXPXo6Sk8yMSxEIhQiFyYcIQRJQg4+MgIcQlQ5CD4iCjE+SSYcPB1TLwAJKzJiFAEJ",
        "FRorGwpqExwtBSMqeAooQj0eJgEtDzxcDjEsQmcbXjAlIjpaTxYRZxoWDzoBB2omCiIrJgY9DiRcJxcZVTpEIVYlJzBALCUmG2QdIxY1Pg9eOQscGhYnPA84OVFZPSETGiFpDAc4Jw8SPwVjBhcxJRoqQDYLFzQbGRANKFggAyATFRwmWyQkJwcCMjobZB0ZCggqKhs9NSpAODsjVi8qLBU8LUQASR8sJgsaCTgGUBsWRDghJCkYExg5Cz4kJjAWMRofGixZWD5JMRI1MzM0Jg=="
    ))
    val TRONGRID_API_KEYS = decoder.decode(listOf(
        "Q1BSRFwZSUFPVFlVHV1VR0QSQkpXV1lEVEIGQUsWSlwWBUlW",
        "SAVUEg0fE01PVFlSH11VQRJPQhBUAg9ECxQGRhlEHw5FAEJaTUZfFREUVFgPTkxHWBVeQwoXXExWXwhLSBEXSBhYFlMUU1RDCQFBEFteD1JLE0xHQhFXX1BRXw9AE1ISGF0bCkRQSQFXFVkfSEU="
    ))
    val UDN_API_KEY = decoder.decode(listOf(
        "AlERGxJKEQEWMxMXFF0JFywAFgQACxwNHgAMGUAISQcYDEcIDwoJSkEYAwATBE4K"
    ))
    val ONE_INCH_API_KEY = decoder.decode(listOf(
        "QyYVBxFuChI1DlskYTYoIRwnJic9LF4kWQcuNXg1TRk="
    ))
    val CHAINALYSIS_API_KEY = decoder.decode(listOf(
        "RFICF1kbSUZQWFoAGkRRFUYWWEdXVlxQCUNVREsSHQlHV0ICV0MKTERNAw5fVRsUBEFGQgwRAlMKDAhEXBUbEg=="
    ))
    val HASH_DIT_API_KEY = decoder.decode(listOf(
        "OxYYCzJLBj86KhsTbBMbBwAfBysVBDkIPwcWDFc3fCw3CTY6ChYhWg==",
        "ESQsGA9iNCwLOS8XeSkTICEUKigXKAg5JRckJ2EcaTErIhwpOz0/aw=="
    ))
}
