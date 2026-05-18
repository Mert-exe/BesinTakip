package com.mertevran.besintakip.ui.health

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mertevran.besintakip.databinding.ActivityHealthAnalysisBinding
import java.util.*

class HealthAnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthAnalysisBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Sağlık Analizi"

        binding.btnCalculate.setOnClickListener {
            calculateAndSave()
        }
    }

    private fun calculateAndSave() {
        val heightStr = binding.etHeight.text.toString()
        val weightStr = binding.etWeight.text.toString()
        val ageStr = binding.etAge.text.toString()

        if (heightStr.isEmpty() || weightStr.isEmpty() || ageStr.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
            return
        }

        val height = heightStr.toDouble()
        val weight = weightStr.toDouble()
        val age = ageStr.toInt()
        val isMale = binding.rbMale.isChecked
        val wantsToLoseWeight = binding.switchWeightLoss.isChecked

        // BMI Calculation
        val bmi = weight / ((height / 100) * (height / 100))
        val bmiStatus = when {
            bmi < 18.5 -> "Zayıf"
            bmi < 25 -> "Normal"
            bmi < 30 -> "Fazla Kilolu"
            else -> "Obez"
        }

        // BMR Calculation (Harris-Benedict)
        val bmr = if (isMale) {
            88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
        } else {
            447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
        }

        var targetCalories = bmr.toInt()
        if (wantsToLoseWeight) {
            targetCalories -= 500
        }

        // Ensure a minimum calorie intake
        if (targetCalories < 1200) targetCalories = 1200

        // Show Results
        binding.cardResults.visibility = View.VISIBLE
        binding.tvBmiResult.text = String.format(Locale.getDefault(), "BMI: %.1f - %s", bmi, bmiStatus)
        binding.tvBmrResult.text = "BMR: ${bmr.toInt()} kcal/gün"
        binding.tvTargetResult.text = "Yeni Hedefiniz: $targetCalories kcal"

        // Save to SharedPreferences
        val prefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("daily_goal", targetCalories)
            // Optionally save other data
            putInt("height", height.toInt())
            putInt("weight", weight.toInt())
            putInt("age", age)
            putBoolean("is_male", isMale)
            apply()
        }

        Toast.makeText(this, "Yeni hedefleriniz uygulandı!", Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
