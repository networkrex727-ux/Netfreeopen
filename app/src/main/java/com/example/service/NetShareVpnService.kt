package com.example.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer

class NetShareVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    companion object {
        const val ACTION_START = "START_VPN"
        const val ACTION_STOP = "STOP_VPN"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("NetShareVpnService", "Received action: $action")
        if (action == ACTION_START) {
            startVpn()
        } else if (action == ACTION_STOP) {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            Log.d("NetShareVpnService", "VPN already running")
            return
        }
        try {
            val builder = Builder()
                .setSession("NetShareProVpn")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
            
            // Forces Chromium, web views, and apps to use our local proxy redirector
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val proxyInfo = android.net.ProxyInfo.buildDirectProxy("127.0.0.1", 8080)
                builder.setHttpProxy(proxyInfo)
            }
            
            vpnInterface = builder.establish()
            Log.d("NetShareVpnService", "VPN established on tun0 successfully!")

            vpnThread = Thread({
                val fileDescriptor = vpnInterface?.fileDescriptor
                if (fileDescriptor != null) {
                    val input = FileInputStream(fileDescriptor)
                    val buffer = ByteBuffer.allocate(32768)
                    try {
                        while (!Thread.interrupted()) {
                            val readBytes = input.read(buffer.array())
                            if (readBytes > 0) {
                                buffer.clear()
                            }
                            Thread.sleep(25)
                        }
                    } catch (e: Exception) {
                        Log.e("NetShareVpnService", "VPN socket reading finished: ${e.localizedMessage}")
                    }
                }
            }, "NetShareVpnThread")
            vpnThread?.start()
            
        } catch (e: Exception) {
            Log.e("NetShareVpnService", "Error establishing VPN: ${e.localizedMessage}")
        }
    }

    private fun stopVpn() {
        vpnThread?.interrupt()
        vpnThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("NetShareVpnService", "Error closing VPN interface: ${e.localizedMessage}")
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
