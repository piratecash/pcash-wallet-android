package cash.p.terminal.network.github

import cash.p.terminal.network.github.api.GithubApi
import cash.p.terminal.network.github.data.GithubApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GithubApiTest {

    private val config = GithubApiConfig(
        apiBaseUrl = "https://api.github.com/repos/x/y",
        rawBaseUrl = "https://raw.githubusercontent.com/x/y/master",
        apiProxyBaseUrl = "https://p.cash/api/github",
        rawProxyBaseUrl = "https://p.cash/api/github-raw",
    )

    private val requested = mutableListOf<String>()

    private val latestReleaseJson =
        """{"tag_name":"v0.58.0-fdroid","html_url":"u","published_at":"2020-01-01T00:00:00Z","assets":[]}"""

    @Test
    fun getLatestRelease_primarySuccess_returnsBodyWithoutProxy() = runTest {
        val api = api { url ->
            if (url.startsWith(config.apiBaseUrl)) respondJson(latestReleaseJson)
            else respond("", HttpStatusCode.InternalServerError)
        }

        assertEquals("v0.58.0-fdroid", api.getLatestRelease().tagName)
        assertTrue(requested.none { it.startsWith(config.apiProxyBaseUrl) })
    }

    @Test
    fun getLatestRelease_primary403_fallsBackToProxy() = runTest {
        val api = api { url ->
            if (url.startsWith(config.apiProxyBaseUrl)) respondJson(latestReleaseJson)
            else respond("", HttpStatusCode.Forbidden)
        }

        assertEquals("v0.58.0-fdroid", api.getLatestRelease().tagName)
        assertTrue(requested.any { it.startsWith(config.apiProxyBaseUrl) })
    }

    @Test
    fun getRawFile_404_returnsNullWithoutProxy() = runTest {
        val api = api { respond("", HttpStatusCode.NotFound) }

        assertNull(api.getRawFile("changelog_en.md"))
        assertTrue(requested.none { it.startsWith(config.rawProxyBaseUrl) })
    }

    @Test
    fun getRawFile_primaryNetworkError_fallsBackToProxy() = runTest {
        val api = api { url ->
            if (url.startsWith(config.rawProxyBaseUrl)) respond("changelog body", HttpStatusCode.OK)
            else throw IOException("boom")
        }

        assertEquals("changelog body", api.getRawFile("changelog_en.md"))
        assertTrue(requested.any { it.startsWith(config.rawProxyBaseUrl) })
    }

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun api(handler: MockRequestHandleScope.(String) -> HttpResponseData): GithubApi {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requested += url
            handler(url)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return GithubApi(client, config)
    }
}
