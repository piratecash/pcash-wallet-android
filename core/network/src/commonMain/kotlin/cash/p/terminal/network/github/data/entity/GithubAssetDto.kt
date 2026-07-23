package cash.p.terminal.network.github.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GithubAssetDto(
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)
