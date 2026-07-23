package cash.p.terminal.network.github.domain.entity

import java.time.Instant

data class AppRelease(
    val version: String,
    val minor: String,
    val tagName: String,
    val publishedAt: Instant,
    val htmlUrl: String,
    val apkSizeBytes: Long?,
    val apkDownloadUrl: String?,
)
