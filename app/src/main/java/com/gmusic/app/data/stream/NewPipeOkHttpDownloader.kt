package com.gmusic.app.data.stream

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

@Singleton
class NewPipeOkHttpDownloader @Inject constructor(
    baseClient: OkHttpClient
) : Downloader() {

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(this, Localization("it", "IT"), ContentCountry("IT"))
                    initialized = true
                }
            }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = if (dataToSend != null) {
            dataToSend.toRequestBody(null)
        } else if (method.equals("POST", ignoreCase = true)) {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }

        val okBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
            )

        for ((headerName, headerValueList) in headers) {
            okBuilder.removeHeader(headerName)
            for (headerValue in headerValueList) {
                okBuilder.addHeader(headerName, headerValue)
            }
        }

        client.newCall(okBuilder.build()).execute().use { okResponse ->
            if (okResponse.code == 429) {
                throw ReCaptchaException("reCAPTCHA Challenge requested", url)
            }
            val responseBodyString = okResponse.body?.string() ?: ""
            val latestUrl = okResponse.request.url.toString()
            return Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                responseBodyString,
                latestUrl
            )
        }
    }
}
