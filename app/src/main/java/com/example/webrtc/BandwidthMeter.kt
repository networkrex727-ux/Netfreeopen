package com.example.webrtc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BandwidthMeter {
    private var startTime = System.currentTimeMillis()
    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private var lastBytesCount = 0L
    private var lastSpeedCalcTime = System.currentTimeMillis()
    private val _speedMbps = MutableStateFlow(0.0)
    val speedMbps: StateFlow<Double> = _speedMbps.asStateFlow()

    fun reset() {
        startTime = System.currentTimeMillis()
        _totalBytes.value = 0L
        lastBytesCount = 0L
        lastSpeedCalcTime = System.currentTimeMillis()
        _speedMbps.value = 0.0
    }

    fun addBytes(bytes: Long) {
        _totalBytes.value += bytes
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val elapsedSec = (now - lastSpeedCalcTime) / 1000.0
        if (elapsedSec >= 0.5) {
            val currentBytes = _totalBytes.value
            val diffBytes = currentBytes - lastBytesCount
            val diffBits = diffBytes * 8.0
            val mbps = (diffBits / (elapsedSec * 1_000_000.0))
            _speedMbps.value = if (mbps > 0.0) mbps else 0.0
            
            lastBytesCount = currentBytes
            lastSpeedCalcTime = now
        }
    }

    fun getUsedMB(): Long = _totalBytes.value / (1024 * 1024)

    fun getRemainingMB(limitMB: Long): Long {
        val remaining = limitMB - getUsedMB()
        return if (remaining > 0L) remaining else 0L
    }

    fun getProgressPercent(limitMB: Long): Int {
        if (limitMB <= 0) return 0
        val percent = ((getUsedMB().toDouble() / limitMB) * 100).toInt()
        return percent.coerceIn(0, 100)
    }
}
