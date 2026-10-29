package dev.pantheon.launcher.ui.lock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 8x8 Bayer ordered dithering filter for Michelangelo's Creation of Adam fresco,
 * matching the 1-bit CRT phosphor aesthetic of the portfolio.
 */
object DitherFilter {
    private val BAYER_8 = intArrayOf(
         0, 32,  8, 40,  2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44,  4, 36, 14, 46,  6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
         3, 35, 11, 43,  1, 33,  9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47,  7, 39, 13, 45,  5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )

    private var cachedKey: String? = null
    private var cachedBitmap: ImageBitmap? = null

    /** [art] is `adam` for the bundled Creation of Adam, or an image file name under files/images. */
    suspend fun loadDithered(
        context: Context,
        art: String,
        fgColor: Int,
        targetWidth: Int = 1080,
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        val cacheKey = "$art-$fgColor-$targetWidth"
        cachedBitmap?.takeIf { cachedKey == cacheKey }?.let { return@withContext it }

        val raw = runCatching {
            if (art == "adam") {
                context.assets.open("art/hands-touch.jpg").use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeFile(java.io.File(context.filesDir, "images/$art").path)
            }
        }.getOrNull() ?: return@withContext null

        val scale = targetWidth.toFloat() / raw.width
        val targetHeight = (raw.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
        if (scaled != raw) raw.recycle()

        val dithered = applyBayer8(scaled, pixelSize = 2, contrast = 1.45f, brightness = -0.04f, fgColor = fgColor)
        scaled.recycle()

        val imageBitmap = dithered.asImageBitmap()
        cachedKey = cacheKey
        cachedBitmap = imageBitmap
        imageBitmap
    }

    private fun applyBayer8(
        source: Bitmap,
        pixelSize: Int,
        contrast: Float,
        brightness: Float,
        fgColor: Int,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        val dstPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val fgA = (fgColor ushr 24) and 0xFF
        val fgR = (fgColor shr 16) and 0xFF
        val fgG = (fgColor shr 8) and 0xFF
        val fgB = fgColor and 0xFF

        for (y in 0 until h) {
            val by = (y / pixelSize) % 8
            val row = y * w
            val topBottomFade = minOf(y.toFloat() / (h * 0.18f), (h - 1 - y).toFloat() / (h * 0.18f)).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val bx = (x / pixelSize) % 8
                val threshold = BAYER_8[by * 8 + bx] / 64f

                val c = srcPixels[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                var l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                l = (l - 0.5f) * contrast + 0.5f + brightness

                // Smooth fade on outer edges
                val leftRightFade = minOf(x.toFloat() / (w * 0.15f), (w - 1 - x).toFloat() / (w * 0.15f)).coerceIn(0f, 1f)
                val fade = leftRightFade * topBottomFade
                l *= fade

                if (l > threshold) {
                    val dotAlpha = ((l.coerceIn(0.45f, 1f)) * fgA).toInt().coerceIn(0, 255)
                    dstPixels[row + x] = (dotAlpha shl 24) or (fgR shl 16) or (fgG shl 8) or fgB
                } else {
                    dstPixels[row + x] = 0 // Transparent
                }
            }
        }
        out.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return out
    }
}
