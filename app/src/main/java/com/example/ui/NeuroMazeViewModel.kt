package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.eeg.BleConnectionState
import com.example.eeg.BleDeviceItem
import com.example.eeg.CameraViewMode
import com.example.eeg.EegConstants
import com.example.eeg.FreeEegBleManager
import com.example.eeg.GameControlMode
import com.example.eeg.MontyPrediction
import com.example.eeg.NeuroIntent
import com.example.eeg.NeuroTelemetry
import com.example.eeg.WorkingMemoryEngine
import com.example.eeg.WorkingMemoryResult
import com.example.maze.AvatarState
import com.example.maze.PathPoint
import com.example.maze.TopoMaze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

data class GameUiState(
    val mazeDim: Int = 13,
    val mazeGrid: Array<IntArray> = emptyArray(),
    val exitPos: Pair<Int, Int> = Pair(1, 1),
    val optimalDistance: Int = 0,
    val avatarX: Float = 1.5f,
    val avatarY: Float = 1.5f,
    val avatarAngle: Float = 0f,
    val cameraYaw: Float = 0f,
    val avatarTrail: List<PathPoint> = emptyList(),
    val idealVector: Pair<Float, Float> = Pair(1f, 0f),
    val lookaheadPath: List<PathPoint> = emptyList(),
    val controlMode: GameControlMode = GameControlMode.BCI_DIRECT,
    val cameraMode: CameraViewMode = CameraViewMode.SCREEN_WORLD,
    val showIdealPath: Boolean = true,
    val showTrail: Boolean = true,
    val showMontyVector: Boolean = true,
    val isHudCollapsed: Boolean = false,
    val mazesSolved: Int = 0,
    val currentLevelTimeSec: Float = 0f,
    val lastWinStats: LevelWinStats? = null,
    val showWinDialog: Boolean = false,
    val showBleDialog: Boolean = false,
    val showTelemetrySheet: Boolean = false,
    val telemetry: NeuroTelemetry = NeuroTelemetry(),
    val connectionState: BleConnectionState = BleConnectionState.SIMULATED,
    val connectedDeviceName: String = "BCI Neuro-Simulator",
    val discoveredDevices: List<BleDeviceItem> = emptyList(),
    val sensitivity: Float = 0.12f,
    val intentMagnitude: Float = 18.0f,
    val hardwareGain: Int = 16,
    val saturatedChannels: List<Int> = emptyList(),
    val isGameRunning: Boolean = true
)

data class LevelWinStats(
    val levelNumber: Int,
    val timeSec: Float,
    val avgPersistence: Float,
    val optimalSteps: Int
)

class NeuroMazeViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = FreeEegBleManager(application.applicationContext)
    private val wmEngine = WorkingMemoryEngine()
    private var maze = TopoMaze(dim = 13)
    private val avatar = AvatarState(
        sensitivity = 0.12f,
        maxSpeed = 0.25f,
        forwardSpeedScale = 0.35f,
        strafeSpeedScale = 0.35f,
        turnSpeedScale = 0.65f,
        intentGain = 2.5f
    )

    private val _uiState = MutableStateFlow(
        GameUiState(
            mazeDim = maze.dim,
            mazeGrid = maze.grid,
            exitPos = maze.exitPos,
            optimalDistance = maze.optimalDistance,
            sensitivity = avatar.sensitivity,
            intentMagnitude = 18.0f,
            hardwareGain = bleManager.currentHardwareGain
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var virtualJoyX = 0f
    private var virtualJoyY = 0f

    private var levelStartTime = System.currentTimeMillis()
    private var persistenceAccum = 0f
    private var persistenceSamples = 0
    private var currentPersistence = 0f
    private var lastIx = 0f
    private var lastIy = 0f

    init {
        // Collect BLE state changes
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                val devName = when (state) {
                    BleConnectionState.CONNECTED -> bleManager.connectedDeviceName.value ?: "FreeEEG16 [LIVE]"
                    BleConnectionState.CONNECTING -> "Connecting FreeEEG..."
                    BleConnectionState.SCANNING -> "Scanning for FreeEEG..."
                    BleConnectionState.SIMULATED -> "BCI Neuro-Simulator"
                    BleConnectionState.DISCONNECTED -> "Disconnected"
                }
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    connectedDeviceName = devName
                )
            }
        }

        viewModelScope.launch {
            bleManager.discoveredDevices.collect { list ->
                _uiState.value = _uiState.value.copy(discoveredDevices = list)
            }
        }

        viewModelScope.launch {
            bleManager.saturatedChannels.collect { list ->
                _uiState.value = _uiState.value.copy(saturatedChannels = list)
            }
        }

        // Default to Simulator mode so app is playable out of the box
        bleManager.setSimulatedMode()

        // 60 FPS Engine loop
        viewModelScope.launch(Dispatchers.Default) {
            runGameLoop()
        }
    }

    private suspend fun runGameLoop() {
        var lastTick = System.nanoTime()

        while (viewModelScope.isActive) {
            val now = System.nanoTime()
            val dt = ((now - lastTick) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
            lastTick = now

            val isLiveBle = bleManager.connectionState.value == BleConnectionState.CONNECTED
            val (idealX, idealY, path) = maze.getContinuous360Gradient(avatar.x, avatar.y, lookaheadDist = 2.2f)

            val sps = if (isLiveBle) bleManager.sps.value else 250f
            val satThresh = bleManager.currentSatThresholdUv

            // 1. INGEST EEG: Either from real FreeEEG16 BLE or Synthetic Agent
            if (isLiveBle) {
                val packets = bleManager.pollPackets()
                if (packets.isNotEmpty()) {
                    for (pkt in packets) {
                        wmEngine.ingestSample(pkt.microvolts)
                    }
                } else {
                    val latest = bleManager.latestPacket.get()
                    if (latest != null) {
                        wmEngine.ingestSample(latest.microvolts)
                    }
                }
            } else {
                // Synthetic BCI Agent (100% parity with synthetic_bci_agent.py)
                val targetDirX = if (virtualJoyX != 0f || virtualJoyY != 0f) virtualJoyX else idealX
                val targetDirY = if (virtualJoyX != 0f || virtualJoyY != 0f) virtualJoyY else idealY
                val chunk = wmEngine.generateSyntheticChunk(targetDirX, targetDirY, _uiState.value.hardwareGain)
                for (sample in chunk) {
                    wmEngine.ingestSample(sample)
                }
            }

            // 2. WORKING MEMORY PROCESSING: 32-slot Theta-Gamma PAC, Downbeat Slot 0, HTM & Monty
            val wmResult: WorkingMemoryResult = wmEngine.processWorkingMemory(idealX, idealY, sps)

            // 3. PERSISTENCE COMPUTATION (BleService.ts)
            val intentX = wmResult.realX
            val intentY = wmResult.realY
            val mag = hypot(intentX.toDouble(), intentY.toDouble()).toFloat()
            val dot = intentX * lastIx + intentY * lastIy
            val lastMag = hypot(lastIx.toDouble(), lastIy.toDouble()).toFloat()
            val cosTh = if (mag * lastMag > 1e-6f) dot / (mag * lastMag) else 0f

            if (mag > 0.05f && cosTh > 0.8f) {
                currentPersistence = (currentPersistence + 0.05f).coerceAtMost(1.0f)
            } else {
                currentPersistence *= 0.95f
            }
            lastIx = intentX
            lastIy = intentY

            persistenceAccum += currentPersistence
            persistenceSamples++

            // 4. KINEMATICS MOTION SOURCING (neurocanvas_monty_bci.py Section 4)
            val intentMag = _uiState.value.intentMagnitude
            val isAutopilot = _uiState.value.controlMode == GameControlMode.BCI_AUTOPILOT

            val driveX: Float
            val driveY: Float
            val driveTorque: Float

            when (_uiState.value.controlMode) {
                GameControlMode.BCI_AUTOPILOT -> {
                    if (_uiState.value.cameraMode == CameraViewMode.SCREEN_WORLD) {
                        // Режим 1: движение от камеры по экрану
                        driveX = wmResult.montyX * intentMag + virtualJoyX * 6.0f
                        driveY = wmResult.montyY * intentMag + virtualJoyY * 6.0f
                        driveTorque = 0f
                    } else {
                        // Режимы 2, 3, 4: движение вперед и подруливание
                        driveX = 0f + virtualJoyX * 6.0f
                        driveY = -intentMag + virtualJoyY * 6.0f
                        val targetAngle = atan2(wmResult.montyY.toDouble(), wmResult.montyX.toDouble()).toFloat() + (PI.toFloat() / 2f)
                        val activeAngle = if (_uiState.value.cameraMode == CameraViewMode.GENSHIN_ORBIT) avatar.cameraYaw else avatar.angle
                        var aDiff = targetAngle - activeAngle
                        while (aDiff > PI.toFloat()) aDiff -= (2f * PI.toFloat())
                        while (aDiff < -PI.toFloat()) aDiff += (2f * PI.toFloat())
                        driveTorque = (aDiff * 0.8f).coerceIn(-1.5f, 1.5f)
                    }
                }
                GameControlMode.BCI_DIRECT -> {
                    if (_uiState.value.cameraMode == CameraViewMode.SCREEN_WORLD) {
                        // Режим 1 (Основной): Камера статична, движение строго от камеры/экрана,
                        // поворот аватара просто вращается и ни на что не влияет
                        driveX = wmResult.realX * intentMag + virtualJoyX * 10.0f
                        driveY = wmResult.realY * intentMag + virtualJoyY * 10.0f
                        driveTorque = wmResult.realTq * 1.8f + (virtualJoyX * 0.8f)
                    } else {
                        // Режимы 2 (EGO), 3 (TANK PRO), 4 (GENSHIN ORBIT):
                        // Намерение вперед толкает по оси камеры/аватара, поворот от сагитты
                        val forwardDrive = if (mag > 0.05f) -mag * intentMag else wmResult.realY * intentMag
                        driveX = wmResult.realX * (intentMag * 0.25f) + virtualJoyX * 10.0f
                        driveY = forwardDrive + virtualJoyY * 10.0f
                        driveTorque = wmResult.realTq * 1.8f + (virtualJoyX * 0.8f)
                    }
                }
                GameControlMode.MANUAL_TOUCH -> {
                    val boost = 1f + currentPersistence * 3.0f
                    driveX = virtualJoyX * intentMag * boost
                    driveY = virtualJoyY * intentMag * boost
                    driveTorque = virtualJoyX * 1.5f
                }
            }

            // 5. AVATAR DISPLACEMENT UPDATE
            avatar.updateMotion(
                intentX = driveX,
                intentY = driveY,
                intentTorque = driveTorque,
                persistence = currentPersistence,
                maze = maze,
                cameraMode = _uiState.value.cameraMode
            )

            // 6. CHECK EXIT
            val curCellX = floor(avatar.x).toInt()
            val curCellY = floor(avatar.y).toInt()
            if (curCellX == maze.exitPos.first && curCellY == maze.exitPos.second) {
                handleMazeWon()
            }

            // 7. ASSEMBLE TELEMETRY
            val compassBars = FloatArray(8)
            val eegHeadingDeg = (Math.toDegrees(atan2(-wmResult.realY.toDouble(), wmResult.realX.toDouble())).toFloat() + 360f) % 360f
            for (c in 0 until 8) {
                val cDeg = WorkingMemoryEngine.COMPASS_ANGLES_DEG[c]
                val diff = abs((eegHeadingDeg - cDeg + 180f) % 360f - 180f)
                compassBars[c] = (1.0f - diff / 60.0f).coerceIn(0f, 1f) * mag.coerceIn(0.2f, 1f)
            }

            // On-demand channel microvolts calculation: only compute full RMS/saturation when telemetry sheet is open
            val isTelemetryOpen = _uiState.value.showTelemetrySheet
            val chMicrovolts: FloatArray
            val chSaturation: BooleanArray
            if (isTelemetryOpen) {
                if (isLiveBle) {
                    chMicrovolts = bleManager.latestChannelUv.clone()
                    val satList = bleManager.saturatedChannels.value
                    chSaturation = BooleanArray(16) { satList.contains(it) }
                } else {
                    chMicrovolts = wmEngine.getChannelMicrovolts()
                    chSaturation = wmEngine.getChannelSaturation(_uiState.value.hardwareGain)
                }
            } else {
                chMicrovolts = FloatArray(16)
                chSaturation = BooleanArray(16)
            }

            val telemetry = NeuroTelemetry(
                isLiveBle = isLiveBle,
                deviceName = if (isLiveBle) (bleManager.connectedDeviceName.value ?: "FreeEEG16") else "BCI Neuro-Simulator",
                sps = if (sps > 0f) sps else 250f,
                packetsReceived = bleManager.totalPackets.value,
                packetLossCount = bleManager.packetLoss.value,
                persistence = currentPersistence,
                bandPowers = com.example.eeg.BandPowers(
                    delta = 0.15f,
                    theta = 0.45f + 0.15f * currentPersistence,
                    alpha = 0.25f,
                    beta = 0.20f,
                    gamma = 0.35f + 0.30f * currentPersistence
                ),
                intent = NeuroIntent(
                    x = wmResult.realX,
                    y = wmResult.realY,
                    torque = wmResult.realTq,
                    magnitude = mag
                ),
                monty = MontyPrediction(
                    x = wmResult.montyX,
                    y = wmResult.montyY,
                    anomalyLoss = wmResult.anomalyLoss,
                    targetHeadingDeg = wmResult.targetHeadingDeg,
                    montyHeadingDeg = wmResult.montyHeadingDeg,
                    angleErrorDeg = wmResult.angleErrorDeg
                ),
                channelMicrovolts = chMicrovolts,
                channelSaturation = chSaturation,
                directionalMemory = compassBars
            )

            val levelTime = (System.currentTimeMillis() - levelStartTime) / 1000f

            _uiState.value = _uiState.value.copy(
                avatarX = avatar.x,
                avatarY = avatar.y,
                avatarAngle = avatar.angle,
                cameraYaw = avatar.cameraYaw,
                avatarTrail = avatar.trail.toList(),
                idealVector = Pair(idealX, idealY),
                lookaheadPath = path,
                telemetry = telemetry,
                currentLevelTimeSec = levelTime
            )

            delay(16) // ~60 FPS
        }
    }

    private fun handleMazeWon() {
        val timeSec = (System.currentTimeMillis() - levelStartTime) / 1000f
        val avgPersist = if (persistenceSamples > 0) persistenceAccum / persistenceSamples else 0f
        val solved = _uiState.value.mazesSolved + 1

        val winStats = LevelWinStats(
            levelNumber = solved,
            timeSec = timeSec,
            avgPersistence = avgPersist,
            optimalSteps = maze.optimalDistance
        )

        // Generate next hardest maze
        maze = TopoMaze(dim = _uiState.value.mazeDim)
        avatar.reset()
        levelStartTime = System.currentTimeMillis()
        persistenceAccum = 0f
        persistenceSamples = 0
        currentPersistence = 0f

        _uiState.value = _uiState.value.copy(
            mazesSolved = solved,
            mazeGrid = maze.grid,
            exitPos = maze.exitPos,
            optimalDistance = maze.optimalDistance,
            lastWinStats = winStats,
            showWinDialog = false
        )
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun setVirtualJoystick(x: Float, y: Float) {
        virtualJoyX = x.coerceIn(-1f, 1f)
        virtualJoyY = y.coerceIn(-1f, 1f)
    }

    fun toggleAutopilot() {
        val next = when (_uiState.value.controlMode) {
            GameControlMode.BCI_AUTOPILOT -> GameControlMode.BCI_DIRECT
            else -> GameControlMode.BCI_AUTOPILOT
        }
        _uiState.value = _uiState.value.copy(controlMode = next)
    }

    fun setControlMode(mode: GameControlMode) {
        _uiState.value = _uiState.value.copy(controlMode = mode)
    }

    fun toggleCameraMode() {
        val next = when (_uiState.value.cameraMode) {
            CameraViewMode.SCREEN_WORLD -> CameraViewMode.EGO_ROTATING
            CameraViewMode.EGO_ROTATING -> CameraViewMode.TANK_PRO
            CameraViewMode.TANK_PRO -> CameraViewMode.GENSHIN_ORBIT
            CameraViewMode.GENSHIN_ORBIT -> CameraViewMode.SCREEN_WORLD
        }
        _uiState.value = _uiState.value.copy(cameraMode = next)
    }

    fun toggleIdealPath() {
        _uiState.value = _uiState.value.copy(showIdealPath = !_uiState.value.showIdealPath)
    }

    fun toggleTrail() {
        _uiState.value = _uiState.value.copy(showTrail = !_uiState.value.showTrail)
    }

    fun toggleMontyVector() {
        _uiState.value = _uiState.value.copy(showMontyVector = !_uiState.value.showMontyVector)
    }

    fun toggleHudCollapsed() {
        _uiState.value = _uiState.value.copy(isHudCollapsed = !_uiState.value.isHudCollapsed)
    }

    fun regenerateMaze(newDim: Int? = null) {
        val dim = newDim ?: _uiState.value.mazeDim
        maze = TopoMaze(dim = dim)
        avatar.reset()
        levelStartTime = System.currentTimeMillis()
        persistenceAccum = 0f
        persistenceSamples = 0
        currentPersistence = 0f

        _uiState.value = _uiState.value.copy(
            mazeDim = dim,
            mazeGrid = maze.grid,
            exitPos = maze.exitPos,
            optimalDistance = maze.optimalDistance
        )
    }

    fun setSensitivity(value: Float) {
        avatar.sensitivity = value.coerceIn(0.02f, 0.20f)
        _uiState.value = _uiState.value.copy(sensitivity = avatar.sensitivity)
    }

    fun setHardwareGain(gain: Int) {
        bleManager.setHardwareGain(gain)
        _uiState.value = _uiState.value.copy(hardwareGain = gain)
    }

    fun openBleDialog() {
        _uiState.value = _uiState.value.copy(showBleDialog = true)
        bleManager.startScan()
    }

    fun closeBleDialog() {
        bleManager.stopScan()
        _uiState.value = _uiState.value.copy(showBleDialog = false)
    }

    fun connectBleDevice(address: String) {
        bleManager.connectDevice(address)
        closeBleDialog()
    }

    fun switchToSimulator() {
        bleManager.setSimulatedMode()
        closeBleDialog()
    }

    fun disconnectBle() {
        bleManager.disconnect()
        bleManager.setSimulatedMode()
    }

    fun openTelemetrySheet() {
        _uiState.value = _uiState.value.copy(showTelemetrySheet = true)
    }

    fun closeTelemetrySheet() {
        _uiState.value = _uiState.value.copy(showTelemetrySheet = false)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
