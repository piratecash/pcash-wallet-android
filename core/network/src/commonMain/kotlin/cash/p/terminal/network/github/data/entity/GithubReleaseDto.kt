package cash.p.terminal.network.github.data.entity

import cash.p.terminal.network.data.serializers.ISO8601InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @Serializable(with = ISO8601InstantSerializer::class)
    @SerialName("published_at") val publishedAt: Instant,
    @SerialName("assets") val assets: List<GithubAssetDto> = emptyList(),
)
