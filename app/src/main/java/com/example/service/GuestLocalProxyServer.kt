package com.example.service

import android.util.Log
import com.example.webrtc.GuestProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class GuestLocalProxyServer(
    private val guestProxyManager: GuestProxyManager,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(8080)
                Log.d("GuestLocalProxyServer", "Local HTTP CONNECT Proxy started on port 8080")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e("GuestLocalProxyServer", "Server socket error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            // Read initial request line
            val buffer = ByteArray(8192)
            val bytesRead = input.read(buffer)
            if (bytesRead <= 0) {
                clientSocket.close()
                return
            }
            
            val requestStr = String(buffer, 0, bytesRead, Charsets.UTF_8)
            val lines = requestStr.split("\r\n")
            if (lines.isEmpty()) {
                clientSocket.close()
                return
            }
            
            val firstLine = lines[0]
            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }
            
            val method = parts[0]
            val url = parts[1]
            
            if (method == "CONNECT") {
                Log.d("GuestLocalProxy", "Intercepted CONNECT request: $url")
                
                // Write successful connect response
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                output.flush()
                
                routeDataTunnel(input, output, url)
            } else {
                Log.d("GuestLocalProxy", "Intercepted HTTP $method request: $url")
                val response = guestProxyManager.performProxyRequest(
                    url = url,
                    method = method,
                    headers = parseHeaders(lines),
                    bodyStr = extractBody(requestStr)
                )
                
                if (response.error.isEmpty()) {
                    val responseBytes = android.util.Base64.decode(response.data, android.util.Base64.DEFAULT)
                    val headersStr = StringBuilder()
                    headersStr.append("HTTP/1.1 ${response.status} OK\r\n")
                    response.headers.forEach { (key, value) ->
                        headersStr.append("$key: $value\r\n")
                    }
                    headersStr.append("\r\n")
                    output.write(headersStr.toString().toByteArray())
                    output.write(responseBytes)
                    output.flush()
                } else {
                    output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                    output.flush()
                }
                clientSocket.close()
            }
        } catch (e: Exception) {
            Log.e("GuestLocalProxyServer", "Error handling client: ${e.localizedMessage}")
            try { clientSocket.close() } catch (ex: Exception) {}
        }
    }

    private fun routeDataTunnel(input: InputStream, output: OutputStream, targetUrl: String) {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16384)
            try {
                while (isRunning) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    
                    val base64Payload = android.util.Base64.encodeToString(buffer, 0, read, android.util.Base64.NO_WRAP)
                    val response = guestProxyManager.performProxyRequest(
                        url = "https://$targetUrl/proxy-payload",
                        method = "POST",
                        headers = mapOf("X-Proxy-Target" to targetUrl),
                        bodyStr = base64Payload
                    )
                    
                    if (response.error.isEmpty() && response.data.isNotEmpty()) {
                        val decodedResponse = android.util.Base64.decode(response.data, android.util.Base64.DEFAULT)
                        output.write(decodedResponse)
                        output.flush()
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("GuestLocalProxyServer", "Tunnel error: ${e.localizedMessage}")
            } finally {
                try { input.close() } catch (e: Exception) {}
                try { output.close() } catch (e: Exception) {}
            }
        }
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) break
            val colonIndex = line.indexOf(":")
            if (colonIndex != -1) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    private fun extractBody(requestStr: String): String? {
        val index = requestStr.indexOf("\r\n\r\n")
        return if (index != -1 && index + 4 < requestStr.length) {
            requestStr.substring(index + 4)
        } else null
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }
}
