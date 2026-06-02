package com.mertevran.besintakip.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mertevran.besintakip.R
import com.mertevran.besintakip.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationIconTint(androidx.core.content.ContextCompat.getColor(this, R.color.primary))

        loadProfileData()

        binding.btnSaveProfile.setOnClickListener {
            saveProfileData()
        }

        binding.btnScheduleAppointment.setOnClickListener {
            scheduleAppointment()
        }
    }

    private fun loadProfileData() {
        val prefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        binding.etHeight.setText(prefs.getInt("height", 0).toString())
        binding.etWeight.setText(prefs.getInt("weight", 0).toString())
        binding.etAge.setText(prefs.getInt("age", 0).toString())
        binding.etGoal.setText(prefs.getInt("daily_goal", 2000).toString())
        binding.etWaterGoal.setText(prefs.getInt("water_goal", 2500).toString())
    }

    private fun saveProfileData() {
        val height = binding.etHeight.text.toString().toIntOrNull() ?: 0
        val weight = binding.etWeight.text.toString().toIntOrNull() ?: 0
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0
        val goal = binding.etGoal.text.toString().toIntOrNull() ?: 2000
        val waterGoal = binding.etWaterGoal.text.toString().toIntOrNull() ?: 2500

        val prefs = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("height", height)
            putInt("weight", weight)
            putInt("age", age)
            putInt("daily_goal", goal)
            putInt("water_goal", waterGoal)
            apply()
        }

        Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun scheduleAppointment() {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, getString(R.string.appointment_title))
            putExtra(CalendarContract.Events.DESCRIPTION, getString(R.string.appointment_description))
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 60 * 60 * 1000) // 1 saat
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // resolveActivity failed (common on Android 11+ without queries, but I added it)
            // or no calendar app. Trying without resolveActivity if it fails or just notifying.
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Takvim uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
