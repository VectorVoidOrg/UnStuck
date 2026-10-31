package com.ufo.fraudobserver.engine

import java.nio.charset.StandardCharsets

/**
 * High-performance 32-bit MurmurHash3 implementation in pure Kotlin.
 * Used for zero-latency, sub-millisecond Bloom Filter double-hashing.
 */
object MurmurHash3 {

    private const val C1 = -0x3361d2af
    private const val C2 = 0x1b873593

    fun hash32(data: ByteArray, offset: Int, length: Int, seed: Int): Int {
        var h1 = seed
        val roundedEnd = offset + (length and 0x3.inv())

        var i = offset
        while (i < roundedEnd) {
            val k1 = (data[i].toInt() and 0xff) or
                    ((data[i + 1].toInt() and 0xff) shl 8) or
                    ((data[i + 2].toInt() and 0xff) shl 16) or
                    (data[i + 3].toInt() shl 24)

            h1 = mixKey(h1, k1)
            i += 4
        }

        var k1 = 0
        val tail = length and 0x03
        if (tail == 3) {
            k1 = k1 xor ((data[roundedEnd + 2].toInt() and 0xff) shl 16)
        }
        if (tail >= 2) {
            k1 = k1 xor ((data[roundedEnd + 1].toInt() and 0xff) shl 8)
        }
        if (tail >= 1) {
            k1 = k1 xor (data[roundedEnd].toInt() and 0xff)
            k1 *= C1
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= C2
            h1 = h1 xor k1
        }

        h1 = h1 xor length
        h1 = fmix(h1)
        return h1
    }

    fun hash32(text: String, seed: Int): Int {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return hash32(bytes, 0, bytes.size, seed)
    }

    private fun mixKey(h1: Int, k1In: Int): Int {
        var k1 = k1In
        k1 *= C1
        k1 = Integer.rotateLeft(k1, 15)
        k1 *= C2

        var h = h1 xor k1
        h = Integer.rotateLeft(h, 13)
        return h * 5 + -0x19ab949c
    }

    private fun fmix(h1In: Int): Int {
        var h1 = h1In
        h1 = h1 xor (h1 ushr 16)
        h1 *= -0x7a143595
        h1 = h1 xor (h1 ushr 13)
        h1 *= -0x3d4d51cb
        h1 = h1 xor (h1 ushr 16)
        return h1
    }
}

