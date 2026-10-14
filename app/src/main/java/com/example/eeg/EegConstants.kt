package com.example.eeg

import java.util.UUID
import kotlin.math.sqrt

object EegConstants {
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val DATA_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val CMD_CHAR_UUID: UUID = UUID.fromString("c0de0001-36e1-4688-b7f5-ea07361b26a8")
    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val CHANNELS_COUNT = 16
    const val PACKET_SIZE = 51
    const val PACKET_HEADER: Byte = 0xA0.toByte()
    const val PACKET_FOOTER: Byte = 0xC0.toByte()

    val SUPPORTED_GAINS = intArrayOf(4, 8, 16, 32)
    val GAIN_TO_CODE = mapOf(1 to 0, 2 to 1, 4 to 2, 8 to 3, 16 to 4, 32 to 5, 64 to 6, 128 to 7)

    fun getUvScale(gain: Int): Float {
        val g = gain.coerceAtLeast(1)
        return (1.2f / g.toFloat() / 8388607.0f) * 1e6f
    }

    fun getSatThresholdUv(gain: Int): Float {
        val g = gain.coerceAtLeast(1)
        return (1.2f / g.toFloat()) * 0.95f * 1e6f
    }

    // Default 16x gain values
    const val DEFAULT_GAIN = 16
    val UV_SCALE: Float = getUvScale(DEFAULT_GAIN)
    val SAT_THRESHOLD_UV: Float = getSatThresholdUv(DEFAULT_GAIN)

    // Physical FreeEEG16-Alpha2 (26mm) Coordinates
    val COORDS_X = floatArrayOf(
        10.14f, 7.43f, 2.75f, 2.72f, -2.72f, -2.75f, -7.42f, -10.14f,
        -10.14f, -7.43f, -2.75f, -2.72f, 2.72f, 2.75f, 7.43f, 10.14f
    )

    val COORDS_Y = floatArrayOf(
        -2.72f, -7.43f, -4.77f, -10.15f, -10.14f, -4.77f, -7.42f, -2.73f,
        2.72f, 7.43f, 4.76f, 10.14f, 10.15f, 4.77f, 7.42f, 2.71f
    )

    const val NUM_PAIRS = 120 // 16 * 15 / 2

    val PAIR_I = IntArray(NUM_PAIRS)
    val PAIR_J = IntArray(NUM_PAIRS)
    val DX_120 = FloatArray(NUM_PAIRS)
    val DY_120 = FloatArray(NUM_PAIRS)
    val PROJ_X = FloatArray(NUM_PAIRS)
    val PROJ_Y = FloatArray(NUM_PAIRS)
    val TQ_MULT = FloatArray(NUM_PAIRS)

    init {
        var idx = 0
        for (i in 0 until CHANNELS_COUNT) {
            for (j in (i + 1) until CHANNELS_COUNT) {
                PAIR_I[idx] = i
                PAIR_J[idx] = j
                val dx = COORDS_X[j] - COORDS_X[i]
                val dy = COORDS_Y[j] - COORDS_Y[i]
                DX_120[idx] = dx
                DY_120[idx] = dy
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
                PROJ_X[idx] = dx / len
                PROJ_Y[idx] = dy / len
                TQ_MULT[idx] = (COORDS_X[i] * dy - COORDS_Y[i] * dx) / 100.0f
                idx++
            }
        }
    }
}
