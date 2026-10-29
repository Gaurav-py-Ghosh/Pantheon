package dev.pantheon.launcher.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Album(val id: String, val name: String, val count: Int)
data class PhotoRef(val uri: Uri, val name: String)

/** Photo albums (MediaStore), thumbnails, EXIF lines, and images imported for image widgets. */
class MediaRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val imagesDir = File(context.filesDir, "images")
    private val bitmaps = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val exif = LruCache<Uri, String>(200)

    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPhotoAccess() = photoPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun albums(): List<Album> = withContext(Dispatchers.IO) {
        val counts = linkedMapOf<String, Pair<String, Int>>()
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: "unnamed"
                    counts[id] = name to ((counts[id]?.second ?: 0) + 1)
                }
            }
        }
        counts.map { (id, v) -> Album(id, v.first, v.second) }
            .sortedWith(compareByDescending<Album> { it.name.equals("camera", ignoreCase = true) }.thenByDescending { it.count })
    }

    suspend fun photos(bucketId: String?, limit: Int = 40): List<PhotoRef> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            if (bucketId != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Images.Media.BUCKET_ID} = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(bucketId))
            }
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val out = mutableListOf<PhotoRef>()
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                args, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    out += PhotoRef(uri, c.getString(1) ?: "image")
                }
            }
        }
        out
    }

    suspend fun thumbnail(uri: Uri, px: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "t:$uri:$px"
        bitmaps.get(key) ?: runCatching { resolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap() }
            .getOrNull()?.also { bitmaps.put(key, it) }
    }

    /** "1/120s f/1.8", or empty when the photo carries no exposure data. */
    suspend fun exifLine(uri: Uri): String = withContext(Dispatchers.IO) {
        exif.get(uri) ?: runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val e = ExifInterface(stream)
                listOfNotNull(
                    e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { t ->
                        if (t >= 1) "${t.roundToInt()}s" else "1/${(1 / t).roundToInt()}s"
                    },
                    e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "f/" + String.format(Locale.US, "%.1f", it).removeSuffix(".0") },
                    @Suppress("DEPRECATION") e.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { "iso $it" },
                ).joinToString(" ")
            }
        }.getOrNull().orEmpty().also { exif.put(uri, it) }
    }

    /** Copies picked images into app storage, downscaled, so they survive the originals being moved. */
    suspend fun importImages(uris: List<Uri>, maxPx: Int = 1440): List<String> = withContext(Dispatchers.IO) {
        imagesDir.mkdirs()
        uris.mapNotNull { uri ->
            runCatching {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val scale = min(1f, maxPx.toFloat() / max(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val name = "${UUID.randomUUID()}.jpg"
                File(imagesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                name
            }.getOrNull()
        }
    }

    suspend fun imported(name: String, px: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "f:$name:$px"
        bitmaps.get(key) ?: runCatching {
            val file = File(imagesDir, name)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()?.also { bitmaps.put(key, it) }
    }

    fun deleteImported(names: List<String>) {
        names.forEach { File(imagesDir, it).delete() }
    }
}
