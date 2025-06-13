package com.example.myhealth.Server

import com.example.myhealth.Model.CurrentResponseApi
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiServices {

    @GET("data/2.5/weather")
    fun getCurrentWeather(
       @Query("lat") lat : Double,
       @Query("lon") lon : Double,
       @Query("appid") ApiKey : String,
       @Query("units") units : String = "metric",
    ): Call<CurrentResponseApi>
}