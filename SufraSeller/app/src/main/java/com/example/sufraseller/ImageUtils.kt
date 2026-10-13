package com.example.sufraseller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * Prepares a picked photo for upload (Milestone 2).
 *
 * The project rules say every photo must be smaller than 0.5 MB, so instead of trusting the
 * user, the app shrinks and compresses the photo itself until it is under the limit.
 */
object ImageUtils {

    /** 0.5 MB limit from the project description (we stay a bit under it). */
    const val MAX_BYTES = 450 * 1024

    /** Longest side of the uploaded photo, in pixels. Plenty for a profile picture. */
    private const val MAX_SIDE = 1080

    /** Returns JPEG bytes of the photo, always smaller than MAX_BYTES. Run it off the main thread. */
    fun compressForUpload(context: Context, uri: Uri): ByteArray {
        var bitmap = decodeScaled(context, uri)
        var quality = 90
        while (true) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= MAX_BYTES) return bytes
            if (quality > 50) {
                quality -= 10                       // 1) lower the JPEG quality
            } else {
                bitmap = Bitmap.createScaledBitmap(  // 2) then make the picture smaller
                    bitmap,
                    (bitmap.width * 0.8f).toInt().coerceAtLeast(1),
                    (bitmap.height * 0.8f).toInt().coerceAtLeast(1),
                    true
                )
                quality = 80
            }
        }
    }

    /** Reads the photo already scaled down so its longest side is at most MAX_SIDE. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also fixes the rotation of photos taken by the camera
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxOf(w, h).toFloat() / MAX_SIDE
                if (scale > 1f) decoder.setTargetSize((w / scale).toInt(), (h / scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Older phones (Android 7–8)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Could not read this image")
    }
}
