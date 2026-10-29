package dev.pantheon.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class SysSnapshot(
    val memUsed: Long,
    val memTotal: Long,
    val rxBitsPerSec: Long,
    val txBitsPerSec: Long,
    val battery: Int,
    val charging: Boolean,
    val uptimeMs: Long,
    val storageUsed: Long,
    val storageTotal: Long,
) {
    val netBitsPerSec: Long get() = rxBitsPerSec + txBitsPerSec
    val ok: Boolean get() = battery > 15 && memUsed.toDouble() / memTotal < 0.92
}

/** Real readings only. CPU % is not readable by apps since Android 8, so it is left out. */
class SystemStats(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    val snapshots: Flow<SysSnapshot> = flow {
        val mem = ActivityManager.MemoryInfo()
        var lastRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        var lastTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        var lastTime = SystemClock.elapsedRealtime()
        while (true) {
            activityManager.getMemoryInfo(mem)
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
            val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
            fun rate(bytes: Long, last: Long) = if (now > lastTime) (bytes - last).coerceAtLeast(0) * 8_000 / (now - lastTime) else 0
            val stat = StatFs(Environment.getDataDirectory().path)
            emit(
                SysSnapshot(
                    memUsed = mem.totalMem - mem.availMem,
                    memTotal = mem.totalMem,
                    rxBitsPerSec = rate(rx, lastRx),
                    txBitsPerSec = rate(tx, lastTx),
                    battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                    charging = batteryManager.isCharging,
                    uptimeMs = now,
                    storageUsed = stat.totalBytes - stat.availableBytes,
                    storageTotal = stat.totalBytes,
                ),
            )
            lastRx = rx
            lastTx = tx
            lastTime = now
            delay(1_000)
        }
    }.flowOn(Dispatchers.Default)
}

class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val cameraId = runCatching {
        cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    var on by androidx.compose.runtime.mutableStateOf(false)
        private set

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
