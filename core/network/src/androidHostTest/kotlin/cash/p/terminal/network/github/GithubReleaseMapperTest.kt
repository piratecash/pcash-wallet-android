package cash.p.terminal.network.github

import cash.p.terminal.network.github.data.entity.GithubAssetDto
import cash.p.terminal.network.github.data.entity.GithubReleaseDto
import cash.p.terminal.network.github.data.mapper.GithubReleaseMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GithubReleaseMapperTest {

    private val mapper = GithubReleaseMapper()

    @Test
    fun map_parsesVersionAndMinorFromTagAndPicksApk() {
        val dto = release(
            tagName = "v0.57.2-fdroid",
            assets = listOf(
                asset("p.cash.apk.sha256", 84),
                asset("p.cash.apk", 172_337_195),
            ),
        )

        val result = mapper.map(dto)

        assertEquals("0.57.2", result.version)
        assertEquals("0.57", result.minor)
        assertEquals(172_337_195L, result.apkSizeBytes)
        assertEquals("https://p.cash.apk", result.apkDownloadUrl)
    }

    @Test
    fun map_noApkAsset_leavesApkFieldsNull() {
        val result = mapper.map(release(tagName = "v0.55.0-fdroid", assets = listOf(asset("notes.txt", 10))))

        assertNull(result.apkSizeBytes)
        assertNull(result.apkDownloadUrl)
    }

    @Test
    fun map_tagWithoutSemver_fallsBackToTrimmedTag() {
        val result = mapper.map(release(tagName = "nightly-fdroid", assets = emptyList()))

        assertEquals("nightly", result.version)
    }

    private fun release(tagName: String, assets: List<GithubAssetDto>) = GithubReleaseDto(
        tagName = tagName,
        name = null,
        body = null,
        htmlUrl = "https://example",
        publishedAt = Instant.EPOCH,
        assets = assets,
    )

    private fun asset(name: String, size: Long) = GithubAssetDto(
        name = name,
        size = size,
        browserDownloadUrl = "https://$name",
        contentType = null,
    )
}
