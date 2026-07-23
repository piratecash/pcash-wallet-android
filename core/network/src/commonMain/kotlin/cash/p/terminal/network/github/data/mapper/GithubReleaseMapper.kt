package cash.p.terminal.network.github.data.mapper

import cash.p.terminal.network.github.data.entity.GithubReleaseDto
import cash.p.terminal.network.github.domain.entity.AppRelease

internal class GithubReleaseMapper {

    fun map(dto: GithubReleaseDto): AppRelease {
        val version = parseVersion(dto.tagName)
        val apk = dto.assets.firstOrNull { it.name.endsWith(APK_SUFFIX, ignoreCase = true) }
        return AppRelease(
            version = version,
            minor = toMinor(version),
            tagName = dto.tagName,
            publishedAt = dto.publishedAt,
            htmlUrl = dto.htmlUrl,
            apkSizeBytes = apk?.size,
            apkDownloadUrl = apk?.browserDownloadUrl,
        )
    }

    /** "v0.57.2-fdroid" -> "0.57.2". Falls back to the trimmed tag when no semver is present. */
    private fun parseVersion(tagName: String): String =
        SEMVER.find(tagName)?.value ?: tagName.removePrefix("v").substringBefore('-')

    /** "0.57.2" -> "0.57". */
    private fun toMinor(version: String): String {
        val parts = version.split('.')
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else version
    }

    private companion object {
        val SEMVER = Regex("""\d+\.\d+(\.\d+)?""")
        const val APK_SUFFIX = ".apk"
    }
}
