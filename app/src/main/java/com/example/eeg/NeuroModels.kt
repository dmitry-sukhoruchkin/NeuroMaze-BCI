package com.example.eeg

data class EegPacket(
    val counter: Int,
    val rawValues: IntArray,
    val microvolts: FloatArray,
    val timestampNanos: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EegPacket
        return counter == other.counter && timestampNanos == other.timestampNanos
    }

    override fun hashCode(): Int {
        var result = counter
        result = 31 * result + timestampNanos.hashCode()
        return result
    }
}

data class BandPowers(
    val delta: Float = 0.2f,
    val theta: Float = 0.5f,
    val alpha: Float = 0.4f,
    val beta: Float = 0.3f,
    val gamma: Float = 0.2f
)

data class NeuroIntent(
    val x: Float = 0f,
    val y: Float = 0f,
    val torque: Float = 0f,
    val magnitude: Float = 0f
)

data class MontyPrediction(
    val x: Float = 0f,
    val y: Float = 0f,
    val anomalyLoss: Float = 0f,
    val targetHeadingDeg: Float = 0f,
    val montyHeadingDeg: Float = 0f,
    val angleErrorDeg: Float = 0f
)

data class NeuroTelemetry(
    val isLiveBle: Boolean = false,
    val deviceName: String = "BCI Simulator",
    val sps: Float = 250f,
    val packetsReceived: Long = 0L,
    val packetLossCount: Int = 0,
    val persistence: Float = 0f,
    val bandPowers: BandPowers = BandPowers(),
    val intent: NeuroIntent = NeuroIntent(),
    val monty: MontyPrediction = MontyPrediction(),
    val channelMicrovolts: FloatArray = FloatArray(16),
    val channelSaturation: BooleanArray = BooleanArray(16),
    val directionalMemory: FloatArray = FloatArray(8) // 8 compass directions [N, NE, E, SE, S, SW, W, NW]
)

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SIMULATED
}

enum class CameraViewMode {
    WORLD,        // Fixed top-down view
    EGO_ROTATING  // Rotating view from avatar's perspective
}

enum class GameControlMode {
    BCI_DIRECT,     // Driven by real-time EEG ciPLV intent
    BCI_AUTOPILOT,  // Driven by Predictive Monty adaptive model
    MANUAL_TOUCH    // Guided by virtual joystick with EEG telemetry
}
