package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eeg.GameControlMode
import com.example.ui.GameUiState
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun NeuroHud(
    uiState: GameUiState,
    onToggleCollapse: () -> Unit,
    onSetGain: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry
    val persistence = telemetry.persistence
    val boost = 1.0f + persistence * 3.5f
    val isCollapsed = uiState.isHudCollapsed
    val satChannels = uiState.saturatedChannels

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurface.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("neuro_hud")
    ) {
        // Top status row with Collapse/Expand toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeColor = when (uiState.controlMode) {
                GameControlMode.BCI_AUTOPILOT -> NeonPink
                GameControlMode.BCI_DIRECT -> NeonCyan
                GameControlMode.MANUAL_TOUCH -> NeonAmber
            }
            val modeLabel = when (uiState.controlMode) {
                GameControlMode.BCI_AUTOPILOT -> "► MONTY AUTOPILOT"
                GameControlMode.BCI_DIRECT -> "► BCI 360° INTENT"
                GameControlMode.MANUAL_TOUCH -> "► TOUCH ASSIST"
            }

            Text(
                text = modeLabel,
                color = modeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // Saturation Warning Badge
            if (satChannels.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(NeonPink.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Saturation",
                        tint = NeonPink,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "SAT: CH ${satChannels.map { it + 1 }.joinToString(",")}",
                        color = NeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(NeonEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(NeonEmerald, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SAT: OK",
                        color = NeonEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = String.format(Locale.US, "BOOST: x%.1f", boost),
                color = if (persistence > 0.5f) NeonAmber else TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            // Collapse / Expand Button
            Row(
                modifier = Modifier
                    .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleCollapse)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .testTag("toggle_hud_collapse"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCollapsed) "SHOW HUD" else "HIDE HUD",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle HUD",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Expanded Content
        AnimatedVisibility(visible = !isCollapsed) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))

                // Hardware Gain Selector row (4x, 8x, 16x, 32x)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HARDWARE GAIN:",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(4, 8, 16, 32).forEach { g ->
                            val selected = uiState.hardwareGain == g
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (selected) NeonCyan.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSetGain(g) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${g}x",
                                    color = if (selected) NeonCyan else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Persistence Progress Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PERSISTENCE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(76.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(persistence.coerceIn(0.02f, 1f))
                                .height(5.dp)
                                .background(
                                    if (persistence > 0.6f) NeonAmber else NeonCyan,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(persistence * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Center HUD: 360 Radar + Directional Compass + Brainwave Bands
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 360° Intent Radar
                    RadarCanvas(
                        idealX = uiState.idealVector.first,
                        idealY = uiState.idealVector.second,
                        eegX = telemetry.intent.x,
                        eegY = telemetry.intent.y,
                        montyX = telemetry.monty.x,
                        montyY = telemetry.monty.y,
                        showIdeal = uiState.showIdealPath,
                        showMonty = uiState.showMontyVector,
                        modifier = Modifier.size(68.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Directional Memory Compass (8 Directions: N, NE, E, SE, S, SW, W, NW)
                    DirectionalCompassBars(
                        memory = telemetry.directionalMemory,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Brainwaves Mini Equalizer
                    BrainwavesMiniBars(
                        bandPowers = telemetry.bandPowers,
                        modifier = Modifier.width(88.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarCanvas(
    idealX: Float,
    idealY: Float,
    eegX: Float,
    eegY: Float,
    montyX: Float,
    montyY: Float,
    showIdeal: Boolean,
    showMonty: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.width / 2f - 3f

        // Radar background and outer rings
        drawCircle(color = Color(0xFF0F172A), radius = radius)
        drawCircle(color = Color(0xFF334155), radius = radius, style = Stroke(width = 1.2f))
        drawCircle(color = Color(0xFF1E293B), radius = radius * 0.5f, style = Stroke(width = 0.8f))

        // Crosshairs
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 1f
        )

        val arrowLen = radius - 4f

        // 1. Ideal Target Vector (Green)
        val idealLen = kotlin.math.hypot(idealX.toDouble(), idealY.toDouble()).toFloat()
        if (showIdeal && idealLen > 0.01f) {
            drawLine(
                color = NeonEmerald,
                start = Offset(cx, cy),
                end = Offset(cx + (idealX / idealLen) * arrowLen, cy + (idealY / idealLen) * arrowLen),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        // 2. Real EEG Intent Vector (Cyan / Blue: Рабочая память)
        val eegLen = kotlin.math.hypot(eegX.toDouble(), eegY.toDouble()).toFloat()
        if (eegLen > 0.01f) {
            val endX = cx + (eegX / eegLen) * arrowLen
            val endY = cy + (eegY / eegLen) * arrowLen
            // Glow
            drawLine(
                color = NeonCyan.copy(alpha = 0.4f),
                start = Offset(cx, cy),
                end = Offset(endX, endY),
                strokeWidth = 5.0f,
                cap = StrokeCap.Round
            )
            // Core beam
            drawLine(
                color = NeonCyan,
                start = Offset(cx, cy),
                end = Offset(endX, endY),
                strokeWidth = 3.0f,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 2.5f, center = Offset(endX, endY))
        }

        // 3. Monty Predicted Vector (Pink / Red)
        val montyLen = kotlin.math.hypot(montyX.toDouble(), montyY.toDouble()).toFloat()
        if (showMonty && montyLen > 0.01f) {
            val mEndX = cx + (montyX / montyLen) * (arrowLen * 0.9f)
            val mEndY = cy + (montyY / montyLen) * (arrowLen * 0.9f)
            drawLine(
                color = NeonPink,
                start = Offset(cx, cy),
                end = Offset(mEndX, mEndY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = NeonPink, radius = 2.0f, center = Offset(mEndX, mEndY))
        }

        // Radar Center Dot
        drawCircle(color = Color.White, radius = 2.5f, center = Offset(cx, cy))
    }
}

@Composable
private fun DirectionalCompassBars(
    memory: FloatArray,
    modifier: Modifier = Modifier
) {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    Column(modifier = modifier) {
        Text(
            text = "360° MEMORY",
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until 8) {
                val value = if (i < memory.size) memory[i] else 0f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(26.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((26 * value.coerceIn(0.05f, 1f)).dp)
                                .background(
                                    if (value > 0.6f) NeonEmerald else NeonPurple,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = labels[i],
                        color = if (value > 0.5f) TextPrimary else TextMuted,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun BrainwavesMiniBars(
    bandPowers: com.example.eeg.BandPowers,
    modifier: Modifier = Modifier
) {
    val bands = listOf(
        Pair("δ", bandPowers.delta),
        Pair("θ", bandPowers.theta),
        Pair("α", bandPowers.alpha),
        Pair("β", bandPowers.beta),
        Pair("γ", bandPowers.gamma)
    )

    Column(modifier = modifier) {
        Text(
            text = "RHYTHMS",
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEach { (name, pwr) ->
                val barColor = when (name) {
                    "θ" -> NeonCyan
                    "β" -> NeonAmber
                    "γ" -> NeonPink
                    else -> NeonPurple
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(26.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((26 * pwr.coerceIn(0.1f, 1f)).dp)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = name,
                        color = barColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
