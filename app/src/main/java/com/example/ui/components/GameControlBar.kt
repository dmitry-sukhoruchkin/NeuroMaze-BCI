package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eeg.BleConnectionState
import com.example.eeg.CameraViewMode
import com.example.eeg.GameControlMode
import com.example.ui.GameUiState
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GameTopBar(
    uiState: GameUiState,
    onOpenBle: () -> Unit,
    onOpenTelemetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberSurface.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Maze info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(NeonCyan, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "NEUROMAZE BCI",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "MAZE #${uiState.mazesSolved + 1} (${uiState.mazeDim}x${uiState.mazeDim})",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Action Buttons: BLE Device pill + Telemetry Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // BLE Connection Pill
            val (pillBg, pillBorder, pillText, pillColor) = when (uiState.connectionState) {
                BleConnectionState.CONNECTED -> Quadruple(
                    NeonEmerald.copy(alpha = 0.15f),
                    NeonEmerald,
                    "LIVE BLE",
                    NeonEmerald
                )
                BleConnectionState.SCANNING -> Quadruple(
                    NeonAmber.copy(alpha = 0.15f),
                    NeonAmber,
                    "SCANNING",
                    NeonAmber
                )
                BleConnectionState.CONNECTING -> Quadruple(
                    NeonAmber.copy(alpha = 0.15f),
                    NeonAmber,
                    "CONNECTING",
                    NeonAmber
                )
                BleConnectionState.SIMULATED -> Quadruple(
                    NeonCyan.copy(alpha = 0.15f),
                    NeonCyan,
                    "SIMULATOR",
                    NeonCyan
                )
                BleConnectionState.DISCONNECTED -> Quadruple(
                    Color(0xFF334155),
                    CyberBorder,
                    "CONNECT EEG",
                    TextSecondary
                )
            }

            Row(
                modifier = Modifier
                    .background(pillBg, RoundedCornerShape(20.dp))
                    .border(1.dp, pillBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenBle)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("ble_status_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = pillColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pillText,
                    color = pillColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 16-ch Telemetry Pill
            Row(
                modifier = Modifier
                    .background(CyberSurfaceVariant, RoundedCornerShape(20.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenTelemetry)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("open_telemetry_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "EEG Signals",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "16-CH EEG",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GameBottomControlBar(
    uiState: GameUiState,
    onToggleAutopilot: () -> Unit,
    onToggleCamera: () -> Unit,
    onTogglePath: () -> Unit,
    onRegenerateMaze: () -> Unit,
    onChangeDim: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .background(CyberSurface.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Autopilot (Monty)
        val isAutopilot = uiState.controlMode == GameControlMode.BCI_AUTOPILOT
        ControlButton(
            icon = Icons.Default.AutoMode,
            label = if (isAutopilot) "AUTOPILOT: ON" else "AUTOPILOT: OFF",
            isActive = isAutopilot,
            activeColor = NeonPink,
            onClick = onToggleAutopilot,
            tag = "toggle_autopilot_button"
        )

        // Camera (1. WORLD SCREEN vs 2. EGO 360 vs 3. TANK PRO vs 4. GENSHIN ORBIT)
        val camLabel = when (uiState.cameraMode) {
            CameraViewMode.SCREEN_WORLD -> "CAM 1: WORLD"
            CameraViewMode.EGO_ROTATING -> "CAM 2: EGO 360"
            CameraViewMode.TANK_PRO -> "CAM 3: TANK (PRO)"
            CameraViewMode.GENSHIN_ORBIT -> "CAM 4: GENSHIN"
        }
        ControlButton(
            icon = Icons.Default.Navigation,
            label = camLabel,
            isActive = uiState.cameraMode != CameraViewMode.SCREEN_WORLD,
            activeColor = when (uiState.cameraMode) {
                CameraViewMode.EGO_ROTATING -> NeonCyan
                CameraViewMode.TANK_PRO -> NeonEmerald
                CameraViewMode.GENSHIN_ORBIT -> NeonPink
                else -> NeonCyan
            },
            onClick = onToggleCamera,
            tag = "toggle_camera_button"
        )

        // Ideal Path Line Toggle
        ControlButton(
            icon = Icons.Default.Route,
            label = if (uiState.showIdealPath) "PATH: ON" else "PATH: OFF",
            isActive = uiState.showIdealPath,
            activeColor = NeonEmerald,
            onClick = onTogglePath,
            tag = "toggle_path_button"
        )

        // New Maze Button
        ControlButton(
            icon = Icons.Default.Refresh,
            label = "NEW MAZE",
            isActive = false,
            activeColor = TextPrimary,
            onClick = onRegenerateMaze,
            tag = "new_maze_button"
        )

        // Difficulty Chips (9x9, 13x13, 17x17)
        Row(
            modifier = Modifier
                .background(CyberSurfaceVariant, RoundedCornerShape(10.dp))
                .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(9, 13, 17).forEach { d ->
                val selected = uiState.mazeDim == d
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) NeonCyan else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onChangeDim(d) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${d}x$d",
                        color = if (selected) NeonCyan else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.15f) else CyberSurfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (isActive) activeColor else CyberBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
