package com.mertevran.besintakip.ui.details

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.mertevran.besintakip.R
import com.mertevran.besintakip.data.model.Meal
import com.mertevran.besintakip.data.remote.USDAFood
import com.mertevran.besintakip.databinding.ActivityDetailsBinding
import com.mertevran.besintakip.ui.viewmodel.MealViewModel
import java.io.File

class DetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailsBinding
    private val viewModel: MealViewModel by viewModels()
    private var currentMeal: Meal? = null
    private var capturedImagePath: String? = null
    private var isViewMode = false
    
    // Values for 100g/ml
    private var baseCalories: Double = 0.0
    private var baseProtein: Double = 0.0
    private var baseCarbs: Double = 0.0
    private var baseFat: Double = 0.0
    private var baseUnit = "g"
    private var isOzUnit = false
    private var currentDescription = ""

    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } else {
            Toast.makeText(this, "SMS göndermek için izin gerekiyor", Toast.LENGTH_SHORT).show()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupObservers()
        setupTextWatcher()

        // Check if we are viewing a past meal
        val pastMeal = intent.getSerializableExtra("EXTRA_MEAL") as? Meal
        if (pastMeal != null) {
            setupViewMode(pastMeal)
        } else {
            setupScanMode()
        }

        binding.btnAddMeal.setOnClickListener {
            saveMeal()
        }

        binding.btnEditFood.setOnClickListener {
            showEditDialog()
        }

        binding.btnShareSms.setOnClickListener {
            if (currentMeal != null) {
                checkSmsPermission()
            } else {
                Toast.makeText(this, "Paylaşılacak besin verisi yok", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun setupViewMode(meal: Meal) {
        isViewMode = true
        currentMeal = meal
        
        binding.tvFoodName.text = meal.name
        binding.tvCalories.text = meal.calories.toInt().toString()
        binding.tvProtein.text = "${meal.protein.toInt()}g"
        binding.tvCarbs.text = "${meal.carbs.toInt()}g"
        // Fat was not stored in Meal entity, showing 0 or calculating if needed, 
        // but for now we follow the stored entity values.
        
        binding.btnAddMeal.visibility = View.GONE
        binding.btnEditFood.visibility = View.GONE
        binding.cardPortion.visibility = View.GONE
        
        meal.imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                binding.ivFoodImage.setImageBitmap(bitmap)
                binding.cardFoodImage.visibility = View.VISIBLE
            }
        }
        
        setViewsVisibility(View.VISIBLE)
    }

    private fun setupScanMode() {
        val foodName = intent.getStringExtra("EXTRA_FOOD_NAME") ?: ""
        capturedImagePath = intent.getStringExtra("EXTRA_IMAGE_PATH")
        
        capturedImagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                binding.ivFoodImage.setImageBitmap(bitmap)
                binding.cardFoodImage.visibility = View.VISIBLE
            }
        }

        if (foodName.trim().isNotEmpty()) {
            val safeFoodName = foodName.trim()
            viewModel.searchFood(safeFoodName)
        } else {
            binding.tvFoodName.text = getString(R.string.invalid_food)
            Toast.makeText(this, R.string.details_error_scan_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTextWatcher() {
        binding.etPortionAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty()) {
                    try {
                        val portion = input.toDouble()
                        if (portion > 0) {
                            calculateAndSetCalories(portion)
                        } else {
                            // 0 veya negatif girilirse değerleri sıfırla veya uyarı ver
                            calculateAndSetCalories(0.0)
                        }
                    } catch (e: NumberFormatException) {
                        // Geçersiz format girildiğinde (örn: sadece nokta) hata verme, sessizce geç
                    }
                } else {
                    calculateAndSetCalories(0.0)
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.selectedFood.observe(this) { food ->
            if (food != null) {
                updateBaseValues(food)
                setViewsVisibility(View.VISIBLE)
                
                // Birim kontrolü: oz/ounce ise grama çevrim için bayrak set et
                val unit = food.servingSizeUnit?.lowercase() ?: "g"
                isOzUnit = unit == "oz" || unit == "ounce"
                baseUnit = if (isOzUnit) "g" else unit
                
                // Set initial portion if available from API
                var initialPortion = food.servingSize ?: 100.0
                
                // Eğer birim oz ise, başlangıç değerini de grama çevirerek gösterelim
                if (isOzUnit) {
                    initialPortion *= 28.35
                }
                
                binding.etPortionAmount.setText(String.format(java.util.Locale.US, "%.1f", initialPortion))
                
                // Show warning if API didn't provide servingSize
                binding.tvPortionWarning.visibility = if (food.servingSize == null) View.VISIBLE else View.GONE
                
                binding.tvPortionLabel.text = getString(R.string.portion_amount, baseUnit)
                currentDescription = food.description
                
                calculateAndSetCalories(initialPortion)
            } else {
                setViewsVisibility(View.GONE)
            }
        }

        viewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnAddMeal.isEnabled = !isLoading
            binding.btnEditFood.isEnabled = !isLoading
            
            if (isLoading) {
                setViewsVisibility(View.INVISIBLE)
            }
        }
    }

    private fun setViewsVisibility(visibility: Int) {
        binding.cardCalories.visibility = visibility
        binding.cardProtein.visibility = visibility
        binding.cardCarbs.visibility = visibility
        binding.cardFat.visibility = visibility
        binding.cardPortion.visibility = visibility
    }

    private fun updateBaseValues(food: USDAFood) {
        binding.tvFoodName.text = food.description.replaceFirstChar { it.uppercase() }
        
        baseCalories = 0.0
        baseProtein = 0.0
        baseCarbs = 0.0
        baseFat = 0.0

        food.foodNutrients.forEach { nutrient ->
            val id = nutrient.nutrientId
            val name = nutrient.nutrientName
            val unit = nutrient.unitName
            val value = nutrient.value
            
            // Energy check (kcal)
            if ((id == 1008 || name.contains("Energy", ignoreCase = true)) && unit.equals("kcal", ignoreCase = true)) {
                baseCalories = value
            } 
            // Protein check
            else if (id == 1003 || name.equals("Protein", ignoreCase = true)) {
                baseProtein = value
            } 
            // Carbohydrate check
            else if (id == 1005 || name.contains("Carbohydrate", ignoreCase = true)) {
                baseCarbs = value
            } 
            // Fat (Total lipid) check - nutrientId 1004
            else if (id == 1004 || name.contains("Total lipid", ignoreCase = true) || name.equals("Fat", ignoreCase = true)) {
                baseFat = value
            }
        }
    }

    private fun calculateAndSetCalories(portion: Double) {
        // Hesaplama her zaman gram/ml üzerinden yapılıyor (base değerler 100g/ml için)
        val multiplier = portion / 100.0
        
        val totalCalories = baseCalories * multiplier
        val totalProtein = baseProtein * multiplier
        val totalCarbs = baseCarbs * multiplier
        val totalFat = baseFat * multiplier

        binding.tvCalories.text = totalCalories.toInt().toString()
        binding.tvProtein.text = "${totalProtein.toInt()}g"
        binding.tvCarbs.text = "${totalCarbs.toInt()}g"
        binding.tvFat.text = "${totalFat.toInt()}g"

        val selectedChipId = binding.cgCategory.checkedChipId
        val category = if (selectedChipId != View.NO_ID) {
            findViewById<Chip>(selectedChipId).text.toString()
        } else {
            "Atıştırmalık"
        }

        currentMeal = Meal(
            name = "$currentDescription (${portion.toInt()}$baseUnit)",
            calories = totalCalories,
            protein = totalProtein,
            carbs = totalCarbs,
            fat = totalFat,
            category = category,
            imagePath = capturedImagePath
        )
    }

    private fun sendSms(phoneNumber: String) {
        currentMeal?.let { meal ->
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

    private fun showEditDialog() {
        val editText = EditText(this)
        editText.setText(binding.tvFoodName.text)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_food_name)
            .setMessage(R.string.usda_search_message)
            .setView(editText)
            .setPositiveButton(R.string.search) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.searchFood(newName)
                } else {
                    Toast.makeText(this, R.string.details_error_invalid_name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveMeal() {
        val portionText = binding.etPortionAmount.text.toString().trim()
        
        if (portionText.isEmpty()) {
            binding.etPortionAmount.error = "Lütfen bir miktar girin"
            return
        }

        try {
            val portion = portionText.toDouble()
            if (portion <= 0) {
                binding.etPortionAmount.error = "Lütfen 0'dan büyük bir miktar girin"
                return
            }
            
            val selectedChipId = binding.cgCategory.checkedChipId
            val category = if (selectedChipId != View.NO_ID) {
                findViewById<Chip>(selectedChipId).text.toString()
            } else {
                "Atıştırmalık"
            }
            
            currentMeal = currentMeal?.copy(category = category)
            
            currentMeal?.let { meal ->
                viewModel.insertMeal(meal)
                Toast.makeText(this@DetailsActivity, R.string.meal_saved, Toast.LENGTH_SHORT).show()
                finish()
            } ?: Toast.makeText(this, R.string.no_data_to_save, Toast.LENGTH_SHORT).show()
            
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Lütfen geçerli bir sayı girin", Toast.LENGTH_SHORT).show()
        }
    }
}
