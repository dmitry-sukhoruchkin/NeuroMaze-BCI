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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.eeg.BleConnectionState
import com.example.eeg.BleDeviceItem
import com.example.ui.GameUiState
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BleDeviceDialog(
    uiState: GameUiState,
    onConnect: (String) -> Unit,
    onSetGain: (Int) -> Unit,
    onUseSimulator: () -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CyberSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberBorder, RoundedCornerShape(20.dp))
                .testTag("ble_device_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FREEEEG16 BLUETOOTH",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Service: 4fafc201-1fb5-459e-8fcc-c5c9c331914b",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (uiState.connectionState == BleConnectionState.SCANNING) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hardware Gain Selector (4x, 8x, 16x, 32x)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBg, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADS131M08 GAIN:",
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
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${g}x",
                                    color = if (selected) NeonCyan else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Discovered Devices List
                Text(
                    text = "TARGET DEVICE (${uiState.discoveredDevices.size})",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (uiState.discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(CyberBg, RoundedCornerShape(12.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = "Searching",
                                tint = TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Searching for FreeEEG16 board...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Filtered strictly to FreeEEG Service UUID",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.discoveredDevices) { device ->
                            DeviceRow(
                                device = device,
                                onClick = { onConnect(device.address) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulator Alternative Button
                Button(
                    onClick = onUseSimulator,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("use_simulator_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Simulator",
                        tint = NeonPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USE BCI NEURO-SIMULATOR",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRescan,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESCAN", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLOSE", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: BleDeviceItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberBg, RoundedCornerShape(10.dp))
            .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name,
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = device.address,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${device.rssi} dBm",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "CONNECT",
                color = NeonEmerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
