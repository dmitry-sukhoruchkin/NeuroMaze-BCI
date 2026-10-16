package com.example.eeg

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class FreeEegBleManager(private val context: Context) {

    companion object {
        private const val TAG = "FreeEegBle"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _packetFlow = MutableSharedFlow<EegPacket>(extraBufferCapacity = 1024)
    val packetFlow: SharedFlow<EegPacket> = _packetFlow.asSharedFlow()

    // Thread-safe ring buffer for zero-lag packet ingestion in the physics loop
    private val incomingQueue = ConcurrentLinkedQueue<EegPacket>()
    val latestPacket = AtomicReference<EegPacket?>(null)

    private val _sps = MutableStateFlow(0f)
    val sps: StateFlow<Float> = _sps.asStateFlow()

    private val _packetLoss = MutableStateFlow(0)
    val packetLoss: StateFlow<Int> = _packetLoss.asStateFlow()

    private val _totalPackets = MutableStateFlow(0L)
    val totalPackets: StateFlow<Long> = _totalPackets.asStateFlow()

    var currentHardwareGain: Int = 16
        private set
    var currentUvScale: Float = EegConstants.getUvScale(16)
        private set
    var currentSatThresholdUv: Float = EegConstants.getSatThresholdUv(16)
        private set

    private val _saturatedChannels = MutableStateFlow<List<Int>>(emptyList())
    val saturatedChannels: StateFlow<List<Int>> = _saturatedChannels.asStateFlow()

    val latestChannelUv = FloatArray(16)

    private var lastCounter = -1
    private val packetCounterInSec = AtomicInteger(0)
    private var lastSpsTime = System.currentTimeMillis()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // STRICT CHECK: Only allow devices advertising FreeEEG service UUID or named FreeEEG
                val serviceUuids = result.scanRecord?.serviceUuids
                val hasTargetService = serviceUuids?.any { it.uuid == EegConstants.SERVICE_UUID } == true
                val devName = device.name ?: result.scanRecord?.deviceName ?: ""
                val isFreeEegName = devName.contains("FreeEEG", ignoreCase = true) || devName.contains("EEG", ignoreCase = true)

                if (hasTargetService || isFreeEegName) {
                    val displayName = if (devName.isNotBlank()) devName else "FreeEEG16"
                    val item = BleDeviceItem(
                        name = displayName,
                        address = device.address,
                        rssi = result.rssi
                    )
                    val current = _discoveredDevices.value.toMutableList()
                    val existingIndex = current.indexOfFirst { it.address == item.address }
                    if (existingIndex >= 0) {
                        current[existingIndex] = item
                    } else {
                        current.add(item)
                    }
                    _discoveredDevices.value = current.sortedByDescending { it.rssi }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, requesting MTU 512 and discovering services...")
                    _connectionState.value = BleConnectionState.CONNECTING
                    _connectedDeviceName.value = gatt?.device?.name ?: "FreeEEG16"
                    try {
                        gatt?.requestMtu(512)
                        scope.launch {
                            delay(300)
                            gatt?.discoverServices()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException discovering services", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected")
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    _connectedDeviceName.value = null
                    bluetoothGatt = null
                    dataCharacteristic = null
                    cmdCharacteristic = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(EegConstants.SERVICE_UUID)
                if (service != null) {
                    dataCharacteristic = service.getCharacteristic(EegConstants.DATA_CHAR_UUID)
                    cmdCharacteristic = service.getCharacteristic(EegConstants.CMD_CHAR_UUID)

                    dataCharacteristic?.let { char ->
                        try {
                            gatt.setCharacteristicNotification(char, true)
                            val desc = char.getDescriptor(EegConstants.CLIENT_CONFIG_DESCRIPTOR)
                            if (desc != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(desc)
                                }
                            }
                            Log.i(TAG, "Notifications enabled on FreeEEG DATA characteristic")
                            _connectionState.value = BleConnectionState.CONNECTED

                            // Apply hardware gain and send stream start command
                            scope.launch {
                                delay(150)
                                applyHardwareGainInternal(gatt, currentHardwareGain)
                                delay(150)
                                sendStartStreamCommand(gatt)
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "SecurityException enabling notifications", e)
                        }
                    }
                } else {
                    Log.w(TAG, "FreeEEG service not found on device")
                }
            }
        }

        // Android 13+ (API 33+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseEegPacket(value)
        }

        // Android < 13
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { data ->
                parseEegPacket(data)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun setHardwareGain(gain: Int) {
        if (gain !in EegConstants.SUPPORTED_GAINS) return
        currentHardwareGain = gain
        currentUvScale = EegConstants.getUvScale(gain)
        currentSatThresholdUv = EegConstants.getSatThresholdUv(gain)

        val gatt = bluetoothGatt ?: return
        scope.launch {
            applyHardwareGainInternal(gatt, gain)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun applyHardwareGainInternal(gatt: BluetoothGatt, gain: Int) {
        val cmdChar = cmdCharacteristic ?: return
        val code = EegConstants.GAIN_TO_CODE[gain] ?: 4
        val regVal = (code shl 12) or (code shl 8) or (code shl 4) or code

        // GAIN1 register 0x04
        writeRegister(gatt, cmdChar, 0x04.toByte(), regVal)
        delay(80)
        // GAIN2 register 0x05
        writeRegister(gatt, cmdChar, 0x05.toByte(), regVal)
        Log.i(TAG, "Configured ADS131M08 Gain: ${gain}x (reg: 0x${Integer.toHexString(regVal)})")
    }

    @SuppressLint("MissingPermission")
    private fun writeRegister(gatt: BluetoothGatt, cmdChar: BluetoothGattCharacteristic, regAddr: Byte, value: Int) {
        try {
            val payload = byteArrayOf(
                regAddr,
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    cmdChar,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                @Suppress("DEPRECATION")
                cmdChar.value = payload
                @Suppress("DEPRECATION")
                cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(cmdChar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing register 0x${Integer.toHexString(regAddr.toInt())}", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendStartStreamCommand(gatt: BluetoothGatt) {
        val cmdChar = cmdCharacteristic ?: return
        try {
            // Command to start stream from FreeEEG ADS131M08 (0x06, 0x00, 0x00, 0x01, 0x00)
            val startCmd = byteArrayOf(0x06, 0x00, 0x00, 0x01, 0x00)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    cmdChar,
                    startCmd,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                @Suppress("DEPRECATION")
                cmdChar.value = startCmd
                @Suppress("DEPRECATION")
                cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(cmdChar)
            }
            Log.i(TAG, "Sent START_STREAM command to FreeEEG")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending start stream command", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }
        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.SCANNING

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            // Strict filter matching only FreeEEG Service UUID
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(EegConstants.SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in startScan", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            if (_connectionState.value == BleConnectionState.SCANNING) {
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(address: String) {
        stopScan()
        _connectionState.value = BleConnectionState.CONNECTING
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            bluetoothGatt?.close()
            bluetoothGatt = device?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting GATT", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disconnecting", e)
        }
        bluetoothGatt = null
        incomingQueue.clear()
        latestPacket.set(null)
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _saturatedChannels.value = emptyList()
    }

    fun setSimulatedMode() {
        disconnect()
        _connectionState.value = BleConnectionState.SIMULATED
        _connectedDeviceName.value = "BCI Neuro-Simulator"
    }

    /**
     * Drains all packets accumulated since last frame tick.
     */
    fun pollPackets(): List<EegPacket> {
        val list = ArrayList<EegPacket>(32)
        while (true) {
            val p = incomingQueue.poll() ?: break
            list.add(p)
            if (list.size >= 128) break
        }
        return list
    }

    fun parseEegPacket(data: ByteArray) {
        if (data.size != EegConstants.PACKET_SIZE) return
        if (data[0] != EegConstants.PACKET_HEADER || data[50] != EegConstants.PACKET_FOOTER) return

        val counter = data[1].toInt() and 0xFF
        if (lastCounter != -1) {
            val expected = (lastCounter + 1) and 0xFF
            if (counter != expected) {
                val lost = (counter - expected + 256) and 0xFF
                _packetLoss.value += lost
            }
        }
        lastCounter = counter

        val raw = IntArray(16)
        val uV = FloatArray(16)
        val saturated = mutableListOf<Int>()

        val uvScale = currentUvScale
        val satThresh = currentSatThresholdUv

        for (i in 0 until 16) {
            val k = 2 + i * 3
            val b0 = data[k].toInt() and 0xFF
            val b1 = data[k + 1].toInt() and 0xFF
            val b2 = data[k + 2].toInt() and 0xFF
            var v = (b0 shl 16) or (b1 shl 8) or b2
            if ((b0 and 0x80) != 0) {
                v -= 16777216
            }
            raw[i] = v
            val valUv = v * uvScale
            uV[i] = valUv

            // Check oversaturation (>8,000,000 ADC counts or above voltage threshold)
            if (abs(v) >= 8000000 || abs(valUv) >= satThresh) {
                saturated.add(i)
            }
        }

        _saturatedChannels.value = saturated
        System.arraycopy(uV, 0, latestChannelUv, 0, 16)

        val packet = EegPacket(
            counter = counter,
            rawValues = raw,
            microvolts = uV,
            timestampNanos = System.nanoTime()
        )

        incomingQueue.offer(packet)
        latestPacket.set(packet)
        _packetFlow.tryEmit(packet)
        _totalPackets.value++

        val count = packetCounterInSec.incrementAndGet()
        val now = System.currentTimeMillis()
        if (now - lastSpsTime >= 1000L) {
            val elapsedSec = (now - lastSpsTime) / 1000f
            _sps.value = count / elapsedSec
            packetCounterInSec.set(0)
            lastSpsTime = now
        }
    }
}
