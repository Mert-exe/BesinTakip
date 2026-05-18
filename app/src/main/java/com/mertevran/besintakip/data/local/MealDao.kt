package com.mertevran.besintakip.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.mertevran.besintakip.data.model.Meal

@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY date DESC")
    suspend fun getAllMeals(): List<Meal>

    @Insert
    suspend fun insertMeal(meal: Meal)

    @Delete
    suspend fun deleteMeal(meal: Meal)

    @Query("SELECT * FROM meals WHERE date >= :startOfDay ORDER BY date DESC")
    suspend fun getTodayMeals(startOfDay: Long): List<Meal>

    @Query("SELECT SUM(calories) FROM meals WHERE date >= :startOfDay")
    suspend fun getTodayTotalCalories(startOfDay: Long): Double?
}
