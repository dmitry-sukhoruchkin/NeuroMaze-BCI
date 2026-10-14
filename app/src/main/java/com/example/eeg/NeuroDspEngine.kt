package com.example.eeg

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class NeuroDspEngine {

    companion object {
        private const val TWO_PI = (2.0 * PI).toFloat()
        val COMPASS_ANGLES_DEG = floatArrayOf(90f, 45f, 0f, 315f, 270f, 225f, 180f, 135f)
    }

    // 2nd-order IIR Resonator Filter States for each channel
    // Theta (~6 Hz)
    private val thetaY1 = FloatArray(16)
    private val thetaY2 = FloatArray(16)
    private val thetaI = FloatArray(16)
    private val thetaQ = FloatArray(16)
    private val thetaPower = FloatArray(16)

    // Beta (~22 Hz)
    private val betaY1 = FloatArray(16)
    private val betaY2 = FloatArray(16)
    private val betaI = FloatArray(16)
    private val betaQ = FloatArray(16)
    private val betaPower = FloatArray(16)

    // Gamma (~40 Hz)
    private val gammaY1 = FloatArray(16)
    private val gammaY2 = FloatArray(16)
    private val gammaI = FloatArray(16)
    private val gammaQ = FloatArray(16)
    private val gammaPower = FloatArray(16)

    // DC baseline moving averages per channel
    private val channelBaselines = FloatArray(16)
    private val channelMicrovolts = FloatArray(16)
    private val channelSaturation = BooleanArray(16)

    // 120-pair imaginary Phase Locking Values (ciPLV)
    private val ciPlv120 = FloatArray(EegConstants.NUM_PAIRS)

    // Smoothed intent vector
    private var smoothedIntentX = 0f
    private var smoothedIntentY = 0f
    private var lastIntentX = 0f
    private var lastIntentY = 0f
    private var persistence = 0f

    // Predictive Monty NLMS weights [120 pairs -> 2D intent]
    private val montyWeightsX = FloatArray(EegConstants.NUM_PAIRS)
    private val montyWeightsY = FloatArray(EegConstants.NUM_PAIRS)

    // Synthetic generator phase
    private var simTime = 0f
    private var packetCounter = 0

    var eegSensitivityGain: Float = 2.5f

    init {
        // Initialize Monty weights with physical dipole projection
        for (i in 0 until EegConstants.NUM_PAIRS) {
            montyWeightsX[i] = EegConstants.PROJ_X[i] * 0.15f
            montyWeightsY[i] = EegConstants.PROJ_Y[i] * 0.15f
        }
    }

    /**
     * Process an EEG packet using digital IIR bandpass resonators and 120-pair dipole ciPLV.
     */
    fun processPacket(
        packet: EegPacket,
        idealTargetX: Float,
        idealTargetY: Float,
        sps: Float,
        satThresholdUv: Float = EegConstants.SAT_THRESHOLD_UV
    ): NeuroTelemetry {
        val fs = if (sps > 50f) sps else 250f
        val dt = 1f / fs

        // Filter coefficients for Theta (6 Hz), Beta (22 Hz), Gamma (40 Hz)
        val wTheta = (TWO_PI * 6.0f / fs)
        val cosTheta = cos(wTheta)
        val sinTheta = sin(wTheta).coerceAtLeast(1e-4f)
        val rTheta = 0.94f

        val wBeta = (TWO_PI * 22.0f / fs)
        val cosBeta = cos(wBeta)
        val sinBeta = sin(wBeta).coerceAtLeast(1e-4f)
        val rBeta = 0.92f

        val wGamma = (TWO_PI * 40.0f / fs)
        val cosGamma = cos(wGamma)
        val sinGamma = sin(wGamma).coerceAtLeast(1e-4f)
        val rGamma = 0.90f

        var totalThetaPwr = 0f
        var totalBetaPwr = 0f
        var totalGammaPwr = 0f

        var spatialSumWeight = 0f
        var spatialSumX = 0f
        var spatialSumY = 0f

        // 1. Process each channel's raw microvolts
        for (ch in 0 until 16) {
            val uV = packet.microvolts[ch]
            channelMicrovolts[ch] = uV
            channelSaturation[ch] = abs(uV) >= satThresholdUv

            // Highpass / DC baseline removal
            channelBaselines[ch] = channelBaselines[ch] * 0.995f + uV * 0.005f
            val x = (uV - channelBaselines[ch]).coerceIn(-1000f, 1000f)

            // Theta Resonator
            val yTh = 2f * rTheta * cosTheta * thetaY1[ch] - (rTheta * rTheta) * thetaY2[ch] + (1f - rTheta) * x
            thetaY2[ch] = thetaY1[ch]
            thetaY1[ch] = yTh
            val qTh = (thetaY2[ch] - yTh * cosTheta) / sinTheta
            thetaI[ch] = yTh
            thetaQ[ch] = qTh
            val pTh = yTh * yTh + qTh * qTh
            thetaPower[ch] = thetaPower[ch] * 0.9f + pTh * 0.1f
            totalThetaPwr += thetaPower[ch]

            // Beta Resonator
            val yBe = 2f * rBeta * cosBeta * betaY1[ch] - (rBeta * rBeta) * betaY2[ch] + (1f - rBeta) * x
            betaY2[ch] = betaY1[ch]
            betaY1[ch] = yBe
            val qBe = (betaY2[ch] - yBe * cosBeta) / sinBeta
            betaI[ch] = yBe
            betaQ[ch] = qBe
            val pBe = yBe * yBe + qBe * qBe
            betaPower[ch] = betaPower[ch] * 0.9f + pBe * 0.1f
            totalBetaPwr += betaPower[ch]

            // Gamma Resonator
            val yGa = 2f * rGamma * cosGamma * gammaY1[ch] - (rGamma * rGamma) * gammaY2[ch] + (1f - rGamma) * x
            gammaY2[ch] = gammaY1[ch]
            gammaY1[ch] = yGa
            val qGa = (gammaY2[ch] - yGa * cosGamma) / sinGamma
            gammaI[ch] = yGa
            gammaQ[ch] = qGa
            val pGa = yGa * yGa + qGa * qGa
            gammaPower[ch] = gammaPower[ch] * 0.9f + pGa * 0.1f
            totalGammaPwr += gammaPower[ch]

            // Spatial activation weighted centroid (Gamma envelope)
            val weight = sqrt(gammaPower[ch]).coerceAtLeast(0.01f)
            spatialSumWeight += weight
            spatialSumX += weight * EegConstants.COORDS_X[ch]
            spatialSumY += weight * EegConstants.COORDS_Y[ch]
        }

        // 2. Compute 120-pair imaginary Phase Locking Value (ciPLV) in Gamma band
        // ciPLV_ij = (Q_i * I_j - I_i * Q_j) / (sqrt(P_i * P_j) + eps)
        var sumPhaseIx = 0f
        var sumPhaseIy = 0f
        var sumTq = 0f

        for (k in 0 until EegConstants.NUM_PAIRS) {
            val i = EegConstants.PAIR_I[k]
            val j = EegConstants.PAIR_J[k]

            val crossImag = gammaQ[i] * gammaI[j] - gammaI[i] * gammaQ[j]
            val denom = sqrt(gammaPower[i] * gammaPower[j]) + 1e-4f
            val iPlv = (crossImag / denom).coerceIn(-1f, 1f)
            ciPlv120[k] = iPlv

            sumPhaseIx += iPlv * EegConstants.PROJ_X[k]
            sumPhaseIy += iPlv * EegConstants.PROJ_Y[k]
            sumTq += iPlv * EegConstants.TQ_MULT[k]
        }

        // 3. Compute Spatial Cortical Gradient
        val spatialCentroidX = if (spatialSumWeight > 1e-4f) spatialSumX / spatialSumWeight else 0f
        val spatialCentroidY = if (spatialSumWeight > 1e-4f) spatialSumY / spatialSumWeight else 0f

        // Combined Raw Intent: 65% phase gradient + 35% spatial activation
        val combinedX = (sumPhaseIx * 0.35f + (spatialCentroidX / 6.0f) * 0.65f) * eegSensitivityGain
        val combinedY = (sumPhaseIy * 0.35f + (spatialCentroidY / 6.0f) * 0.65f) * eegSensitivityGain

        // Smooth with exponential leaky filter
        smoothedIntentX = smoothedIntentX * 0.85f + combinedX * 0.15f
        smoothedIntentY = smoothedIntentY * 0.85f + combinedY * 0.15f

        val rawMag = hypot(smoothedIntentX.toDouble(), smoothedIntentY.toDouble()).toFloat()
        val normIx = if (rawMag > 0.05f) (smoothedIntentX / rawMag).coerceIn(-1f, 1f) else smoothedIntentX
        val normIy = if (rawMag > 0.05f) (smoothedIntentY / rawMag).coerceIn(-1f, 1f) else smoothedIntentY

        // 4. Update Persistence (speed boost when intention is held consistently)
        val curMag = hypot(normIx.toDouble(), normIy.toDouble()).toFloat()
        val lastMag = hypot(lastIntentX.toDouble(), lastIntentY.toDouble()).toFloat()
        val dot = normIx * lastIntentX + normIy * lastIntentY
        val cosTh = if (curMag * lastMag > 1e-5f) dot / (curMag * lastMag) else 0f

        if (curMag > 0.10f && cosTh > 0.70f) {
            persistence = (persistence + 0.05f).coerceAtMost(1.0f)
        } else {
            persistence *= 0.94f
        }
        lastIntentX = normIx
        lastIntentY = normIy

        // 5. Predictive Monty (Adaptive NLMS Online Learning)
        var predX = 0f
        var predY = 0f
        var stateEnergy = 1e-4f
        for (k in 0 until EegConstants.NUM_PAIRS) {
            val xk = ciPlv120[k]
            predX += montyWeightsX[k] * xk
            predY += montyWeightsY[k] * xk
            stateEnergy += xk * xk
        }

        val errX = idealTargetX - predX
        val errY = idealTargetY - predY
        val anomalyLoss = ((errX * errX + errY * errY) * 0.5f).coerceIn(0f, 2f)

        // NLMS online learning step: eta = 0.08 / stateEnergy
        val eta = 0.08f / stateEnergy
        for (k in 0 until EegConstants.NUM_PAIRS) {
            val xk = ciPlv120[k]
            montyWeightsX[k] = (montyWeightsX[k] + eta * errX * xk) * 0.9995f
            montyWeightsY[k] = (montyWeightsY[k] + eta * errY * xk) * 0.9995f
        }

        val predMag = hypot(predX.toDouble(), predY.toDouble()).toFloat()
        val montyNormX = if (predMag > 0.05f) predX / predMag else idealTargetX
        val montyNormY = if (predMag > 0.05f) predY / predMag else idealTargetY

        val targetHeadingDeg = (Math.toDegrees(atan2(-idealTargetY.toDouble(), idealTargetX.toDouble())).toFloat() + 360f) % 360f
        val montyHeadingDeg = (Math.toDegrees(atan2(-montyNormY.toDouble(), montyNormX.toDouble())).toFloat() + 360f) % 360f
        val angleErr = abs((montyHeadingDeg - targetHeadingDeg + 180f) % 360f - 180f)

        // 6. Directional Memory 8-Compass Bars
        val compassBars = FloatArray(8)
        val eegHeadingDeg = (Math.toDegrees(atan2(-normIy.toDouble(), normIx.toDouble())).toFloat() + 360f) % 360f
        for (c in 0 until 8) {
            val cDeg = COMPASS_ANGLES_DEG[c]
            val diff = abs((eegHeadingDeg - cDeg + 180f) % 360f - 180f)
            compassBars[c] = (1.0f - diff / 60.0f).coerceIn(0f, 1f) * curMag.coerceIn(0.2f, 1f)
        }

        // 7. Spectral Band Powers normalized
        val meanTh = (totalThetaPwr / 16f)
        val meanBe = (totalBetaPwr / 16f)
        val meanGa = (totalGammaPwr / 16f)
        val sumTotal = meanTh + meanBe + meanGa + 1e-4f

        val deltaPwr = 0.15f
        val alphaPwr = 0.30f
        val thetaPwr = (meanTh / sumTotal).coerceIn(0.1f, 0.9f)
        val betaPwr = (meanBe / sumTotal).coerceIn(0.1f, 0.9f)
        val gammaPwr = (meanGa / sumTotal).coerceIn(0.1f, 0.9f)

        return NeuroTelemetry(
            isLiveBle = true,
            deviceName = "FreeEEG16",
            sps = sps,
            persistence = persistence,
            bandPowers = BandPowers(
                delta = deltaPwr,
                theta = thetaPwr,
                alpha = alphaPwr,
                beta = betaPwr,
                gamma = gammaPwr
            ),
            intent = NeuroIntent(
                x = normIx,
                y = normIy,
                torque = sumTq.coerceIn(-1f, 1f),
                magnitude = curMag
            ),
            monty = MontyPrediction(
                x = montyNormX,
                y = montyNormY,
                anomalyLoss = anomalyLoss,
                targetHeadingDeg = targetHeadingDeg,
                montyHeadingDeg = montyHeadingDeg,
                angleErrorDeg = angleErr
            ),
            channelMicrovolts = channelMicrovolts.clone(),
            channelSaturation = channelSaturation.clone(),
            directionalMemory = compassBars
        )
    }

    /**
     * Synthesizes a realistic EEG packet responding to target navigation intent.
     * Traveling wave phase delay and cortical gradient are correctly aligned with target direction.
     */
    fun generateSyntheticPacket(targetX: Float, targetY: Float, gain: Int = 16): EegPacket {
        simTime += 0.004f // 250 Hz step
        packetCounter = (packetCounter + 1) and 0xFF

        val raw = IntArray(16)
        val uV = FloatArray(16)
        val uvScale = EegConstants.getUvScale(gain)

        val targetAngle = atan2(targetY.toDouble(), targetX.toDouble()).toFloat()

        for (ch in 0 until 16) {
            val ex = EegConstants.COORDS_X[ch]
            val ey = EegConstants.COORDS_Y[ch]

            // Physics of wave propagation towards (targetX, targetY):
            // Phase DELAY: phi = -k * (x*cos + y*sin)
            val spatialPhaseDelay = -(ex * cos(targetAngle) + ey * sin(targetAngle)) * 0.15f

            // Amplitude modulation towards target direction
            val dirAlignment = (ex * cos(targetAngle) + ey * sin(targetAngle)) / 10f
            val ampMod = 1.0f + 0.35f * dirAlignment

            val thetaWave = 22.0f * sin(TWO_PI * 6.0f * simTime + spatialPhaseDelay) * ampMod
            val alphaWave = 14.0f * sin(TWO_PI * 10.0f * simTime + ch * 0.2f)
            val betaWave = 12.0f * sin(TWO_PI * 22.0f * simTime + spatialPhaseDelay * 1.5f) * ampMod
            val gammaWave = 10.0f * sin(TWO_PI * 40.0f * simTime + spatialPhaseDelay * 2.0f) * ampMod
            val noise = ((Math.random() - 0.5) * 4.0).toFloat()

            val microvolts = (thetaWave + alphaWave + betaWave + gammaWave + noise).coerceIn(-250f, 250f)
            val rawInt = (microvolts / uvScale).toInt()

            raw[ch] = rawInt
            uV[ch] = microvolts
        }

        return EegPacket(
            counter = packetCounter,
            rawValues = raw,
            microvolts = uV,
            timestampNanos = System.nanoTime()
        )
    }
}
