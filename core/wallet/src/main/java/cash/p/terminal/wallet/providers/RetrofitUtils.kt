package cash.p.terminal.wallet.providers

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class RetrofitUtils(private val okHttpClient: OkHttpClient) {

    fun build(baseUrl: String, headers: Map<String, String> = emptyMap()): Retrofit {
        val client = if (headers.isEmpty()) {
            okHttpClient
        } else {
            // Create a new client that shares connection pool but adds headers
            okHttpClient.newBuilder()
                .addInterceptor(HeadersInterceptor(headers))
                .build()
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setLenient().create())
            )
            .build()
    }

    private class HeadersInterceptor(private val headers: Map<String, String>) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val requestBuilder = chain.request().newBuilder()
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            return chain.proceed(requestBuilder.build())
        }
    }

}
