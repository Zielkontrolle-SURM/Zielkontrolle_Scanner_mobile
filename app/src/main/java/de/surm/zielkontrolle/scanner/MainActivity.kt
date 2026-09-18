package de.surm.zielkontrolle.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.FrameLayout
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
    private lateinit var flashOverlay: View
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastScan = ""
    private var ip = "192.168.178.143:8080"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        flashOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            alpha = 0f
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(previewView)
            addView(flashOverlay)
        }
        setContentView(root)

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
                val v = b.rawValue ?: return@forEach
                if (v == lastScan) return@forEach
                if (Regex("^#\\d{4}$").matches(v)) {
                    lastScan = v
                    sendToApi(v)
                    vibrate(this)
                } else if (Regex("^CONFIG=.*$").matches(v)) {
                    lastScan = v
                    ip = v.removePrefix("CONFIG=")
                    // flash screen blue to indicate config change
                    runOnUiThread { flashScreen(Color.argb(120, 99, 129, 255)) }
                    vibrate(this)
                }
            }
        }.addOnCompleteListener { imageProxy.close() }
    }

    private fun flashScreen(color: Int) {
        uiHandler.post {
            flashOverlay.setBackgroundColor(color)
            flashOverlay.visibility = View.VISIBLE
            flashOverlay.alpha = 0f
            flashOverlay.animate()
                .alpha(0.7f)
                .setDuration(1000)
                .withEndAction {
                    flashOverlay.animate()
                        .alpha(0f)
                        .setDuration(220)
                        .withEndAction { flashOverlay.visibility = View.GONE }
                        .start()
                }
                .start()
        }
    }

    private fun sendToApi(value: String) {
        val number = value.removePrefix("#").toIntOrNull() ?: return
        val req = Request.Builder()
            .url("http://$ip/scan/$number")
            .post("".toRequestBody(null))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                android.util.Log.e("MainActivity", "API call failed", e)
                runOnUiThread { flashScreen(Color.argb(120, 255, 99, 99)) }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    android.util.Log.d("MainActivity", "API response: ${response.code}")
                    runOnUiThread { flashScreen(Color.argb(120, 99, 255, 129)) }
                } else {
                    android.util.Log.e("MainActivity", "API call unsuccessful: ${response.code}")
                    runOnUiThread { flashScreen(Color.argb(120, 255, 99, 99)) }
                }
                response.close()
            }
        })
    }

    private fun vibrate(ctx: Context){
        val vibrator : Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(300), intArrayOf(255), -1))
        } else {
            vibrator = ctx.getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(300L)
            }
        }
    }
}
