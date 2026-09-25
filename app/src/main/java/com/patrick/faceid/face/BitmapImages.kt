package com.patrick.faceid.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Edge adapters between Android bitmaps and the platform-neutral [RgbImage] used by the
 * recognition core. Keeping these separate is what lets the core stay free of android.* types.
 */
object BitmapImages {

    fun fromBitmap(bitmap: Bitmap): RgbImage {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return RgbImage(bitmap.width, bitmap.height, argb)
    }

    fun decode(bytes: ByteArray): RgbImage {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalArgumentException("not a decodable image (${bytes.size} bytes)")
        return try {
            fromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun toBitmap(image: RgbImage): Bitmap =
        Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
}
