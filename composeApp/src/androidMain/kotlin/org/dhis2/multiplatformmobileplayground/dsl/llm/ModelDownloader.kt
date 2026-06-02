package org.dhis2.multiplatformmobileplayground.dsl.llm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the on-device model file over HTTP into app-private storage, streaming to a temporary
 * file and reporting progress. Handles redirects (e.g. Hugging Face -> CDN), checks free storage
 * before writing, and is cancellation-aware.
 */
internal class ModelDownloader {

    /**
     * Downloads [url] into [target]. No-op if [target] already exists and is non-empty.
     * [onProgress] reports a 0f..1f fraction, or null when the total size is unknown.
     *
     * @throws IOException on network failures, non-success HTTP codes, or insufficient storage.
     */
    suspend fun download(
        url: String,
        target: File,
        authToken: String?,
        onProgress: (Float?) -> Unit
    ) {
        if (target.exists() && target.length() > 0L) return

        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, target.name + ".part")
        if (tempFile.exists()) tempFile.delete()

        val connection = openConnectionFollowingRedirects(URL(url), authToken)
        try {
            val contentLength = connection.contentLengthLong
            ensureEnoughStorage(target, contentLength)
            onProgress(if (contentLength > 0L) 0f else null)

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0L) {
                            val percent = (downloaded * 100 / contentLength).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(downloaded.toFloat() / contentLength)
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }

        if (!tempFile.renameTo(target)) {
            // rename can fail across mount points; fall back to copy.
            try {
                tempFile.copyTo(target, overwrite = true)
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun openConnectionFollowingRedirects(initialUrl: URL, authToken: String?): HttpURLConnection {
        var url = initialUrl
        var redirects = 0
        while (true) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // Only attach the token to the gated host — never forward it to redirected CDN URLs.
                if (!authToken.isNullOrBlank() && url.host.endsWith("huggingface.co")) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
            }
            when (val code = connection.responseCode) {
                in 200..299 -> return connection
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location == null || ++redirects > MAX_REDIRECTS) {
                        throw IOException("Too many redirects while downloading the model")
                    }
                    url = URL(url, location)
                }
                else -> {
                    connection.disconnect()
                    throw IOException("Model download failed with HTTP $code")
                }
            }
        }
    }

    private fun ensureEnoughStorage(target: File, contentLength: Long) {
        if (contentLength <= 0L) return
        val usable = (target.parentFile ?: target).usableSpace
        if (usable < contentLength + STORAGE_HEADROOM_BYTES) {
            throw IOException(
                "Not enough storage for the model: ${contentLength / MB} MB needed, " +
                    "${usable / MB} MB free"
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_REDIRECTS = 5
        const val MB = 1024L * 1024L
        const val STORAGE_HEADROOM_BYTES = 256L * MB
    }
}
