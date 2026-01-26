package com.securenode.sdk.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.securenode.sdk.security.KeyStoreManager
import java.util.concurrent.Executors

class QrScanActivity : AppCompatActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            findViewById<TextView>(R.id.error).text = "Camera permission required"
        }
    }

    private fun startCamera() {
        val previewView = findViewById<PreviewView>(R.id.preview)
        val errorText = findViewById<TextView>(R.id.error)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val barcodeScanner = BarcodeScanning.getClient()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !saved) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstOrNull()?.rawValue
                            val apiKey = parseApiKey(raw)
                            if (!apiKey.isNullOrBlank() && !saved) {
                                saved = true
                                KeyStoreManager(applicationContext).saveApiKey(apiKey)
                                runOnUiThread { finish() }
                            }
                        }
                        .addOnFailureListener {
                            errorText.text = "Scan failed"
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)
            } catch (e: Exception) {
                errorText.text = "Camera init failed"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseApiKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // Accept raw key
        val trimmed = raw.trim()

        // Accept URL formats: securenode://apikey?key=... or ...?api_key=...
        return try {
            val uri = android.net.Uri.parse(trimmed)
            val q1 = uri.getQueryParameter("api_key")
            val q2 = uri.getQueryParameter("key")
            val q3 = uri.getQueryParameter("apikey")
            (q1 ?: q2 ?: q3 ?: trimmed).trim()
        } catch (_e: Exception) {
            trimmed
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}


