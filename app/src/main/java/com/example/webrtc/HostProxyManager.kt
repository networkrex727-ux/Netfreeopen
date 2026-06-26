package com.example.webrtc

import android.util.Base64
import com.example.data.model.ProxyRequest
import com.example.data.model.ProxyResponse
import com.example.data.repository.ShareTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.DataChannel
import java.nio.ByteBuffer

class HostProxyManager(
    private val dataChannel: DataChannel,
    private val shareTokenRepository: ShareTokenRepository,
    private val token: String,
    private val limitMB: Long,
    private val bandwidthMeter: BandwidthMeter,
    private val scope: CoroutineScope
) {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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
                val requestStr = String(data, Charsets.UTF_8)

                scope.launch(Dispatchers.IO) {
                    try {
                        val proxyRequest = json.decodeFromString<ProxyRequest>(requestStr)
                        handleRequest(proxyRequest)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private suspend fun handleRequest(proxyRequest: ProxyRequest) {
        val usedMB = bandwidthMeter.getUsedMB()
        if (usedMB >= limitMB) {
            sendError(proxyRequest.id, "LIMIT_REACHED")
            return
        }

        try {
            val builder = Request.Builder().url(proxyRequest.url)
            
            proxyRequest.headers.forEach { (key, value) ->
                builder.addHeader(key, value)
            }

            if (proxyRequest.method.equals("POST", ignoreCase = true) || 
                proxyRequest.method.equals("PUT", ignoreCase = true)) {
                val bodyBytes = proxyRequest.body?.let { Base64.decode(it, Base64.DEFAULT) } ?: byteArrayOf()
                val requestBody = bodyBytes.toRequestBody("application/json".toMediaTypeOrNull())
                builder.method(proxyRequest.method.uppercase(), requestBody)
            } else {
                builder.method(proxyRequest.method.uppercase(), null)
            }

            val response = httpClient.newCall(builder.build()).execute()
            val responseBytes = response.body?.bytes() ?: byteArrayOf()

            val totalBytesTransferred = responseBytes.size.toLong()
            bandwidthMeter.addBytes(totalBytesTransferred)
            shareTokenRepository.updateBandwidthUsed(token, totalBytesTransferred)

            val base64Data = Base64.encodeToString(responseBytes, Base64.NO_WRAP)
            val proxyResponse = ProxyResponse(
                id = proxyRequest.id,
                status = response.code,
                headers = response.headers.toMap(),
                data = base64Data,
                error = ""
            )

            sendResponse(proxyResponse)
        } catch (e: Exception) {
            sendError(proxyRequest.id, e.message ?: "Unknown Error")
        }
    }

    private fun sendResponse(response: ProxyResponse) {
        try {
            val responseStr = json.encodeToString(response)
            val bytes = responseStr.toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            dataChannel.send(buffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendError(id: String, errorMessage: String) {
        val response = ProxyResponse(
            id = id,
            status = 500,
            error = errorMessage
        )
        sendResponse(response)
    }
}
