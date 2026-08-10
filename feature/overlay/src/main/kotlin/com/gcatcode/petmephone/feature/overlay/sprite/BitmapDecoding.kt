package com.gcatcode.petmephone.feature.overlay.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject

/**
 * Seam over [BitmapFactory] so [SpriteSheetDecoder] can be tested for one thing raw static mocking
 * cannot prove: that the full-decode call is *never invoked* when the header check rejects a
 * sheet, not merely that the result happens to be `Failed`. Production uses [Default]; tests
 * substitute a call-counting fake.
 */
interface BitmapDecoding {
    fun decodeBounds(bytes: ByteArray): Bounds
    fun decodeFull(bytes: ByteArray): Bitmap?

    data class Bounds(val widthPx: Int, val heightPx: Int)

    class Default @Inject constructor() : BitmapDecoding {
        override fun decodeBounds(bytes: ByteArray): Bounds {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            return Bounds(options.outWidth, options.outHeight)
        }

        override fun decodeFull(bytes: ByteArray): Bitmap? {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
