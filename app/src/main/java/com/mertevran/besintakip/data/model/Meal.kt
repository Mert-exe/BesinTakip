package com.mertevran.besintakip.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double = 0.0,
    val category: String = "Atıştırmalık",
    val date: Long = System.currentTimeMillis(),
    val imagePath: String? = null
) : Serializable
