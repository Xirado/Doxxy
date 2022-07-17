package com.freya02.bot.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.outputStream

object HttpUtils {
    @JvmField
    val CLIENT: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(2, TimeUnit.MINUTES) //Jitpack builds have a blocking read
        .build()

    @Synchronized
    fun parseDocument(downloadedBody: String, baseUri: String): Document {
        return Jsoup.parse(downloadedBody, baseUri)
    }

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun getDocument(url: String): Document {
        return parseDocument(downloadBody(url), url)
    }

    fun <R> doRequest(url: String, block: (Response, ResponseBody) -> R): R {
        CLIENT.newCall(
            Request.Builder()
                .url(url)
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Got an unsuccessful response from ${response.request.url}, code: ${response.code}")

            val body: ResponseBody = response.body
                ?: throw IOException("Got no ResponseBody for ${response.request.url}")

            return block(response, body)
        }
    }

    @Throws(IOException::class)
    fun downloadBody(url: String): String = doRequest(url) { _, body ->
        body.string()
    }

    fun downloadAt(url: String, path: Path): Path = doRequest(url) { _, body ->
        path.also { it.outputStream().use { stream -> body.byteStream().transferTo(stream) } }
    }

    fun doesStartByLocalhost(link: String): Boolean {
        return link.startsWith("http://localhost")
    }

    fun removeFragment(url: String): String = url.substringBefore('#')
}