package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BandwidthSharingService : Service() {

    companion object {
        private const val CHANNEL_ID = "bandwidth_sharing_channel"
        private const val NOTIFICATION_ID = 101
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        
        const val EXTRA_IS_HOST = "EXTRA_IS_HOST"
        const val EXTRA_USED_MB = "EXTRA_USED_MB"
        const val EXTRA_LIMIT_MB = "EXTRA_LIMIT_MB"
        const val EXTRA_SPEED_MBPS = "EXTRA_SPEED_MBPS"

        private val _stopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopEvent = _stopEvent.asSharedFlow()
        
        fun startService(context: Context, isHost: Boolean, usedMB: Long, limitMB: Long, speedMbps: Double) {
            val intent = Intent(context, BandwidthSharingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IS_HOST, isHost)
                putExtra(EXTRA_USED_MB, usedMB)
                putExtra(EXTRA_LIMIT_MB, limitMB)
                putExtra(EXTRA_SPEED_MBPS, speedMbps)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun updateService(context: Context, isHost: Boolean, usedMB: Long, limitMB: Long, speedMbps: Double) {
            val intent = Intent(context, BandwidthSharingService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_IS_HOST, isHost)
                putExtra(EXTRA_USED_MB, usedMB)
                putExtra(EXTRA_LIMIT_MB, limitMB)
                putExtra(EXTRA_SPEED_MBPS, speedMbps)
            }
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BandwidthSharingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)
                val usedMB = intent.getLongExtra(EXTRA_USED_MB, 0)
                val limitMB = intent.getLongExtra(EXTRA_LIMIT_MB, 0)
                val speedMbps = intent.getDoubleExtra(EXTRA_SPEED_MBPS, 0.0)
                val notification = buildNotification(isHost, usedMB, limitMB, speedMbps)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)
                val usedMB = intent.getLongExtra(EXTRA_USED_MB, 0)
                val limitMB = intent.getLongExtra(EXTRA_LIMIT_MB, 0)
                val speedMbps = intent.getDoubleExtra(EXTRA_SPEED_MBPS, 0.0)
                val notification = buildNotification(isHost, usedMB, limitMB, speedMbps)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                _stopEvent.tryEmit(Unit)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(isHost: Boolean, usedMB: Long, limitMB: Long, speedMbps: Double): Notification {
        val roleStr = if (isHost) "Sharing" else "Using"
        val speedArrow = if (isHost) "↑" else "↓"
        val speedText = String.format("%.2f Mbps", speedMbps)
        val percent = if (limitMB > 0) (usedMB * 100 / limitMB) else 0
        val text = "$roleStr: $speedText $speedArrow | $usedMB MB of $limitMB MB ($percent%)"
        
        val icon = android.R.drawable.stat_sys_download_done

        val stopIntent = Intent(this, BandwidthSharingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
        } else null

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetShare P2P Tunnel Active")
            .setContentText(text)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                if (isHost) "Stop Sharing" else "Stop Session",
                stopPendingIntent
            )

        if (launchPendingIntent != null) {
            builder.setContentIntent(launchPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bandwidth Sharing Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
