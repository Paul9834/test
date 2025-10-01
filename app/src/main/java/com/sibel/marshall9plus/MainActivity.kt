package com.sibel.marshall9plus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.aratek.trustfinger.sdk.DeviceOpenListener
import com.aratek.trustfinger.sdk.MultiFingerCallback
import com.aratek.trustfinger.sdk.MultiFingerParam
import com.aratek.trustfinger.sdk.SegmentImageDesc
import com.aratek.trustfinger.sdk.TrustFinger
import com.aratek.trustfinger.sdk.TrustFingerDevice
import com.aratek.trustfinger.sdk.TrustFingerException
import com.sibel.marshall9plus.ui.theme.Marshall9PlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var trustFinger: TrustFinger? = null
    private var device: TrustFingerDevice? = null
    private var isRunningOnEmulator = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isRunningOnEmulator = checkIfEmulator()
        initializeSDK()

        setContent {
            Marshall9PlusTheme {
                MultiFingerprintDemoScreen()
            }
        }
    }

    private fun checkIfEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    private fun initializeSDK() {
        if (isRunningOnEmulator) {
            Log.w("TrustFinger", "Running on emulator - SDK not initialized")
            return
        }

        try {
            trustFinger = TrustFinger.getInstance(this)
            trustFinger?.initialize()
            Log.i("TrustFinger", "SDK initialized successfully")
        } catch (e: TrustFingerException) {
            Log.e("TrustFinger", "Failed to initialize SDK", e)
            e.printStackTrace()
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TrustFinger", "Native library not found - requires physical device", e)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MultiFingerprintDemoScreen() {
        var statusMessage by remember {
            mutableStateOf(
                if (isRunningOnEmulator) "⚠️ Emulator detected - Connect a physical Android device"
                else "SDK Ready"
            )
        }
        var deviceCount by remember { mutableStateOf(0) }
        var capturedFingerprints by remember { mutableStateOf<List<FingerprintData>>(emptyList()) }
        var isProcessing by remember { mutableStateOf(false) }
        var selectedMode by remember { mutableStateOf(CaptureMode.FOUR_FINGERS_RIGHT) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (!isRunningOnEmulator) {
                deviceCount = getDeviceCount()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Marshall 9 Plus - Multi Finger") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRunningOnEmulator) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SDK Info",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (isRunningOnEmulator) {
                                Text(
                                    "⚠️ Running on Emulator",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "TrustFinger SDK requires a physical Android device with USB OTG support",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("Version: ${trustFinger?.getSdkJarVersion() ?: "N/A"}")
                                Text("Algorithm: ${trustFinger?.getAlgVersion() ?: "N/A"}")
                                Text("Devices Connected: $deviceCount")
                            }

                            Text(
                                text = statusMessage,
                                color = if (statusMessage.contains("Error") || statusMessage.contains("⚠️")) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }

                if (!isRunningOnEmulator) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Capture Mode",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                CaptureMode.values().forEach { mode ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedMode == mode,
                                            onClick = { selectedMode = mode }
                                        )
                                        Text(mode.displayName)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    openDevice { message ->
                                        statusMessage = message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing
                        ) {
                            Text("Open Device")
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                isProcessing = true
                                capturedFingerprints = emptyList()
                                scope.launch {
                                    captureMultipleFingerprints(
                                        mode = selectedMode,
                                        onStatus = { message ->
                                            statusMessage = message
                                        },
                                        onFingerprintsExtracted = { fingerprints ->
                                            capturedFingerprints = fingerprints
                                            isProcessing = false
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = device != null && !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isProcessing) "Capturing..." else "Capture ${selectedMode.displayName}")
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                closeDevice()
                                statusMessage = "Device Closed"
                                capturedFingerprints = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = device != null && !isProcessing
                        ) {
                            Text("Close Device")
                        }
                    }

                    if (capturedFingerprints.isNotEmpty()) {
                        item {
                            Text(
                                text = "Captured: ${capturedFingerprints.size} fingerprint(s)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        itemsIndexed(capturedFingerprints) { index, fingerprintData ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Finger #${index + 1} - ${fingerprintData.position}",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Image(
                                        bitmap = fingerprintData.bitmap.asImageBitmap(),
                                        contentDescription = "Fingerprint ${index + 1}",
                                        modifier = Modifier
                                            .size(200.dp)
                                            .border(1.dp, Color.Gray)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Quality", style = MaterialTheme.typography.labelSmall)
                                            Text(
                                                text = "${fingerprintData.quality}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = when {
                                                    fingerprintData.quality >= 80 -> Color(0xFF4CAF50)
                                                    fingerprintData.quality >= 50 -> Color(0xFFFF9800)
                                                    else -> Color(0xFFF44336)
                                                }
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Feature Size",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = "${fingerprintData.featureData.size} bytes",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getDeviceCount(): Int {
        return try {
            trustFinger?.deviceCount ?: 0
        } catch (e: TrustFingerException) {
            0
        }
    }

    private suspend fun openDevice(onStatus: (String) -> Unit) = withContext(Dispatchers.Main) {
        try {
            trustFinger?.openDevice(0, object : DeviceOpenListener {
                override fun openSuccess(trustFingerDevice: TrustFingerDevice) {
                    device = trustFingerDevice
                    val info = device?.imageInfo
                    onStatus("Device Opened: ${info?.width}x${info?.height} @ ${info?.resolution}dpi")
                }

                override fun openFail(errorMessage: String) {
                    onStatus("Error: $errorMessage")
                }
            })
        } catch (e: TrustFingerException) {
            onStatus("Error: ${e.message}")
        }
    }

    private suspend fun captureMultipleFingerprints(
        mode: CaptureMode,
        onStatus: (String) -> Unit,
        onFingerprintsExtracted: (List<FingerprintData>) -> Unit
    ) = withContext(Dispatchers.Main) {
        val currentDevice = device
        if (currentDevice == null) {
            onStatus("Error: Device not opened")
            return@withContext
        }

        try {
            onStatus("Place ${mode.displayName} on sensor...")

            val param = MultiFingerParam(
                mode.inputImageType,
                15000,
                1
            )

            currentDevice.multiFingerCapture(param, object : MultiFingerCallback {
                override fun multiFingerCallback(
                    occurredEventCode: Int,
                    rawData: ByteArray,
                    segmentImageDesc: Array<SegmentImageDesc>,
                    numberOfSegment: Int
                ) {
                    when (occurredEventCode) {
                        1 -> {
                            onStatus("Processing image...")
                        }

                        9 -> {
                            if (numberOfSegment > 0) {
                                onStatus("Successfully captured $numberOfSegment fingerprint(s)!")

                                val fingerprints = mutableListOf<FingerprintData>()

                                for (i in 0 until numberOfSegment) {
                                    val desc = segmentImageDesc[i]
                                    val bmpData = currentDevice.rawToBmp(
                                        desc.pSegmentImagePtr,
                                        desc.nFingerwidth,
                                        desc.nFingerheight,
                                        500
                                    )
                                    val bitmap = BitmapFactory.decodeByteArray(bmpData, 0, bmpData.size)
                                    val quality = currentDevice.bmpDataQuality(bmpData)

                                    fingerprints.add(
                                        FingerprintData(
                                            bitmap = bitmap,
                                            quality = quality,
                                            featureData = desc.pFeatureData,
                                            position = getFingerPositionName(desc.nFingerPos)
                                        )
                                    )
                                }

                                onFingerprintsExtracted(fingerprints)
                            }
                        }

                        -5 -> {
                            onStatus("Error: Low quality. Try again.")
                            onFingerprintsExtracted(emptyList())
                        }

                        11 -> {
                            onStatus("Error: Timeout. Please try again.")
                            onFingerprintsExtracted(emptyList())
                        }

                        -2 -> {
                            onStatus("Error: No fingers detected")
                            onFingerprintsExtracted(emptyList())
                        }

                        -8 -> {
                            onStatus("Error: Wrong number of fingers. Expected ${mode.expectedFingers}")
                            onFingerprintsExtracted(emptyList())
                        }
                    }
                }
            })
        } catch (e: Exception) {
            onStatus("Error: ${e.message}")
            onFingerprintsExtracted(emptyList())
        }
    }

    private fun getFingerPositionName(position: Int): String {
        return when (position) {
            1 -> "Right Thumb"
            2 -> "Right Index"
            3 -> "Right Middle"
            4 -> "Right Ring"
            5 -> "Right Little"
            6 -> "Left Thumb"
            7 -> "Left Index"
            8 -> "Left Middle"
            9 -> "Left Ring"
            10 -> "Left Little"
            else -> "Unknown"
        }
    }

    private fun closeDevice() {
        try {
            device?.close()
            device = null
        } catch (e: TrustFingerException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeDevice()
        trustFinger?.release()
    }
}

data class FingerprintData(
    val bitmap: Bitmap,
    val quality: Int,
    val featureData: ByteArray,
    val position: String
)

enum class CaptureMode(
    val displayName: String,
    val inputImageType: Int,
    val expectedFingers: Int
) {
    BOTH_THUMBS("Both Thumbs", 21, 2),
    FOUR_FINGERS_LEFT("4 Fingers Left Hand", 22, 4),
    FOUR_FINGERS_RIGHT("4 Fingers Right Hand", 23, 4),
    SINGLE_FINGER("Single Finger (Right Index)", 2, 1)
}
