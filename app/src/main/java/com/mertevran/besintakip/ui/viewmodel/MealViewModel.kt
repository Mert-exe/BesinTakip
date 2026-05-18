package com.mertevran.besintakip.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mertevran.besintakip.R
import com.mertevran.besintakip.data.local.AppDatabase
import com.mertevran.besintakip.data.model.Meal
import com.mertevran.besintakip.data.model.WaterLog
import com.mertevran.besintakip.data.remote.RetrofitClient
import com.mertevran.besintakip.data.remote.USDAFood
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class MealViewModel(application: Application) : AndroidViewModel(application) {

    private val mealDao = AppDatabase.getDatabase(application).mealDao()
    private val waterDao = AppDatabase.getDatabase(application).waterDao()
    private val context = application.applicationContext

    private val _todayMeals = MutableLiveData<List<Meal>>()
    val todayMeals: LiveData<List<Meal>> = _todayMeals

    private val _todayTotalCalories = MutableLiveData<Double>()
    val todayTotalCalories: LiveData<Double> = _todayTotalCalories

    private val _todayWater = MutableLiveData<Int>()
    val todayWater: LiveData<Int> = _todayWater

    private val _allMeals = MutableLiveData<List<Meal>>()
    val allMeals: LiveData<List<Meal>> = _allMeals

    private val _selectedFood = MutableLiveData<USDAFood?>()
    val selectedFood: LiveData<USDAFood?> = _selectedFood

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadTodayData(startOfDay: Long) {
        viewModelScope.launch {
            try {
                _todayMeals.value = mealDao.getTodayMeals(startOfDay)
                _todayTotalCalories.value = mealDao.getTodayTotalCalories(startOfDay) ?: 0.0
                _todayWater.value = waterDao.getTodayWater(startOfDay) ?: 0
            } catch (e: Exception) {
                // Local DB errors are rare but should be handled if necessary
            }
        }
    }

    fun addWater(amount: Int, startOfDay: Long) {
        viewModelScope.launch {
            waterDao.insertWaterLog(WaterLog(amountMl = amount))
            loadTodayData(startOfDay)
        }
    }

    fun removeLastWater(startOfDay: Long) {
        viewModelScope.launch {
            waterDao.deleteLastWaterLog(startOfDay)
            loadTodayData(startOfDay)
        }
    }

    fun loadAllMeals() {
        viewModelScope.launch {
            _allMeals.value = mealDao.getAllMeals()
        }
    }

    fun searchFood(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val searchQuery = query // No manipulation, let Retrofit handle encoding
                
                val response = RetrofitClient.apiService.searchFood(
                    query = searchQuery
                )
                
                if (response.foods.isNotEmpty()) {
                    val food = response.foods.sortedWith(compareBy(
                        { !(it.dataType == "Survey (FNDDS)" || it.dataType == "SR Legacy") },
                        { !it.description.contains(query, ignoreCase = true) },
                        { it.description.length }
                    )).first()
                    _selectedFood.value = food
                } else {
                    _selectedFood.value = null
                    _error.value = context.getString(R.string.usda_no_data)
                }
            } catch (e: UnknownHostException) {
                _error.value = context.getString(R.string.connection_error, context.getString(R.string.main_internet_error))
            } catch (e: Exception) {
                _error.value = context.getString(R.string.connection_error, e.localizedMessage ?: "Bilinmeyen hata")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun insertMeal(meal: Meal) {
        viewModelScope.launch {
            mealDao.insertMeal(meal)
        }
    }

    fun deleteMeal(meal: Meal, startOfDay: Long? = null) {
        viewModelScope.launch {
            // Silme işlemi: Fiziksel dosya silme
            meal.imagePath?.let { path ->
                if (path.isNotEmpty()) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }

            mealDao.deleteMeal(meal)
            // Refresh data after deletion
            if (startOfDay != null) {
                loadTodayData(startOfDay)
            } else {
                loadAllMeals()
            }
        }
    }
}
