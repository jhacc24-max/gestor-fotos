package com.example.gestorfotos.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sqrt

object PerceptualHash {

    private const val SIZE = 32
    private const val SMALL_SIZE = 8

    /**
     * Genera un pHash de 64 bits representado como número Long o String hexadecimal.
     */
    fun calculatePHash(bitmap: Bitmap): Long {
        // 1. Escalar la imagen a 32x32 en escala de grises
        val resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val vals = Array(SIZE) { DoubleArray(SIZE) }

        for (x in 0 until SIZE) {
            for (y in 0 until SIZE) {
                val pixel = resized.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                // Luminancia según la fórmula NTSC
                vals[x][y] = 0.299 * red + 0.587 * green + 0.114 * blue
            }
        }

        // 2. Aplicar DCT 2D
        val dctVals = applyDCT(vals)

        // 3. Tomar el bloque superior izquierdo 8x8 (frecuencias bajas), omitiendo el componente DC (0,0)
        var total = 0.0
        for (x in 0 until SMALL_SIZE) {
            for (y in 0 until SMALL_SIZE) {
                if (x == 0 && y == 0) continue // Excluir componente DC
                total += dctVals[x][y]
            }
        }

        val avg = total / (SMALL_SIZE * SMALL_SIZE - 1)

        // 4. Construir el hash de 64 bits comparando contra el promedio
        var hash = 0L
        var bitIndex = 0
        for (x in 0 until SMALL_SIZE) {
            for (y in 0 until SMALL_SIZE) {
                if (x == 0 && y == 0) continue
                if (dctVals[x][y] > avg) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }

        return hash
    }

    /**
     * Calcula la distancia de Hamming entre dos pHashes.
     * Retorna cuántos bits difieren (0 = idénticas, <= 10 = muy parecidas).
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    private fun applyDCT(f: Array<DoubleArray>): Array<DoubleArray> {
        val F = Array(SIZE) { DoubleArray(SIZE) }
        val c = DoubleArray(SIZE)
        c[0] = 1.0 / sqrt(2.0)
        for (i in 1 until SIZE) c[i] = 1.0

        for (u in 0 until SIZE) {
            for (v in 0 until SIZE) {
                var sum = 0.0
                for (i in 0 until SIZE) {
                    for (j in 0 until SIZE) {
                        sum += f[i][j] * 
                            cos((2 * i + 1) * u * Math.PI / (2.0 * SIZE)) * 
                            cos((2 * j + 1) * v * Math.PI / (2.0 * SIZE))
                    }
                }
                F[u][v] = 0.25 * c[u] * c[v] * sum
            }
        }
        return F
    }
}