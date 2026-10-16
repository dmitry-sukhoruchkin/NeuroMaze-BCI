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

data class ThetaAnalytics(
    val frequencyHz: Float = 6.0f,
    val periodMs: Float = 166.7f,
    val phaseDeg: Float = 0.0f,
    val currentSlot: Int = 0, // 0..31 Lisman-Jensen slot
    val meanThetaUv: Float = 0.0f,
    val thetaPeakChannel: Int = 1
)

data class CoherencePairItem(
    val pairIndex: Int,
    val channelA: Int,
    val channelB: Int,
    val ciPlv: Float,
    val distanceMm: Float,
    val directionAngleDeg: Float
)

data class WorkingMemoryCoherenceAnalytics(
    val meanCiPlv: Float = 0.0f,
    val maxCiPlv: Float = 0.0f,
    val significantPairsCount: Int = 0,
    val topPairs: List<CoherencePairItem> = emptyList(),
    val spatialDipoleMagUv: Float = 0.0f,
    val spatialDipoleMagMv: Float = 0.0f,
    val spatialDipoleAngleDeg: Float = 0.0f,
    val noiseRmsMv: Float = 0.002f
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
    val thetaAnalytics: ThetaAnalytics = ThetaAnalytics(),
    val coherenceAnalytics: WorkingMemoryCoherenceAnalytics = WorkingMemoryCoherenceAnalytics(),
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
    SCREEN_WORLD,   // 1. Основной: Камера статична на лабиринт, движение от камеры, поворот аватара ни на что не влияет
    EGO_ROTATING,   // 2. Камера вращается с аватаром (360 эго-вид), движение от камеры
    TANK_PRO,       // 3. Мультиплеер/Профи: Лабиринт статичен, движение относительно поворота аватара
    GENSHIN_ORBIT   // 4. Геншин: Сагитта вращает камеру, движение от камеры, аватар поворачивается туда куда идет
}

enum class GameControlMode {
    BCI_DIRECT,     // Driven by real-time EEG ciPLV intent
    BCI_AUTOPILOT,  // Driven by Predictive Monty adaptive model
    MANUAL_TOUCH    // Guided by virtual joystick with EEG telemetry
}
