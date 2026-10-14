package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeonCyan
import kotlin.math.hypot

@Composable
fun VirtualJoystick(
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    radiusDp: Float = 55f
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size((radiusDp * 2).dp)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val maxRadius = size.width / 2f
                detectDragGestures(
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = hypot(newOffset.x.toDouble(), newOffset.y.toDouble()).toFloat()
                        thumbOffset = if (dist > maxRadius) {
                            Offset(
                                (newOffset.x / dist) * maxRadius,
                                (newOffset.y / dist) * maxRadius
                            )
                        } else {
                            newOffset
                        }

                        val normX = (thumbOffset.x / maxRadius).coerceIn(-1f, 1f)
                        val normY = (thumbOffset.y / maxRadius).coerceIn(-1f, 1f)
                        onMove(normX, normY)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.size((radiusDp * 2).dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width / 2f - 4f

            // Base Ring
            drawCircle(
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = Color(0xFF334155),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = baseRadius * 0.5f,
                center = center,
                style = Stroke(width = 1f)
            )

            // Thumb Knob
            val thumbCenter = center + thumbOffset
            val thumbRadius = baseRadius * 0.38f

            drawCircle(
                color = NeonCyan.copy(alpha = 0.25f),
                radius = thumbRadius * 1.3f,
                center = thumbCenter
            )
            drawCircle(
                color = NeonCyan,
                radius = thumbRadius,
                center = thumbCenter
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius * 0.4f,
                center = thumbCenter
            )
        }
    }
}
