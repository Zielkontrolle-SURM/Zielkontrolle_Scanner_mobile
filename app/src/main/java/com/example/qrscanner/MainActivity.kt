package com.example.qrscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private var lastSent = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); previewView = PreviewView(this); setContentView(
            previewView
        )
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) startCamera() else ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            1
        )
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this); providerFuture.addListener({
            val provider = providerFuture.get()
            val preview =
                Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
            val analyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(executor) { img -> process(img) }
            }
            provider.unbindAll(); provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analyzer
        )
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun process(imageProxy: ImageProxy) {
        val media = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient().process(image).addOnSuccessListener { codes ->
            codes.forEach { b ->
                val v = b.rawValue
                    ?: return@forEach; if (Regex("^#\\d{4}$").matches(v) && v != lastSent) {
                lastSent = v; sendToApi(v)
            }
            }
        }.addOnCompleteListener { imageProxy.close() }
    }

    private fun sendToApi(value: String) {
        val number = value.removePrefix("#").toIntOrNull() ?: return
        val req = Request.Builder()
            .url("http://192.168.178.143:8080/scan/$number")
            .post("".toRequestBody(null))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                android.util.Log.e("MainActivity", "API call failed", e)
            }
            override fun onResponse(call: Call, response: Response) {
                android.util.Log.d("MainActivity", $$"API response: ${response.code}")
                response.close()
            }
        })
    }
}
