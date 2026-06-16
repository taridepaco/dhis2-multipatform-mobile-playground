package org.dhis2.multiplatformmobileplayground.dsl.llm

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Exercises [ModelDownloader] against a real local HTTP server that mimics the failure mode seen in
 * production (the connection dropping mid-transfer on a large file), verifying that the partial
 * `.part` file is resumed via an HTTP Range request rather than restarting from zero.
 */
class ModelDownloaderTest {

    @Test
    fun shouldResumePartialDownloadAfterInterruptedTransfer() = runTest {
        val payload = ByteArray(2_000) { (it % 251).toByte() }
        val handler = FlakyHandler(payload, partialBytes = 800, honorRange = true)
        val server = startServer(handler)
        try {
            val target = newTargetFile()

            ModelDownloader().download(urlFor(server), target, authToken = null) {}

            assertContentEquals(payload, target.readBytes())
            assertTrue(handler.requestCount >= 2, "expected at least one resume request")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun shouldRestartFromScratchWhenServerIgnoresRange() = runTest {
        val payload = ByteArray(1_500) { (it % 251).toByte() }
        val handler = FlakyHandler(payload, partialBytes = 600, honorRange = false)
        val server = startServer(handler)
        try {
            val target = newTargetFile()

            ModelDownloader().download(urlFor(server), target, authToken = null) {}

            assertContentEquals(payload, target.readBytes())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun shouldSkipDownloadWhenTargetAlreadyPresent() = runTest {
        val target = newTargetFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }

        // An unroutable URL: if the existing file weren't honored this would fail, not no-op.
        ModelDownloader().download("http://127.0.0.1:1/model", target, authToken = null) {}

        assertContentEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    private fun newTargetFile(): File =
        File(Files.createTempDirectory("model-downloader").toFile(), "model.bin")

    private fun startServer(handler: HttpHandler): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/model", handler)
            executor = null
            start()
        }

    private fun urlFor(server: HttpServer): String =
        "http://127.0.0.1:${server.address.port}/model"

    /**
     * Drops the first transfer after [partialBytes] (declaring the full length, so the client sees a
     * short read), then serves the rest. With [honorRange] it answers the resume with 206 Partial
     * Content; otherwise it ignores the Range header and returns the full body with 200.
     */
    private class FlakyHandler(
        private val payload: ByteArray,
        private val partialBytes: Int,
        private val honorRange: Boolean
    ) : HttpHandler {
        @Volatile
        var requestCount = 0

        override fun handle(exchange: HttpExchange) {
            requestCount++
            val range = exchange.requestHeaders.getFirst("Range")
            try {
                when {
                    requestCount == 1 -> {
                        // Declare the full size but drop the connection after a partial body.
                        exchange.sendResponseHeaders(200, payload.size.toLong())
                        exchange.responseBody.use { it.write(payload, 0, partialBytes) }
                    }
                    honorRange && range != null -> {
                        val start = RANGE.find(range)!!.groupValues[1].toInt()
                        val remaining = payload.size - start
                        exchange.responseHeaders.add(
                            "Content-Range",
                            "bytes $start-${payload.size - 1}/${payload.size}"
                        )
                        exchange.sendResponseHeaders(206, remaining.toLong())
                        exchange.responseBody.use { it.write(payload, start, remaining) }
                    }
                    else -> {
                        // Ignore the Range header: serve the whole file from the beginning.
                        exchange.sendResponseHeaders(200, payload.size.toLong())
                        exchange.responseBody.use { it.write(payload) }
                    }
                }
            } finally {
                exchange.close()
            }
        }

        private companion object {
            val RANGE = Regex("bytes=(\\d+)-")
        }
    }
}
