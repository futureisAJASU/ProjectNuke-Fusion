package com.projectnuke.fusion.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

object FusionThumbnailCache {
    private const val DefaultTargetSize = 160
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String, targetSize: Int = DefaultTargetSize): Bitmap? {
        val key = "$path:$targetSize"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return it }
        val bitmap = decodeThumbnail(path, targetSize) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }

    private fun decodeThumbnail(path: String, targetSize: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(path, boundsOptions)
            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            var sampleSize = 1
            while (
                originalWidth / sampleSize > targetSize * 2 ||
                originalHeight / sampleSize > targetSize * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decoded = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
            if (decoded.width <= targetSize && decoded.height <= targetSize) {
                decoded
            } else {
                val scale = minOf(
                    targetSize.toFloat() / decoded.width.toFloat(),
                    targetSize.toFloat() / decoded.height.toFloat()
                )
                val scaledWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
