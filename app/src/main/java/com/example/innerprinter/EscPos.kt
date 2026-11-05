package com.example.innerprinter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40)
    fun feed(lines: Int) = byteArrayOf(0x1B, 0x64, lines.toByte())

    /** يحوّل الصورة إلى أوامر ESC/POS (GS v 0) على دفعات صغيرة. widthDots: 384 لورق 58mm و 576 لورق 80mm */
    fun rasterChunks(src: Bitmap, widthDots: Int, rowsPerChunk: Int = 64): List<ByteArray> {
        // دمج الصورة على خلفية بيضاء (لدعم PNG الشفاف)
        val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }

        val h = (flat.height * widthDots.toFloat() / flat.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(flat, widthDots, h, true)
        val pixels = IntArray(widthDots * h)
        scaled.getPixels(pixels, 0, widthDots, 0, 0, widthDots, h)

        val bytesPerRow = widthDots / 8
        val out = mutableListOf<ByteArray>()
        var y = 0
        while (y < h) {
            val rows = minOf(rowsPerChunk, h - y)
            val buf = ByteArray(8 + bytesPerRow * rows)
            buf[0] = 0x1D; buf[1] = 0x76; buf[2] = 0x30; buf[3] = 0
            buf[4] = (bytesPerRow and 0xFF).toByte(); buf[5] = (bytesPerRow shr 8).toByte()
            buf[6] = (rows and 0xFF).toByte();        buf[7] = (rows shr 8).toByte()
            for (r in 0 until rows) {
                for (bx in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val p = pixels[(y + r) * widthDots + bx * 8 + bit]
                        val lum = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                        if (lum < 140) b = b or (0x80 shr bit)
                    }
                    buf[8 + r * bytesPerRow + bx] = b.toByte()
                }
            }
            out.add(buf)
            y += rows
        }
        return out
    }
}
