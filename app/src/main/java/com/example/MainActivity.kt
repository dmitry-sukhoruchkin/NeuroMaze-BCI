package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NeuroMazeViewModel
import com.example.ui.components.BleDeviceDialog
import com.example.ui.components.EegTelemetrySheet
import com.example.ui.components.GameBottomControlBar
import com.example.ui.components.GameTopBar
import com.example.ui.components.LevelWinDialog
import com.example.ui.components.MazeCanvas
import com.example.ui.components.NeuroHud
import com.example.ui.components.VirtualJoystick
import com.example.ui.theme.CyberBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NeuroMazeApp()
            }
        }
    }
}

@Composable
fun NeuroMazeApp(
    viewModel: NeuroMazeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // Bluetooth permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Trigger haptic vibration on level complete
    LaunchedEffect(uiState.showWinDialog) {
        if (uiState.showWinDialog) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 80, 50, 120),
                            intArrayOf(0, 180, 0, 255),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(150L)
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg),
        containerColor = CyberBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Status & Navigation Bar
            GameTopBar(
                uiState = uiState,
                onOpenBle = { viewModel.openBleDialog() },
                onOpenTelemetry = { viewModel.openTelemetrySheet() },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )

            // 2. Middle Maze Area (weight 1f guarantees NO OVERLAP with the bottom HUD)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                MazeCanvas(
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )

                // Virtual Touch Thumbstick in bottom-right corner of maze
                VirtualJoystick(
                    onMove = { x, y ->
                        viewModel.setVirtualJoystick(x, y)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                )
            }

            // 3. Collapsible Neuro HUD (360 radar, saturation status, gain selector)
            NeuroHud(
                uiState = uiState,
                onToggleCollapse = { viewModel.toggleHudCollapsed() },
                onSetGain = { g -> viewModel.setHardwareGain(g) },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )

            // 4. Quick Control Bar
            GameBottomControlBar(
                uiState = uiState,
                onToggleAutopilot = { viewModel.toggleAutopilot() },
                onToggleCamera = { viewModel.toggleCameraMode() },
                onTogglePath = { viewModel.toggleIdealPath() },
                onRegenerateMaze = { viewModel.regenerateMaze() },
                onChangeDim = { dim -> viewModel.regenerateMaze(dim) },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Dialogs & Sheets
        if (uiState.showBleDialog) {
            BleDeviceDialog(
                uiState = uiState,
                onConnect = { address -> viewModel.connectBleDevice(address) },
                onSetGain = { g -> viewModel.setHardwareGain(g) },
                onUseSimulator = { viewModel.switchToSimulator() },
                onRescan = { viewModel.openBleDialog() },
                onDismiss = { viewModel.closeBleDialog() }
            )
        }

        if (uiState.showTelemetrySheet) {
            EegTelemetrySheet(
                uiState = uiState,
                onSetGain = { gain -> viewModel.setHardwareGain(gain) },
                onDismiss = { viewModel.closeTelemetrySheet() }
            )
        }

        if (uiState.showWinDialog) {
            LevelWinDialog(
                stats = uiState.lastWinStats,
                onNextMaze = { viewModel.dismissWinDialog() }
            )
        }
    }
}
