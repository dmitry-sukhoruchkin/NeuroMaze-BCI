package com.example.maze

import com.example.eeg.CameraViewMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class AvatarState(
    var sensitivity: Float = 0.12f,
    var maxSpeed: Float = 0.25f,
    var forwardSpeedScale: Float = 0.35f,
    var strafeSpeedScale: Float = 0.35f,
    var turnSpeedScale: Float = 0.65f,
    var intentGain: Float = 2.5f
) {
    var x: Float = 1.5f
    var y: Float = 1.5f
    var angle: Float = 0.0f
    var cameraYaw: Float = 0.0f

    var ctrlMoveX: Float = 0.0f
    var ctrlMoveY: Float = 0.0f
    var ctrlTorque: Float = 0.0f

    val trail = mutableListOf<PathPoint>()

    fun reset(startX: Float = 1.5f, startY: Float = 1.5f) {
        x = startX
        y = startY
        angle = 0.0f
        cameraYaw = 0.0f
        ctrlMoveX = 0f
        ctrlMoveY = 0f
        ctrlTorque = 0f
        trail.clear()
        trail.add(PathPoint(x, y))
    }

    fun updateMotion(
        intentX: Float,
        intentY: Float,
        intentTorque: Float,
        persistence: Float,
        maze: TopoMaze,
        cameraMode: CameraViewMode = CameraViewMode.SCREEN_WORLD
    ) {
        val skill = sensitivity.coerceIn(0.04f, 0.30f)
        val smooth = 0.94f - (skill * 0.1f)
        val gain = skill * intentGain
        val activeBoost = 1.0f + persistence * 3.5f

        // Leaky-integrator motion smoothing
        ctrlMoveX = ctrlMoveX * smooth + intentX * gain * (1.0f - smooth)
        ctrlMoveY = ctrlMoveY * smooth + intentY * gain * (1.0f - smooth)
        ctrlTorque = ctrlTorque * smooth + intentTorque * gain * 1.5f * (1.0f - smooth)

        val rawDx: Float
        val rawDy: Float

        when (cameraMode) {
            CameraViewMode.SCREEN_WORLD -> {
                // 1. ОСНОВНОЙ РЕЖИМ (ДЛЯ ОБУЧЕНИЯ):
                // Камера статична на лабиринт. Движение СТРОГО ОТ КАМЕРЫ (экранное):
                // Вверх = Вверх (Север), Вправо = Вправо (Восток), Вниз = Вниз (Юг), Влево = Влево (Запад).
                cameraYaw = 0f
                rawDx = ctrlMoveX * strafeSpeedScale * activeBoost
                rawDy = ctrlMoveY * forwardSpeedScale * activeBoost

                // Аватар просто вращается от сагитты, но его поворот НИ НА ЧТО НЕ ВЛИЯЕТ на движение:
                angle += ctrlTorque * activeBoost * turnSpeedScale * 2.0f
                angle = ((angle + PI) % (2.0 * PI) - PI).toFloat()
            }

            CameraViewMode.EGO_ROTATING -> {
                // 2. ЭГО-ВИД: Камера вращается вместе с поворотом аватара (лабиринт крутится на 360).
                // Движение привязано к камере (вверх по экрану = вперед в направлении камеры).
                angle += ctrlTorque * activeBoost * turnSpeedScale * 2.0f
                angle = ((angle + PI) % (2.0 * PI) - PI).toFloat()
                cameraYaw = angle

                val forwardSpeed = -ctrlMoveY * forwardSpeedScale * activeBoost
                val strafeSpeed = ctrlMoveX * strafeSpeedScale * activeBoost

                rawDx = (sin(angle.toDouble()) * forwardSpeed + cos(angle.toDouble()) * strafeSpeed).toFloat()
                rawDy = (-cos(angle.toDouble()) * forwardSpeed + sin(angle.toDouble()) * strafeSpeed).toFloat()
            }

            CameraViewMode.TANK_PRO -> {
                // 3. МУЛЬТИПЛЕЕР / ПРОФИ: Лабиринт статичен.
                // Направление движения строго ОТНОСИТЕЛЬНО ПОВОРОТА АВАТАРА!
                cameraYaw = 0f
                angle += ctrlTorque * activeBoost * turnSpeedScale * 2.0f
                angle = ((angle + PI) % (2.0 * PI) - PI).toFloat()

                // Намерение «вперед» толкает вдоль носика аватара:
                val forwardSpeed = -ctrlMoveY * forwardSpeedScale * activeBoost
                val strafeSpeed = ctrlMoveX * strafeSpeedScale * activeBoost

                rawDx = (sin(angle.toDouble()) * forwardSpeed + cos(angle.toDouble()) * strafeSpeed).toFloat()
                rawDy = (-cos(angle.toDouble()) * forwardSpeed + sin(angle.toDouble()) * strafeSpeed).toFloat()
            }

            CameraViewMode.GENSHIN_ORBIT -> {
                // 4. ГЕНШИН: Сагитта вращает камеру вокруг аватара, лабиринт крутится по cameraYaw.
                // Движение ВСЕГДА от поворота камеры:
                cameraYaw += ctrlTorque * activeBoost * turnSpeedScale * 2.0f
                cameraYaw = ((cameraYaw + PI) % (2.0 * PI) - PI).toFloat()

                val forwardSpeed = -ctrlMoveY * forwardSpeedScale * activeBoost
                val strafeSpeed = ctrlMoveX * strafeSpeedScale * activeBoost

                rawDx = (sin(cameraYaw.toDouble()) * forwardSpeed + cos(cameraYaw.toDouble()) * strafeSpeed).toFloat()
                rawDy = (-cos(cameraYaw.toDouble()) * forwardSpeed + sin(cameraYaw.toDouble()) * strafeSpeed).toFloat()

                // Аватар просто поворачивается туда, куда идёт движение в мире:
                val moveDist = hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
                if (moveDist > 0.003f) {
                    val targetAngle = kotlin.math.atan2(rawDx.toDouble(), -rawDy.toDouble()).toFloat()
                    var aDiff = targetAngle - angle
                    while (aDiff > PI) aDiff -= (2.0 * PI).toFloat()
                    while (aDiff < -PI) aDiff += (2.0 * PI).toFloat()
                    angle += (aDiff * 0.35f).toFloat()
                    angle = ((angle + PI) % (2.0 * PI) - PI).toFloat()
                }
            }
        }

        // Velocity clamping to maxSpeed
        val intendedMove = hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
        val targetDx: Float
        val targetDy: Float
        if (intendedMove > maxSpeed) {
            targetDx = (rawDx / intendedMove) * maxSpeed
            targetDy = (rawDy / intendedMove) * maxSpeed
        } else {
            targetDx = rawDx
            targetDy = rawDy
        }

        // Sub-step collision detection with walls
        val maxDelta = max(abs(targetDx), abs(targetDy))
        val steps = max(1, ceil(maxDelta / 0.04f).toInt())
        val sdx = targetDx / steps
        val sdy = targetDy / steps

        val margin = 0.20f
        for (s in 0 until steps) {
            val signX = if (sdx >= 0f) margin else -margin
            val signY = if (sdy >= 0f) margin else -margin

            if (!maze.isWall(x + sdx + signX, y)) {
                x += sdx
            }
            if (!maze.isWall(x, y + sdy + signY)) {
                y += sdy
            }
        }

        trail.add(PathPoint(x, y))
        if (trail.size > 35) {
            trail.removeAt(0)
        }
    }

    fun updateMotion(
        intentX: Float,
        intentY: Float,
        intentTorque: Float,
        persistence: Float,
        maze: TopoMaze,
        isRotatingView: Boolean
    ) {
        updateMotion(
            intentX = intentX,
            intentY = intentY,
            intentTorque = intentTorque,
            persistence = persistence,
            maze = maze,
            cameraMode = if (isRotatingView) CameraViewMode.EGO_ROTATING else CameraViewMode.SCREEN_WORLD
        )
    }
}
