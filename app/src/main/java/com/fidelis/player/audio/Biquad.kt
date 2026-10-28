package com.fidelis.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One RBJ-cookbook filter, used to read its curve: how many dB it adds at a frequency. */
class Biquad private constructor(private val b: DoubleArray, private val a: DoubleArray) {

    fun dbAt(hz: Float): Float {
        val w = 2 * PI * hz / FS
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b[0] + b[1] * c1 + b[2] * c2; val ni = -(b[1] * s1 + b[2] * s2)
        val dr = a[0] + a[1] * c1 + a[2] * c2; val di = -(a[1] * s1 + a[2] * s2)
        return (10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))).toFloat()
    }

    companion object {
        private const val FS = 48_000.0

        fun peak(f: Float, g: Float, q: Float): Biquad {
            val a = 10.0.pow(g / 40.0); val w = 2 * PI * f / FS; val al = sin(w) / (2 * q); val c = cos(w)
            return Biquad(doubleArrayOf(1 + al * a, -2 * c, 1 - al * a), doubleArrayOf(1 + al / a, -2 * c, 1 - al / a))
        }

        fun lowShelf(f: Float, g: Float, q: Float): Biquad {
            val a = 10.0.pow(g / 40.0); val w = 2 * PI * f / FS; val c = cos(w); val r = 2 * sqrt(a) * sin(w) / (2 * q)
            return Biquad(
                doubleArrayOf(a * ((a + 1) - (a - 1) * c + r), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - r)),
                doubleArrayOf((a + 1) + (a - 1) * c + r, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - r),
            )
        }

        fun highShelf(f: Float, g: Float, q: Float): Biquad {
            val a = 10.0.pow(g / 40.0); val w = 2 * PI * f / FS; val c = cos(w); val r = 2 * sqrt(a) * sin(w) / (2 * q)
            return Biquad(
                doubleArrayOf(a * ((a + 1) + (a - 1) * c + r), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - r)),
                doubleArrayOf((a + 1) - (a - 1) * c + r, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - r),
            )
        }
    }
}
