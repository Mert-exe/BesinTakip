package com.mertevran.besintakip.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mertevran.besintakip.BuildConfig
import com.mertevran.besintakip.R
import com.mertevran.besintakip.databinding.ActivityCameraBinding
import com.mertevran.besintakip.ui.details.DetailsActivity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var lastDetectedLabel: String? = null
    private var lastBitmap: Bitmap? = null
    
    // AI Voting Buffer
    private val detectionBuffer = mutableListOf<String>()
    private val BUFFER_SIZE = 5

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { startCrop(it) }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                processCroppedImage(uriContent)
            }
        } else {
            val exception = result.error
            exception?.printStackTrace()
        }
    }

    private fun startCrop(uri: Uri) {
        cropImage.launch(
            CropImageContractOptions(
                uri = uri,
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    fixAspectRatio = true,
                    backgroundColor = android.graphics.Color.parseColor("#99000000"),
                    activityBackgroundColor = android.graphics.Color.BLACK,
                    activityTitle = getString(R.string.share),
                    allowRotation = true,
                    allowFlipping = true,
                    cropMenuCropButtonTitle = getString(R.string.search),
                    borderCornerColor = android.graphics.Color.parseColor("#00FF00"),
                    activityMenuIconColor = android.graphics.Color.WHITE
                )
            )
        )
    }

    private fun processCroppedImage(uri: Uri) {
        binding.loadingOverlay.visibility = View.VISIBLE
        cameraExecutor.execute {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                lastBitmap = bitmap
                
                val image = InputImage.fromBitmap(bitmap, 0)
                
                analyzeImage(image) {
                    runOnUiThread {
                        binding.loadingOverlay.visibility = View.GONE
                        lastDetectedLabel?.let { navigateToDetails(it) } ?: run {
                            Toast.makeText(this@CameraActivity, "Besin algılanamadı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@CameraActivity, "Görsel işlenemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationIconTint(android.graphics.Color.WHITE)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener { 
            lastDetectedLabel?.let { label ->
                binding.viewFinder.bitmap?.let { lastBitmap = it }
                navigateToDetails(label)
            } ?: run {
                Toast.makeText(this, R.string.detection_needed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                
                // Continuous AF
                val cameraControl = camera.cameraControl
                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val point = factory.createPoint(0.5f, 0.5f)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                cameraControl.startFocusAndMetering(action)

            } catch (exc: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val width = imageProxy.width
            val height = imageProxy.height

            // ROI: 60% of the center image
            val cropWidth = (width * 0.6).toInt()
            val cropHeight = (height * 0.6).toInt()
            val left = (width - cropWidth) / 2
            val top = (height - cropHeight) / 2

            imageProxy.setCropRect(Rect(left, top, left + cropWidth, top + cropHeight))

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            analyzeImage(image) {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeImage(image: InputImage, onComplete: () -> Unit) {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
        val labeler = ImageLabeling.getClient(options)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                val priorityFoods = listOf("hamburger", "pizza", "salad", "fries", "sandwich", "soup", "pasta", "steak")
                val genericTerms = listOf("food", "fast food", "dish", "cuisine", "meal", "plate", "tableware")
                val noise = listOf("table", "hand", "person", "flower", "plant", "petal", "leaf", "botany", "furniture", "room", "design", "art", 
                                   "television", "monitor", "screen", "building", "electronics", "gadget", "office", "mobile phone")
                
                val topLabels = labels.take(5)
                
                // 1. Priority check (Specific world foods)
                val priorityMatch = topLabels.find { label ->
                    priorityFoods.any { food -> label.text.lowercase().contains(food) }
                }
                
                var currentLabel = ""
                if (priorityMatch != null) {
                    currentLabel = priorityMatch.text
                } else {
                    // 2. Filter meaningful labels (Exclude noise and generic terms)
                    val meaningful = topLabels.filter { label ->
                        val text = label.text.lowercase()
                        !noise.any { text.contains(it) } && !genericTerms.contains(text)
                    }
                    if (meaningful.isNotEmpty()) {
                        currentLabel = meaningful.first().text
                    }
                }

                if (currentLabel.isNotEmpty()) {
                    updateBufferAndLabel(currentLabel)
                }
            }
            .addOnCompleteListener {
                onComplete()
            }
    }

    private fun updateBufferAndLabel(label: String) {
        detectionBuffer.add(label)
        if (detectionBuffer.size > BUFFER_SIZE) {
            detectionBuffer.removeAt(0)
        }

        val mostFrequent = detectionBuffer.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        
        mostFrequent?.let {
            lastDetectedLabel = it
            runOnUiThread {
                binding.tvPrediction.text = getString(R.string.detected_food, it)
            }
        }
    }

    private fun navigateToDetails(label: String) {
        val imagePath = lastBitmap?.let { saveBitmapToInternalStorage(it) }
        val intent = Intent(this, DetailsActivity::class.java).apply {
            putExtra("EXTRA_FOOD_NAME", label)
            putExtra("EXTRA_IMAGE_PATH", imagePath)
        }
        startActivity(intent)
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap): String? {
        return try {
            val filename = "${UUID.randomUUID()}.jpg"
            val file = File(filesDir, filename)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
