package cash.p.terminal.network.github.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single entry of the GitHub "contents" API (a file or dir in a repository folder). */
@Serializable
internal data class GithubContentDto(
    @SerialName("name") val name: String,
)
