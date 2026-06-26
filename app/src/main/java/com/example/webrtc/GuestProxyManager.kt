package com.example.webrtc

import android.util.Base64
import com.example.data.model.ProxyRequest
import com.example.data.model.ProxyResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuestProxyManager(
    private val dataChannel: DataChannel,
    private val bandwidthMeter: BandwidthMeter
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ProxyResponse>>()

    init {
        setupObserver()
    }

    private fun setupObserver() {
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {}

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return

                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val responseStr = String(data, Charsets.UTF_8)

                try {
                    val response = json.decodeFromString<ProxyResponse>(responseStr)
                    val deferred = pendingRequests.remove(response.id)
                    if (deferred != null) {
                        val responseBytes = Base64.decode(response.data, Base64.DEFAULT)
                        bandwidthMeter.addBytes(responseBytes.size.toLong())
                        
                        deferred.complete(response)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    suspend fun performProxyRequest(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        bodyStr: String? = null
    ): ProxyResponse {
        val requestId = UUID.randomUUID().toString()
        val base64Body = bodyStr?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }

        val request = ProxyRequest(
            id = requestId,
            url = url,
            method = method,
            headers = headers,
            body = base64Body
        )

        val deferred = CompletableDeferred<ProxyResponse>()
        pendingRequests[requestId] = deferred

        try {
            val requestStr = json.encodeToString(request)
            val bytes = requestStr.toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            dataChannel.send(buffer)

            return withTimeout(30000) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            return ProxyResponse(
                id = requestId,
                status = 500,
                error = e.message ?: "Request failed or timed out"
            )
        }
    }
}
