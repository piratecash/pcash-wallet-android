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
        "KCksOyt4JSIsJiwxFEE5PTY8LEc8LyNRLzU/JG8hHzA7Mg==",
        "MSYkRVx3JCdbWic5fDVUNT1OKigpMDdYNTtdLRdDHD40LFxVIDEteyoxOD0nMno+J0dANjw4XSItMydAUzUfJ34uJFVFTzcxPBRCN1E9M1pmM1ghPS4uMyM4NF0jOz0zbygdXiMkRTtNNF5mSCM4KD46fjopJzAlOiA2KDovN0A/I3hEFi8hJiQ5MF8ueEc2OykxMmglMj4rPSU0XCw0O1swKyZjIBcwJ1kjWiIj"
    ))
    val ETHERSCAN_KEY = decoder.decode(listOf(
        "QSJXIlF1KS0rVSAkeikyQUAkP0RRJjoxPystMh1HGyAqVQ==",
        "O1QmSjh+Qy07KS87eDMmJSJOJSVVJjxaPERWN3lFajhCUFwoNj0haSY0Mj5fKGwiOyNGJzooJzMqPzs7MiF2RmohOzUpTzI6WmU9LVcvIFtvQzQ1RD07KlcrOi88MVBHZCF/JUErOztNOi11JDdbPixUYCU3QSYmVipWUlY7PUNRQmc1ZCpCK0UrMl9aGiNBNF8uOmc3LDAjID4oVjVaOitLVkEbIWw5KjBDJShERHQ7QSkpKFB5MS8+Szw1Ry5XK1s8Qz89Y0Z3LD5ZJCYgMSU="
    ))
    val OTHER_SCAN_KEY = decoder.decode(listOf(
        "RDYzISkZOzE6OT9VYEYsRjU6OD9cNThYNTonR2BHejImJQ==",
        "PTNTKztrOCM4XlgwGUY2Mj41JCcsIiVfN0MkMBgmdiJEKVw7WTRbfSNBJSpeWhlDJTA6Qy5KJjE6IzREUjwYNRcgJDhGTzhLXRgoPTRYMSh4M1g3JyUiQD4wKVE1MzRNGDVkPkFTQTJNR1tpNT8nKSQiHCBZQio2OkdRVC9YOTcmJndFaDg6NjMlKQ=="
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
    val MERKLE_IO_KEY = decoder.decode(listOf(
        "AAg+HgpeL0ZQDwhVGkhVF0FAXBBRAAgLXBYDQx8TGQ5BU0dWUksN"
    ))
    val CHAINALYSIS_API_KEY = decoder.decode(listOf(
        "RFICF1kbSUZQWFoAGkRRFUYWWEdXVlxQCUNVREsSHQlHV0ICV0MKTERNAw5fVRsUBEFGQgwRAlMKDAhEXBUbEg=="
    ))
    val HASH_DIT_API_KEY = decoder.decode(listOf(
        "OxYYCzJLBj86KhsTbBMbBwAfBysVBDkIPwcWDFc3fCw3CTY6ChYhWg==",
        "ESQsGA9iNCwLOS8XeSkTICEUKigXKAg5JRckJ2EcaTErIhwpOz0/aw=="
    ))
}
