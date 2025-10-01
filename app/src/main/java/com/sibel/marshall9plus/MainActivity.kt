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
            AppUI()
        }
    }

    private fun checkIfEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk", true)
                || Build.MODEL.contains("Emulator", true)
                || Build.MODEL.contains("Android SDK built for x86", true)
                || Build.MANUFACTURER.contains("Genymotion", true)
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    private fun initializeSDK() {
        if (isRunningOnEmulator) {
            Log.w("TrustFinger", "Emulador detectado - no se inicializa SDK")
            return
        }
        try {
            trustFinger = TrustFinger.getInstance(this)
            trustFinger?.initialize()
            Log.i("TrustFinger", "SDK inicializado")
        } catch (e: TrustFingerException) {
            Log.e("TrustFinger", "Fallo al inicializar SDK", e)
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TrustFinger", "Faltan librerías nativas (usa dispositivo físico)", e)
        }
    }

    // ---------- UI ----------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppUI() {
        var statusMessage by remember {
            mutableStateOf(
                if (isRunningOnEmulator) "⚠️ Emulator detected - Connect a physical Android device"
                else "SDK Ready"
            )
        }
        var deviceCount by remember { mutableStateOf(0) }
        var capturedFingerprints by remember { mutableStateOf<List<FingerprintData>>(emptyList()) }

        var isOpening by remember { mutableStateOf(false) }
        var isProcessing by remember { mutableStateOf(false) }
        var selectedMode by remember { mutableStateOf(CaptureMode.SINGLE_FINGER) } // arranca estable

        LaunchedEffect(Unit) {
            if (!isRunningOnEmulator) deviceCount = getDeviceCount()
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

                // SDK info
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRunningOnEmulator)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("SDK Info", style = MaterialTheme.typography.titleMedium)
                            if (isRunningOnEmulator) {
                                Text(
                                    "⚠️ Running on Emulator",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "TrustFinger SDK requiere dispositivo físico con USB OTG",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("Version: ${trustFinger?.getSdkJarVersion() ?: "N/A"}")
                                Text("Algorithm: ${trustFinger?.getAlgVersion() ?: "N/A"}")
                                Text("Devices Connected: $deviceCount")
                            }
                            Text(
                                text = statusMessage,
                                color = when {
                                    statusMessage.contains("Error") || statusMessage.contains("⚠️") ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }

                if (!isRunningOnEmulator) {

                    // Selector de modo
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
                                Text("Capture Mode", style = MaterialTheme.typography.titleMedium)
                                CaptureMode.values().forEach { mode ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedMode == mode,
                                            onClick = { if (!isOpening && !isProcessing) selectedMode = mode }
                                        )
                                        Text(mode.displayName)
                                    }
                                }
                            }
                        }
                    }

                    // Abrir
                    item {
                        Button(
                            onClick = {
                                isOpening = true
                                statusMessage = "Opening device..."
                                openDevice(
                                    onStatus = { msg -> runOnUiThread { statusMessage = msg } },
                                    onDone = { runOnUiThread { isOpening = false } }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isOpening && !isProcessing
                        ) {
                            if (isOpening) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Opening...")
                            } else {
                                Text("Open Device")
                            }
                        }
                    }

                    // Capturar
                    item {
                        Button(
                            onClick = {
                                isProcessing = true
                                capturedFingerprints = emptyList()
                                statusMessage = "Capturing..."
                                captureMultipleFingerprints(
                                    mode = selectedMode,
                                    onStatus = { msg -> runOnUiThread { statusMessage = msg } },
                                    onFingerprintsExtracted = { fps ->
                                        runOnUiThread {
                                            capturedFingerprints = fps
                                            isProcessing = false
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = device != null && !isOpening && !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Capturing…")
                            } else {
                                Text("Capture ${selectedMode.displayName}")
                            }
                        }
                    }

                    // Cerrar
                    item {
                        Button(
                            onClick = {
                                statusMessage = "Closing device…"
                                closeDevice()
                                statusMessage = "Device Closed"
                                capturedFingerprints = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = device != null && !isOpening && !isProcessing
                        ) { Text("Close Device") }
                    }

                    // Resultado
                    if (capturedFingerprints.isNotEmpty()) {
                        item {
                            Text(
                                text = "Captured: ${capturedFingerprints.size} fingerprint(s)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        itemsIndexed(capturedFingerprints) { index, fp ->
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
                                        "Finger #${index + 1} - ${fp.position}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Image(
                                        bitmap = fp.bitmap.asImageBitmap(),
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
                                                "${fp.quality}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = when {
                                                    fp.quality >= 80 -> Color(0xFF4CAF50)
                                                    fp.quality >= 50 -> Color(0xFFFF9800)
                                                    else -> Color(0xFFF44336)
                                                }
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Feature Size", style = MaterialTheme.typography.labelSmall)
                                            Text("${fp.featureData.size} bytes", style = MaterialTheme.typography.bodyLarge)
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

    private fun getDeviceCount(): Int =
        runCatching { trustFinger?.deviceCount ?: 0 }.getOrDefault(0)

    // ---------- Apertura con estados ----------
    private fun openDevice(
        onStatus: (String) -> Unit,
        onDone: () -> Unit
    ) {
        try {
            onStatus("Opening device…")
            trustFinger?.openDevice(0, object : DeviceOpenListener {
                override fun openSuccess(trustFingerDevice: TrustFingerDevice) {
                    device = trustFingerDevice
                    val info = device?.imageInfo
                    onStatus("Device Opened: ${info?.width}x${info?.height} @ ${info?.resolution}dpi")
                    onDone()
                }
                override fun openFail(errorMessage: String) {
                    onStatus("Error opening: $errorMessage")
                    onDone()
                }
            })
        } catch (e: TrustFingerException) {
            onStatus("Error opening: ${e.message}")
            onDone()
        } catch (t: Throwable) {
            onStatus("Error opening: ${t.message}")
            onDone()
        }
    }

    // ---------- Captura “a prueba de crash” ----------
    private fun captureMultipleFingerprints(
        mode: CaptureMode,
        onStatus: (String) -> Unit,
        onFingerprintsExtracted: (List<FingerprintData>) -> Unit
    ) {
        val currentDevice = device ?: run {
            onStatus("Error: Device not opened")
            return
        }

        try {
            onStatus("Place ${mode.displayName} on sensor…")

            val param = MultiFingerParam(mode.inputImageType, /*timeout*/ 20_000, /*retries*/ 2)

            currentDevice.multiFingerCapture(param, object : MultiFingerCallback {
                override fun multiFingerCallback(
                    occurredEventCode: Int,
                    rawData: ByteArray,
                    segmentImageDesc: Array<SegmentImageDesc>,
                    numberOfSegment: Int
                ) {
                    try {
                        when (occurredEventCode) {
                            1 -> runOnUiThread { onStatus("Processing image…") }

                            9 -> {
                                if (numberOfSegment <= 0) return
                                // decodificar desde el RAW COMPLETO (NO usar pSegmentImagePtr)
                                Thread {
                                    val out = ArrayList<FingerprintData>(numberOfSegment)
                                    try {
                                        val info = currentDevice.imageInfo
                                        val fullBmpBytes = runCatching {
                                            currentDevice.rawToBmp(rawData, info.width, info.height, info.resolution)
                                        }.getOrNull()

                                        val fullBitmap = runCatching {
                                            fullBmpBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                        }.getOrNull()

                                        val qualityWhole = runCatching {
                                            fullBmpBytes?.let { currentDevice.bmpDataQuality(it) } ?: 0
                                        }.getOrElse { 0 }

                                        for (i in 0 until numberOfSegment) {
                                            val d = segmentImageDesc.getOrNull(i) ?: continue
                                            val bmpForUi = fullBitmap ?: continue
                                            out.add(
                                                FingerprintData(
                                                    bitmap = bmpForUi, // mostramos el slap completo
                                                    quality = qualityWhole,
                                                    featureData = d.pFeatureData ?: ByteArray(0),
                                                    position = getFingerPositionName(d.nFingerPos)
                                                )
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        Log.e("TrustFinger", "decode/process error", t)
                                    }
                                    runOnUiThread {
                                        onFingerprintsExtracted(out)
                                        onStatus("Successfully captured ${out.size} fingerprint(s)!")
                                    }
                                }.start()
                            }

                            // errores/estados
                            -5 -> runOnUiThread {
                                onStatus("Error: Low quality. Try again.")
                                onFingerprintsExtracted(emptyList())
                            }
                            11 -> runOnUiThread {
                                onStatus("Error: Timeout. Please try again.")
                                onFingerprintsExtracted(emptyList())
                            }
                            -2 -> runOnUiThread {
                                onStatus("Error: No fingers detected")
                                onFingerprintsExtracted(emptyList())
                            }
                            -8 -> runOnUiThread {
                                onStatus("Error: Wrong number of fingers. Expected ${mode.expectedFingers}")
                                onFingerprintsExtracted(emptyList())
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("TrustFinger", "callback crash", t)
                        runOnUiThread {
                            onStatus("Error (callback): ${t.message}")
                            onFingerprintsExtracted(emptyList())
                        }
                    }
                }
            })
        } catch (e: Throwable) {
            onStatus("Error: ${e.message}")
            onFingerprintsExtracted(emptyList())
            Log.e("TrustFinger", "captureMultipleFingerprints", e)
        }
    }

    private fun getFingerPositionName(position: Int): String = when (position) {
        1 -> "Right Thumb"; 2 -> "Right Index"; 3 -> "Right Middle"; 4 -> "Right Ring"; 5 -> "Right Little";
        6 -> "Left Thumb"; 7 -> "Left Index"; 8 -> "Left Middle"; 9 -> "Left Ring"; 10 -> "Left Little"; else -> "Unknown"
    }

    private fun closeDevice() {
        runCatching { device?.close() }
        device = null
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

enum class CaptureMode(val displayName: String, val inputImageType: Int, val expectedFingers: Int) {
    BOTH_THUMBS("Both Thumbs", 21, 2),
    FOUR_FINGERS_LEFT("4 Fingers Left Hand", 22, 4),
    FOUR_FINGERS_RIGHT("4 Fingers Right Hand", 23, 4),
    SINGLE_FINGER("Single Finger (Right Index)", 2, 1)
}
