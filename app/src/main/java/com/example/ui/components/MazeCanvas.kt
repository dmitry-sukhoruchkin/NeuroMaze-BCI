package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.eeg.CameraViewMode
import com.example.eeg.GameControlMode
import com.example.ui.GameUiState
import com.example.ui.theme.AvatarCyan
import com.example.ui.theme.AvatarPink
import com.example.ui.theme.CyberBg
import com.example.ui.theme.ExitGlow
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.WallDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MazeCanvas(
    uiState: GameUiState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val dim = uiState.mazeDim
        val cellSize = min(width - 40f, height - 40f) / dim
        val gridWidth = dim * cellSize
        val gridHeight = dim * cellSize
        val offsetX = (width - gridWidth) / 2f
        val offsetY = (height - gridHeight) / 2f

        val isRotating = uiState.cameraMode == CameraViewMode.EGO_ROTATING || uiState.cameraMode == CameraViewMode.GENSHIN_ORBIT
        val activeYaw = if (uiState.cameraMode == CameraViewMode.GENSHIN_ORBIT) uiState.cameraYaw else uiState.avatarAngle
        val rotationDeg = if (isRotating) -Math.toDegrees(activeYaw.toDouble()).toFloat() else 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerCanvas = Offset(width / 2f, height / 2f)

            if (isRotating) {
                // In Ego view: camera stays centered on avatar, rotated around avatar
                val avatarScreenX = offsetX + uiState.avatarX * cellSize
                val avatarScreenY = offsetY + uiState.avatarY * cellSize
                val pivot = Offset(avatarScreenX, avatarScreenY)

                rotate(degrees = rotationDeg, pivot = centerCanvas) {
                    drawMazeContent(
                        uiState = uiState,
                        cellSize = cellSize,
                        offsetX = offsetX + (centerCanvas.x - avatarScreenX),
                        offsetY = offsetY + (centerCanvas.y - avatarScreenY),
                        gridWidth = gridWidth,
                        gridHeight = gridHeight
                    )
                }
            } else {
                // In World view: fixed centered top-down view
                drawMazeContent(
                    uiState = uiState,
                    cellSize = cellSize,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight
                )
            }
        }
    }
}

private fun DrawScope.drawMazeContent(
    uiState: GameUiState,
    cellSize: Float,
    offsetX: Float,
    offsetY: Float,
    gridWidth: Float,
    gridHeight: Float
) {
    val dim = uiState.mazeDim
    val grid = uiState.mazeGrid
    val persistence = uiState.telemetry.persistence

    // 1. Maze Outer Boundary Frame
    drawRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(offsetX - 2f, offsetY - 2f),
        size = Size(gridWidth + 4f, gridHeight + 4f),
        style = Stroke(width = 2f)
    )

    // 2. Draw Maze Tiles (Walls & Exit)
    val wallGlowAlpha = (0.25f + persistence * 0.5f).coerceIn(0.2f, 0.8f)
    val wallBorderColor = Color(
        red = 0.13f + persistence * 0.15f,
        green = 0.50f + persistence * 0.35f,
        blue = 0.85f + persistence * 0.15f,
        alpha = wallGlowAlpha
    )

    for (gy in 0 until dim) {
        if (gy >= grid.size) continue
        for (gx in 0 until dim) {
            if (gx >= grid[gy].size) continue
            val tile = grid[gy][gx]
            val px = offsetX + gx * cellSize
            val py = offsetY + gy * cellSize

            if (tile == 1) { // Wall
                drawRect(
                    color = WallDark,
                    topLeft = Offset(px, py),
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = wallBorderColor,
                    topLeft = Offset(px, py),
                    size = Size(cellSize, cellSize),
                    style = Stroke(width = 1.2f)
                )
            } else if (tile == 2) { // Exit Portal
                val exitCenterX = px + cellSize * 0.5f
                val exitCenterY = py + cellSize * 0.5f
                // Concentric energy circles
                drawCircle(
                    color = ExitGlow.copy(alpha = 0.3f),
                    radius = cellSize * 0.45f,
                    center = Offset(exitCenterX, exitCenterY)
                )
                drawCircle(
                    color = ExitGlow,
                    radius = cellSize * 0.32f,
                    center = Offset(exitCenterX, exitCenterY),
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = Color.White,
                    radius = cellSize * 0.12f,
                    center = Offset(exitCenterX, exitCenterY)
                )
            }
        }
    }

    // 3. Draw Ideal BFS Lookahead Path (Green line)
    if (uiState.showIdealPath && uiState.lookaheadPath.size > 1) {
        val path = Path()
        val first = uiState.lookaheadPath[0]
        path.moveTo(offsetX + first.x * cellSize, offsetY + first.y * cellSize)
        for (i in 1 until uiState.lookaheadPath.size) {
            val pt = uiState.lookaheadPath[i]
            path.lineTo(offsetX + pt.x * cellSize, offsetY + pt.y * cellSize)
        }
        drawPath(
            path = path,
            color = NeonEmerald.copy(alpha = 0.85f),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
    }

    // 4. Draw Avatar Motion Trail
    if (uiState.showTrail && uiState.avatarTrail.size > 1) {
        val trailColor = if (uiState.controlMode == GameControlMode.BCI_AUTOPILOT) AvatarPink else AvatarCyan
        val trailPath = Path()
        val first = uiState.avatarTrail[0]
        trailPath.moveTo(offsetX + first.x * cellSize, offsetY + first.y * cellSize)
        for (i in 1 until uiState.avatarTrail.size) {
            val pt = uiState.avatarTrail[i]
            trailPath.lineTo(offsetX + pt.x * cellSize, offsetY + pt.y * cellSize)
        }
        drawPath(
            path = trailPath,
            color = trailColor.copy(alpha = 0.70f),
            style = Stroke(width = 3.0f, cap = StrokeCap.Round)
        )
    }

    // 5. Draw Avatar Body and Orientation Beacon
    val avatarPx = offsetX + uiState.avatarX * cellSize
    val avatarPy = offsetY + uiState.avatarY * cellSize
    val avatarCenter = Offset(avatarPx, avatarPy)
    val avatarRadius = cellSize * 0.28f

    // Avatar Glow
    val avatarColor = if (uiState.controlMode == GameControlMode.BCI_AUTOPILOT) {
        AvatarPink
    } else {
        AvatarCyan
    }

    drawCircle(
        color = avatarColor.copy(alpha = 0.35f + persistence * 0.4f),
        radius = avatarRadius * (1.2f + persistence * 0.5f),
        center = avatarCenter
    )
    drawCircle(
        color = avatarColor,
        radius = avatarRadius,
        center = avatarCenter
    )
    drawCircle(
        color = Color.White,
        radius = avatarRadius * 0.35f,
        center = avatarCenter
    )

    // Avatar Nose / Heading Direction
    val noseLen = cellSize * 0.40f
    val noseX = avatarPx + sin(uiState.avatarAngle.toDouble()).toFloat() * noseLen
    val noseY = avatarPy - cos(uiState.avatarAngle.toDouble()).toFloat() * noseLen
    drawLine(
        color = Color.White,
        start = avatarCenter,
        end = Offset(noseX, noseY),
        strokeWidth = 3.0f,
        cap = StrokeCap.Round
    )

    // 6. Draw Vector Overlays (Ideal, Real EEG Intent, Monty Prediction)
    val vectorScale = cellSize * 1.5f

    // Ideal Vector (Green)
    if (uiState.showIdealPath) {
        val (ix, iy) = uiState.idealVector
        drawLine(
            color = NeonEmerald,
            start = avatarCenter,
            end = Offset(avatarPx + ix * vectorScale, avatarPy + iy * vectorScale),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }

    // Real EEG Intent Vector (Cyan / Blue: Рабочая память)
    val eegX = uiState.telemetry.intent.x
    val eegY = uiState.telemetry.intent.y
    val eegLen = kotlin.math.hypot(eegX.toDouble(), eegY.toDouble()).toFloat()
    if (eegLen > 0.01f) {
        val wmScale = vectorScale * (1.0f + persistence * 0.4f)
        val endPoint = Offset(avatarPx + (eegX / eegLen) * wmScale, avatarPy + (eegY / eegLen) * wmScale)
        // Outer glow
        drawLine(
            color = NeonCyan.copy(alpha = 0.4f),
            start = avatarCenter,
            end = endPoint,
            strokeWidth = 7.0f,
            cap = StrokeCap.Round
        )
        // Main crisp cyan beam
        drawLine(
            color = NeonCyan,
            start = avatarCenter,
            end = endPoint,
            strokeWidth = 4.0f,
            cap = StrokeCap.Round
        )
        // Arrow head indicator
        drawCircle(
            color = Color.White,
            radius = 3.5f,
            center = endPoint
        )
    }

    // Monty Predicted Vector (Pink / Red: Monty автопилот)
    if (uiState.showMontyVector) {
        val mx = uiState.telemetry.monty.x
        val my = uiState.telemetry.monty.y
        val mLen = kotlin.math.hypot(mx.toDouble(), my.toDouble()).toFloat()
        if (mLen > 0.01f) {
            val montyScale = vectorScale * 0.9f
            val montyEndPoint = Offset(avatarPx + (mx / mLen) * montyScale, avatarPy + (my / mLen) * montyScale)
            drawLine(
                color = NeonPink,
                start = avatarCenter,
                end = montyEndPoint,
                strokeWidth = 3.0f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = NeonPink,
                radius = 2.5f,
                center = montyEndPoint
            )
        }
    }
}
