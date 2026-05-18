package com.mertevran.besintakip.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mertevran.besintakip.data.model.WaterLog

@Dao
interface WaterDao {
    @Query("SELECT SUM(amountMl) FROM water_logs WHERE date >= :startOfDay")
    suspend fun getTodayWater(startOfDay: Long): Int?

    @Insert
    suspend fun insertWaterLog(waterLog: WaterLog)

    @Query("DELETE FROM water_logs WHERE id = (SELECT id FROM water_logs WHERE date >= :startOfDay ORDER BY id DESC LIMIT 1)")
    suspend fun deleteLastWaterLog(startOfDay: Long)
}
