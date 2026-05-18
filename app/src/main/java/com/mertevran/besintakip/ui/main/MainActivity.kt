package com.mertevran.besintakip.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mertevran.besintakip.R
import com.mertevran.besintakip.databinding.ActivityMainBinding
import com.mertevran.besintakip.ui.camera.CameraActivity
import com.mertevran.besintakip.ui.details.DetailsActivity
import com.mertevran.besintakip.ui.health.HealthAnalysisActivity
import com.mertevran.besintakip.ui.history.HistoryActivity
import com.mertevran.besintakip.ui.profile.ProfileActivity
import com.mertevran.besintakip.ui.viewmodel.MealViewModel
import com.mertevran.besintakip.worker.NotificationWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MealAdapter
    private val viewModel: MealViewModel by viewModels()
    private var hasShownLimitWarning = false
    private var selectedMeal: com.mertevran.besintakip.data.model.Meal? = null

    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pickContact()
        } else {
            Toast.makeText(this, "SMS göndermek için izin gerekiyor", Toast.LENGTH_SHORT).show()
        }
    }

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

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val phoneNumber = cursor.getString(numberIndex)
                    sendSms(phoneNumber)
                }
            }
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
                    borderCornerColor = android.graphics.Color.parseColor("#00FF00")
                )
            )
        )
    }

    private fun processCroppedImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            // Save cropped image to internal storage to pass to DetailsActivity
            val fileName = "cropped_meal_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            val out = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            val image = InputImage.fromBitmap(bitmap, 0)
            analyzeImage(image, file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.gallery_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        setupObservers()
        scheduleNotification()
    }

    override fun onResume() {
        super.onResume()
        loadTodayData()
    }

    private fun setupRecyclerView() {
        adapter = MealAdapter(
            meals = emptyList(),
            onDeleteClick = { meal ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_meal_title)
                    .setMessage(R.string.delete_meal_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_meal) { _, _ ->
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        viewModel.deleteMeal(meal, calendar.timeInMillis)
                    }
                    .show()
            },
            onItemClick = { meal ->
                val intent = Intent(this, DetailsActivity::class.java).apply {
                    putExtra("EXTRA_MEAL", meal)
                }
                startActivity(intent)
            },
            onShareClick = { meal ->
                selectedMeal = meal
                checkSmsPermission()
            }
        )
        binding.rvTodayMeals.layoutManager = LinearLayoutManager(this)
        binding.rvTodayMeals.adapter = adapter
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            pickContact()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun sendSms(phoneNumber: String) {
        selectedMeal?.let { meal ->
            val message = "Besin Takip: ${meal.name} - ${meal.calories.toInt()} kcal, ${meal.protein.toInt()}g P, ${meal.carbs.toInt()}g K, ${meal.fat.toInt()}g Y."
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
                Toast.makeText(this, "SMS gönderildi: $phoneNumber", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Fallback to Intent if direct SMS fails
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", message)
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "SMS gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.fabCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        binding.fabGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.fabProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.fabHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnFindParks.setOnClickListener {
            startActivity(Intent(this, com.mertevran.besintakip.ui.map.MapActivity::class.java))
        }
        binding.fabHealth.setOnClickListener {
            startActivity(Intent(this, HealthAnalysisActivity::class.java))
        }
        binding.btnAddWater100.setOnClickListener {
            addWater(100)
        }
        binding.btnAddWater250.setOnClickListener {
            addWater(250)
        }
        binding.btnAddWater500.setOnClickListener {
            addWater(500)
        }
        binding.btnRemoveWater.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            viewModel.removeLastWater(calendar.timeInMillis)
        }
    }

    private fun addWater(amount: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        viewModel.addWater(amount, calendar.timeInMillis)
    }

    private fun processGalleryImage(uri: Uri) {
        // This is now replaced by startCrop -> processCroppedImage
    }

    private fun analyzeImage(image: InputImage, imagePath: String) {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f)
            .build()
        val labeler = ImageLabeling.getClient(options)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                val blacklist = listOf("television", "monitor", "furniture", "screen", "building", "electronics")
                val genericTerms = listOf("food", "dish", "cuisine", "meal")
                
                val filtered = labels.filter { label ->
                    val text = label.text.lowercase()
                    !blacklist.any { text.contains(it) } && !genericTerms.contains(text)
                }

                val finalLabel = filtered.firstOrNull()?.text
                if (finalLabel != null) {
                    val intent = Intent(this, DetailsActivity::class.java).apply {
                        putExtra("EXTRA_FOOD_NAME", finalLabel)
                        putExtra("EXTRA_IMAGE_PATH", imagePath)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.detection_failed, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun fixOrientationAndCrop(uri: Uri, bitmap: Bitmap): Bitmap {
        var result = bitmap
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val exif = androidx.exifinterface.media.ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Exif error", e)
        }

        val width = result.width
        val height = result.height
        val newDimension = if (width < height) width else height
        val left = (width - newDimension) / 2
        val top = (height - newDimension) / 2
        
        return Bitmap.createBitmap(result, left, top, newDimension, newDimension)
    }

    private fun analyzeImage(image: InputImage) {
        // This is replaced by analyzeImage(image, imagePath)
    }

    private fun setupObservers() {
        viewModel.todayMeals.observe(this) { meals ->
            adapter.updateMeals(meals)
        }

        viewModel.todayTotalCalories.observe(this) { totalCalories ->
            updateCalorieUI(totalCalories)
        }

        viewModel.todayWater.observe(this) { totalWater ->
            updateWaterUI(totalWater)
        }
    }

    private fun updateWaterUI(totalWater: Int) {
        val prefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        val waterGoal = prefs.getInt("water_goal", 2500)
        
        binding.tvWaterStatus.text = "$totalWater / $waterGoal ml"
        binding.waterProgressBar.max = waterGoal
        binding.waterProgressBar.progress = totalWater
    }

    private fun updateCalorieUI(totalCalories: Double) {
        val prefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        val dailyGoal = prefs.getInt("daily_goal", 2000)
        
        binding.tvCalorieStatus.text = getString(R.string.calorie_status_format, totalCalories.toInt(), dailyGoal)
        binding.progressBar.max = dailyGoal
        binding.progressBar.progress = totalCalories.toInt()

        if (totalCalories > dailyGoal) {
            binding.tvCalorieStatus.setTextColor(android.graphics.Color.RED)
            binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            binding.cardSummary.setCardBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
            
            if (!hasShownLimitWarning) {
                showLimitWarningDialog()
                hasShownLimitWarning = true
            }
        } else {
            binding.tvCalorieStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.onPrimaryContainer))
            binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.primary)
            )
            binding.cardSummary.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.primaryContainer))
            hasShownLimitWarning = false
        }
    }

    private fun showLimitWarningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.limit_exceeded_title)
            .setMessage(R.string.limit_exceeded_message)
            .setPositiveButton(R.string.got_it, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun scheduleNotification() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 20)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()

        val notificationRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationRequest
        )
    }

    private fun loadTodayData() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        viewModel.loadTodayData(startOfDay)
    }
}
