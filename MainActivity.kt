package com.example.objectcounter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.objectcounter.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var helper: ObjectDetectorHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var workExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var liveMode = true

    /** Last raw result (unfiltered). The confidence slider filters it for display. */
    private var lastResult: DetectionResult? = null
    private var threshold = 0.4f

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) analysePhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        workExecutor = Executors.newSingleThreadExecutor()

        helper = ObjectDetectorHelper(
            context = this,
            onLiveResult = { result ->
                if (liveMode) {
                    lastResult = result
                    runOnUiThread { render() }
                }
            },
            onError = { msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        )

        binding.confidenceLabel.text = getString(R.string.confidence, (threshold * 100).toInt())
        binding.confidenceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                // slider 0..90 -> 10%..100%
                threshold = (progress + 10) / 100f
                binding.confidenceLabel.text =
                    getString(R.string.confidence, (threshold * 100).toInt())
                render()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        binding.confidenceSeek.progress = 30 // 40%

        binding.galleryButton.setOnClickListener {
            pickImage.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        binding.liveButton.setOnClickListener { switchToLive() }

        helper.setupLive()
        if (hasCameraPermission()) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- live camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(cameraExecutor) { image -> analyseFrame(image) } }

        provider.unbindAll()
        provider.bindToLifecycle(
            this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
        )
    }

    private fun analyseFrame(image: ImageProxy) {
        if (!liveMode) {
            image.close()
            return
        }
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        image.use { bitmap.copyPixelsFromBuffer(it.planes[0].buffer) }

        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        helper.detectLive(upright)
    }

    private fun switchToLive() {
        liveMode = true
        binding.stillImage.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.overlay.fitInside = false
        lastResult = null
        render()
        if (hasCameraPermission()) {
            if (cameraProvider == null) startCamera() else bindCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------------------------------------------------------- gallery photo

    private fun analysePhoto(uri: Uri) {
        liveMode = false
        cameraProvider?.unbindAll()
        binding.previewView.visibility = View.GONE
        binding.stillImage.visibility = View.VISIBLE
        binding.overlay.fitInside = true
        binding.overlay.clear()

        workExecutor.execute {
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // limit very large photos for speed/memory
                    val maxSide = 1600
                    val big = maxOf(info.size.width, info.size.height)
                    if (big > maxSide) {
                        val s = maxSide.toFloat() / big
                        decoder.setTargetSize(
                            (info.size.width * s).toInt(), (info.size.height * s).toInt()
                        )
                    }
                }
                val result = helper.detectStill(bitmap)
                runOnUiThread {
                    binding.stillImage.setImageBitmap(bitmap)
                    lastResult = result
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Could not read image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------- UI

    /** Applies the confidence threshold, draws boxes and updates the counts. */
    private fun render() {
        val result = lastResult
        if (result == null) {
            binding.overlay.clear()
            binding.totalText.text = getString(R.string.nothing)
            binding.breakdownText.text = ""
            return
        }
        val shown = result.detections.filter { it.score >= threshold }
        binding.overlay.setDetections(shown, result.imageWidth, result.imageHeight)

        if (shown.isEmpty()) {
            binding.totalText.text = getString(R.string.nothing)
            binding.breakdownText.text = ""
            return
        }
        binding.totalText.text = getString(R.string.total, shown.size)
        binding.breakdownText.text = shown
            .groupingBy { it.label }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString("   ") { "${it.key}: ${it.value}" }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        workExecutor.shutdown()
        helper.close()
    }
}
