package com.mertevran.besintakip.data.remote

import com.mertevran.besintakip.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

data class USDAFoodResponse(
    val foods: List<USDAFood>
)

data class USDAFood(
    val description: String,
    val foodNutrients: List<USDANutrient>,
    val brandOwner: String? = null,
    val dataType: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null
)

data class USDANutrient(
    val nutrientId: Int? = null,
    val nutrientName: String,
    val unitName: String,
    val value: Double
)

interface ApiService {
    @GET("v1/foods/search")
    suspend fun searchFood(
        @Query("query") query: String,
        @Query("pageSize") pageSize: Int = 1,
        @Query("api_key") apiKey: String = BuildConfig.USDA_API_KEY
    ): USDAFoodResponse
}
