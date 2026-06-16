package org.dhis2.multiplatformmobileplayground.dsl.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the on-device model file over HTTP into app-private storage, streaming to a temporary
 * file and reporting progress. Handles redirects (e.g. Hugging Face -> CDN), checks free storage
 * before writing, and is cancellation-aware.
 *
 * The model is large (~2.5 GB), so a single transient network stall must not throw away the whole
 * download. The temporary `.part` file is kept across failures and the transfer is **resumed** with
 * an HTTP Range request, retrying a bounded number of times with backoff before giving up. Because
 * the partial file survives, even a later [download] call (e.g. the next warm-up) continues where
 * the previous one stopped instead of restarting from zero.
 */
internal class ModelDownloader {

    /**
     * Downloads [url] into [target]. No-op if [target] already exists and is non-empty.
     * [onProgress] reports a 0f..1f fraction, or null when the total size is unknown.
     *
     * @throws IOException if every attempt fails (network errors, non-success HTTP codes, or
     *   insufficient storage). The `.part` file is preserved so a later call can resume it.
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

        var attempt = 0
        while (true) {
            try {
                streamToTemp(url, tempFile, authToken, onProgress)
                break
            } catch (e: CancellationException) {
                // Cooperative cancellation: keep the partial file so a later warm-up can resume it.
                throw e
            } catch (e: IOException) {
                // Don't retry if the failure is actually a cancellation surfacing as I/O.
                currentCoroutineContext().ensureActive()
                if (++attempt > MAX_RETRIES) throw e
                // Keep tempFile on disk; the next attempt resumes from its current length.
                delay(RETRY_BASE_DELAY_MS * attempt)
            }
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

    /**
     * Performs one download attempt, appending to [tempFile] when the server honors a resume.
     * Throws [IOException] on any network/HTTP/short-read failure, leaving [tempFile] intact so the
     * retry loop can resume from its current length.
     */
    private suspend fun streamToTemp(
        url: String,
        tempFile: File,
        authToken: String?,
        onProgress: (Float?) -> Unit
    ) {
        val alreadyDownloaded = if (tempFile.exists()) tempFile.length() else 0L
        val connection = openConnectionFollowingRedirects(URL(url), authToken, alreadyDownloaded)
        try {
            // A resume is honored only with 206 Partial Content; a plain 200 means the server
            // ignored our Range header, so we must overwrite and start from the beginning.
            val resuming =
                alreadyDownloaded > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            val startOffset = if (resuming) alreadyDownloaded else 0L

            // Content-Length of this response is the number of bytes still to transfer this attempt.
            val responseLength = connection.contentLengthLong
            val total = if (responseLength > 0L) startOffset + responseLength else -1L
            ensureEnoughStorage(tempFile, responseLength)
            onProgress(if (total > 0L) startOffset.toFloat() / total else null)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, resuming).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = startOffset
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(downloaded.toFloat() / total)
                            }
                        }
                    }
                }
            }

            // A short read (server closed the connection early) leaves a truncated file; treat it
            // as a failure so the retry loop resumes the remaining bytes.
            if (total > 0L && tempFile.length() != total) {
                throw IOException("Incomplete download: ${tempFile.length()} of $total bytes")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnectionFollowingRedirects(
        initialUrl: URL,
        authToken: String?,
        rangeStart: Long
    ): HttpURLConnection {
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
                // Resume a partial download by requesting only the remaining bytes.
                if (rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
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

        // A large download will occasionally hit a transient stall; resume and retry a few times
        // with linear backoff before degrading to the DSL fallback.
        const val MAX_RETRIES = 4
        const val RETRY_BASE_DELAY_MS = 2_000L

        const val MB = 1024L * 1024L
        const val STORAGE_HEADROOM_BYTES = 256L * MB
    }
}
