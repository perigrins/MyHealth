package com.example.myhealth.ViewModel

import androidx.lifecycle.ViewModel
import com.example.myhealth.Model.CurrentResponseApi
import com.example.myhealth.Repository.WeatherRepository
import com.example.myhealth.Server.ApiClient
import com.example.myhealth.Server.ApiServices
import retrofit2.Call

class WeatherViewModel(val repository:WeatherRepository): ViewModel() {
    constructor():this(WeatherRepository(ApiClient().getClient().create(ApiServices::class.java)))

    fun loadCurrentWeather(lat : Double, lon : Double): Call<CurrentResponseApi> {
        return repository.getCurrentWeather(lat, lon)
    }
}