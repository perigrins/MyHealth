package com.example.myhealth.Repository

import com.example.myhealth.Server.ApiServices

class WeatherRepository (val api:ApiServices) {
    fun getCurrentWeather (lat:Double, lon:Double)=
        //api.getCurrentWeather(lat, lon, "a36c4942dc1564603b3dfbe5ccd2205d")
        api.getCurrentWeather(lat, lon, "a36c4942dc1564603b3dfbe5ccd2205d")
}