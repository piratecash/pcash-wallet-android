package cash.p.terminal.core.managers

import com.google.gson.*
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Guide
import cash.p.terminal.entities.GuideCategoryMultiLang
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.net.URL

object GuidesManager {

    private val guidesUrl = AppConfigProvider.guidesUrl

    private val gson = GsonBuilder()
            .setDateFormat("yyyy-MM-dd")
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Guide::class.java, GuideDeserializer(guidesUrl))
            .create()

    fun getGuideCategories(): Single<Array<GuideCategoryMultiLang>> {
        return Single.fromCallable {
            val request = Request.Builder()
                    .url(guidesUrl)
                    .build()

            val response = APIClient.okHttpClient.newCall(request).execute()
            val categories = gson.fromJson(response.body?.charStream(), Array<GuideCategoryMultiLang>::class.java)
            response.close()

            categories
        }
    }

    class GuideDeserializer(guidesUrl: String) : JsonDeserializer<Guide> {
        private val guidesUrlObj = URL(guidesUrl)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Guide {
            val jsonObject = json.asJsonObject

            return Guide(
                jsonObject.get("title").asString,
                absolutify(jsonObject.get("markdown").asString)
            )
        }

        private fun absolutify(relativeUrl: String?): String {
            return URL(guidesUrlObj, relativeUrl).toString()
        }
    }
}
