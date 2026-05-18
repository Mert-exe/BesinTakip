package com.mertevran.besintakip.ui.history

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.mertevran.besintakip.R
import com.mertevran.besintakip.data.model.Meal
import com.mertevran.besintakip.databinding.ActivityHistoryBinding
import com.mertevran.besintakip.ui.main.MealAdapter
import com.mertevran.besintakip.ui.viewmodel.MealViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: MealAdapter
    private val viewModel: MealViewModel by viewModels()
    private var selectedMeal: com.mertevran.besintakip.data.model.Meal? = null

    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pickContact()
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
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupObservers()
        viewModel.loadAllMeals()
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
                        viewModel.deleteMeal(meal)
                    }
                    .show()
            },
            onItemClick = { meal ->
                val intent = Intent(this, com.mertevran.besintakip.ui.details.DetailsActivity::class.java).apply {
                    putExtra("EXTRA_MEAL", meal)
                }
                startActivity(intent)
            },
            onShareClick = { meal ->
                selectedMeal = meal
                checkSmsPermission()
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
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

    private fun setupObservers() {
        viewModel.allMeals.observe(this) { meals ->
            if (meals.isEmpty()) {
                binding.llEmptyState.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
                binding.cardChart.visibility = View.GONE
            } else {
                binding.llEmptyState.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.cardChart.visibility = View.VISIBLE
                adapter.updateMeals(meals)
                setupChart(meals)
            }
        }
    }

    private fun setupChart(meals: List<Meal>) {
        val last7DaysMeals = meals.filter { 
            it.date > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        }

        if (last7DaysMeals.isEmpty()) {
            binding.barChart.setNoDataText("Henüz yeterli veri yok")
            binding.barChart.invalidate()
            return
        }

        val entries = ArrayList<BarEntry>()
        val dateLabels = ArrayList<String>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Gruplayarak son 7 günün her birini oluştur
        for (i in 6 downTo 0) {
            val dayStart = calendar.timeInMillis - (i * 24 * 60 * 60 * 1000L)
            val dayEnd = dayStart + (24 * 60 * 60 * 1000L)
            
            val dailyTotal = last7DaysMeals.filter { it.date in dayStart until dayEnd }
                .sumOf { it.calories }
            
            entries.add(BarEntry((6 - i).toFloat(), dailyTotal.toFloat()))
            dateLabels.add(sdf.format(Date(dayStart)))
        }

        val dataSet = BarDataSet(entries, "Kalori")
        dataSet.color = ContextCompat.getColor(this, R.color.primary)
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        binding.barChart.data = barData

        // Grafik Ayarları
        binding.barChart.description.isEnabled = false
        binding.barChart.setFitBars(true)
        binding.barChart.animateY(1000)
        
        val xAxis = binding.barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value.toInt() in dateLabels.indices) dateLabels[value.toInt()] else ""
            }
        }

        binding.barChart.axisLeft.setDrawGridLines(false)
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.legend.isEnabled = false
        
        binding.barChart.invalidate()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
