package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameUiState
import com.example.ui.theme.CyberBg
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
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EegTelemetrySheet(
    uiState: GameUiState,
    onSetGain: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val telemetry = uiState.telemetry

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberBg,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        modifier = Modifier.testTag("eeg_telemetry_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (telemetry.isLiveBle) NeonEmerald else NeonCyan,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (telemetry.isLiveBle) "FREEEEG16 [LIVE HARDWARE]" else "FREEEEG16 [SIMULATOR]",
                            color = if (telemetry.isLiveBle) NeonEmerald else NeonCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (telemetry.isLiveBle) "Device: ${uiState.connectedDeviceName} | Real-time Packet Streaming" else "Running in BCI Neuro-Simulator Mode",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Telemetry",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware Stats Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "SAMPLING RATE",
                    value = String.format(Locale.US, "%.0f Hz", telemetry.sps),
                    accentColor = NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "PACKET LOSS",
                    value = "${telemetry.packetLossCount}",
                    accentColor = if (telemetry.packetLossCount > 0) NeonAmber else NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "TOTAL PACKETS",
                    value = "${telemetry.packetsReceived}",
                    accentColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // BCI Sensitivity / Gain Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "EEG Gain",
                        tint = NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADS131M08 GAIN",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4, 8, 16, 32).forEach { g ->
                        val isSel = uiState.hardwareGain == g
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSel) NeonAmber.copy(alpha = 0.2f) else CyberSurfaceVariant,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) NeonAmber else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSetGain(g) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${g}x",
                                color = if (isSel) NeonAmber else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Predictive Monty Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PREDICTIVE MONTY (NLMS)",
                            color = NeonPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format(Locale.US, "Loss: %.4f", telemetry.monty.anomalyLoss),
                            color = if (telemetry.monty.anomalyLoss < 0.1f) NeonEmerald else NeonAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Target Heading: %.1f°", telemetry.monty.targetHeadingDeg),
                            color = NeonEmerald,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format(Locale.US, "Monty: %.1f° (Δ %.1f°)", telemetry.monty.montyHeadingDeg, telemetry.monty.angleErrorDeg),
                            color = NeonPink,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 16-Channel Microvolts Grid Header
            Text(
                text = "16 CHANNELS SIGNAL STATUS",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 16 Channels List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(16) { ch ->
                    val uV = if (ch < telemetry.channelMicrovolts.size) telemetry.channelMicrovolts[ch] else 0f
                    val isSaturated = if (ch < telemetry.channelSaturation.size) telemetry.channelSaturation[ch] else false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, if (isSaturated) NeonPink else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CH ${ch + 1}",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(50.dp)
                        )

                        // Amplitude Bar
                        val barRatio = (abs(uV) / 80f).coerceIn(0.05f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barRatio)
                                    .height(8.dp)
                                    .background(
                                        if (isSaturated) NeonPink else NeonCyan,
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = String.format(Locale.US, "%+7.1f µV", uV),
                            color = if (isSaturated) NeonPink else TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(80.dp)
                        )

                        if (isSaturated) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Saturation Warning",
                                    tint = NeonPink,
                                    modifier = Modifier.height(14.dp)
                                )
                                Text(
                                    text = "SAT",
                                    color = NeonPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "OK",
                                color = NeonEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CyberSurface, RoundedCornerShape(10.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
