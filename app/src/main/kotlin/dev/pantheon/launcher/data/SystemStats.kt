package dev.pantheon.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class SysSnapshot(
    val memUsed: Long,
    val memTotal: Long,
    val netBitsPerSec: Long,
    val battery: Int,
    val charging: Boolean,
    val uptimeMs: Long,
) {
    val ok: Boolean get() = battery > 15 && memUsed.toDouble() / memTotal < 0.92
}

/** Real readings only. CPU % is not readable by apps since Android 8, so it is left out. */
class SystemStats(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    val snapshots: Flow<SysSnapshot> = flow {
        val mem = ActivityManager.MemoryInfo()
        var lastBytes = totalBytes()
        var lastTime = SystemClock.elapsedRealtime()
        while (true) {
            activityManager.getMemoryInfo(mem)
            val now = SystemClock.elapsedRealtime()
            val bytes = totalBytes()
            val bps = if (now > lastTime) (bytes - lastBytes).coerceAtLeast(0) * 8_000 / (now - lastTime) else 0
            emit(
                SysSnapshot(
                    memUsed = mem.totalMem - mem.availMem,
                    memTotal = mem.totalMem,
                    netBitsPerSec = bps,
                    battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                    charging = batteryManager.isCharging,
                    uptimeMs = now,
                ),
            )
            lastBytes = bytes
            lastTime = now
            delay(1_000)
        }
    }.flowOn(Dispatchers.Default)

    private fun totalBytes() = (TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()).coerceAtLeast(0)
}

class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val cameraId = runCatching {
        cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    private var on = false

    init {
        cameras.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) on = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun toggle() {
        val id = cameraId ?: return
        runCatching { cameras.setTorchMode(id, !on) }
    }
}
