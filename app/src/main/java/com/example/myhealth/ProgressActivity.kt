package com.example.myhealth

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myhealth.Model.CurrentResponseApi
import com.example.myhealth.ViewModel.WeatherViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.content.edit
import com.github.mikephil.charting.data.BarData
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class ProgressActivity : AppCompatActivity() {

    // activity objects
    private lateinit var currentTimeTextView: TextView
    private lateinit var currentDateTextView : TextView
    private lateinit var stepsButton : Button
    private lateinit var distanceButton : Button
    private lateinit var caloriesButton : Button
    private lateinit var currentData : TextView
    private lateinit var cityName : TextView
    private lateinit var temp : TextView
    private lateinit var feelsLike : TextView
    private lateinit var pastStepsButton : Button
    private lateinit var pastDistanceButton : Button
    private lateinit var pastCaloriesButton : Button
    private lateinit var headerDbValues: TextView
    // chart
    private lateinit var chartDatabase : BarChart

    // notifications vars
    private lateinit var pendingIntent: PendingIntent

    // variables used within getting location permission
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // view model (getting weather data from external api)
    private val weatherViewModel : WeatherViewModel by viewModels()
    private var lat = 0.0
    private var lon = 0.0

    // sensor and sensor manager
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    // database
    private lateinit var database: DatabaseReference

    // current date val
    private lateinit var currentDate : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_progress)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.progress_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // database initialization
        initializeDbRef()

        // location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        // step counter sensor initiation
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // hour and date formatting
        val formatterHour = DateTimeFormatter.ofPattern("HH:mm")
        val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val currentHour = LocalDateTime.now().format(formatterHour)
        currentDate = LocalDateTime.now().format(formatterDate)
        val currentHourString = currentHour.toString()
        val currentDateString = currentDate.toString()

        // activity objects: ---------------------------------------------
        currentTimeTextView =  findViewById(R.id.textViewTime)
        currentDateTextView = findViewById(R.id.textViewDate)

        currentTimeTextView.text = getString(R.string.current_time, currentHourString)
        currentDateTextView.text = getString(R.string.current_date, currentDateString)

        stepsButton = findViewById(R.id.buttonStepsCurrent)
        distanceButton = findViewById(R.id.buttonDistanceCurrent)
        caloriesButton = findViewById(R.id.buttonCaloriesCurrent)
        cityName = findViewById(R.id.textViewCityChangable)
        currentData = findViewById(R.id.textViewCurrentInfo)
        temp = findViewById(R.id.textViewTempValue)
        feelsLike = findViewById(R.id.textViewFeelsLikeValue)
        pastStepsButton = findViewById(R.id.buttonStepsDb)
        pastDistanceButton = findViewById(R.id.buttonDistanceDb)
        pastCaloriesButton = findViewById(R.id.buttonCaloriesDb)
        chartDatabase = findViewById(R.id.chartDb)
        headerDbValues = findViewById(R.id.textViewHistoricalData)
        // ----------------------------------------------------------------

        // used to auto delete notifications after clicking
        val intent = Intent(this, ProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        stepsButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val stepsRef = db.getReference("steps").child(uid).child(currentDate)

            stepsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val steps = snapshot.getValue(Int::class.java)
                    currentData.text = getString(R.string.steps_today, steps)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "query cancelled", error.toException())
                }
                })
        }

        distanceButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val distanceRef = db.getReference("distance").child(uid).child(currentDate)

            distanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val distance = snapshot.getValue(Int::class.java)
                    currentData.text = getString(R.string.distance_today, distance)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "query cancelled", error.toException())
                }
                })
        }

        caloriesButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val caloriesRef = db.getReference("calories").child(uid).child(currentDate)

            caloriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val calories = snapshot.getValue(Int::class.java)
                    currentData.text = getString(R.string.calories_today, calories)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "query cancelled", error.toException())
                }
                })
        }

        // past plots ------------------------------------------------------------

        pastStepsButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val stepsRef = db.getReference("steps").child(uid)
            loadDataAndShowChart(stepsRef, chartDatabase)
            headerDbValues.text = getString(R.string.past_data_header_after_clicking, "steps")
        }

        pastDistanceButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val distanceRef = db.getReference("distance").child(uid)
            loadDataAndShowChart(distanceRef, chartDatabase)
            headerDbValues.text = getString(R.string.past_data_header_after_clicking, "distance")
        }

        pastCaloriesButton.setOnClickListener {
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val caloriesRef = db.getReference("calories").child(uid)
            loadDataAndShowChart(caloriesRef, chartDatabase)
            headerDbValues.text = getString(R.string.past_data_header_after_clicking, "burned calories")
        }

        // ------------------------------------------------------------------------

        // step sensor permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                1001
            )
        }

        // permission to send notifications
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        // obtaining location and getting data from api
        getLastLocation()
        weatherFun(lat, lon)

        // getting goals from db
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            loadGoals(user.uid) {
            }
        }
    }

    private fun initializeDbRef() {
        database = Firebase.database.reference
    }

    // checking user's permission
    /*private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getLastLocation()
        }
    }*/
    private fun checkLocationPermission() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getLastLocation()
        }
    }

    // getting user's location usage permission
    // calculating city name based on its lat & lon
    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    getCityName(lat, lon)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // if location permission denied, make toast
    /*override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getLastLocation()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }*/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                getLastLocation()
            } else {
                Toast.makeText(this, "Permission denied - some functions may not work properly", Toast.LENGTH_LONG).show()
            }
        }
    }


    // getting city name from its lat & lon
    private fun getCityName(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val city = addresses[0].locality
                if (city != null) {
                    weatherFun(latitude, longitude)
                    cityName.text = city
                } else {
                    Toast.makeText(this, "City name not found", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error getting city name", Toast.LENGTH_SHORT).show()
        }
    }

    // getting data from api and modifying textViews
    private fun weatherFun(lati : Double, longi : Double){
        weatherViewModel.loadCurrentWeather(lati, longi).enqueue(object:
            Callback<CurrentResponseApi> {
            override fun onResponse(
                call: Call<CurrentResponseApi>,
                response: Response<CurrentResponseApi>
            ) {
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null) {
                       //Log.d("MyHealth", "Weather data: $data")

                        val temperature = data.main?.temp?.let {
                            "$it"
                        } ?: "N/A"
                        temp.text = temperature

                        val feels_like = data.main?.feelsLike?.let {
                            "$it"
                        } ?: "N/A"
                        feelsLike.text = feels_like

                    } else {
                        Toast.makeText(this@ProgressActivity, "no data available", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("WeatherApp", "response not successful: ${response.errorBody()?.string()}")
                    Toast.makeText(this@ProgressActivity, "response not successful", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<CurrentResponseApi>, t: Throwable) {
                Toast.makeText(this@ProgressActivity, t.toString(), Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun loadGoals(uid: String, onLoaded: () -> Unit) {
        val goalsRef = FirebaseDatabase.getInstance().getReference("goals").child(uid)
        goalsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                SharedData.stepsGoal = snapshot.child("stepsGoal").getValue(Int::class.java) ?: 0
                SharedData.distanceGoal = snapshot.child("distanceGoal").getValue(Int::class.java) ?: 0
                SharedData.caloriesGoal = snapshot.child("caloriesGoal").getValue(Int::class.java) ?: 0
                onLoaded()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to load goals", error.toException())
                onLoaded()
            }
        })
    }

    // historic data
    fun getLast3Dates(): List<String> {
        val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val dates = mutableListOf<String>()
        var current = LocalDate.now()

        for (i in 0..2) {
            dates.add(current.format(formatterDate))
            current = current.minusDays(1)
        }
        return dates
    }

    fun loadDataAndShowChart(dataRef: DatabaseReference, barChart: BarChart) {
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        val last3Dates = getLast3Dates()

        dataRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<BarEntry>()
                val labels = mutableListOf<String>()

                for ((index, date) in last3Dates.withIndex()) {
                    val data = snapshot.child(date).getValue(Int::class.java) ?: 0
                    entries.add(BarEntry(index.toFloat(), data.toFloat()))
                    labels.add(date)
                }

                val dataSet = BarDataSet(entries, "Data from last 3 days")
                dataSet.color = "#094557".toColorInt()

                val data = BarData(dataSet)
                data.barWidth = 0.9f

                barChart.data = data

                val xAxis = barChart.xAxis
                xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                xAxis.labelCount = labels.size

                if (isDarkTheme) {
                    xAxis.textColor = Color.WHITE
                    barChart.axisLeft.textColor = Color.WHITE
                    barChart.axisRight.textColor = Color.WHITE
                    barChart.legend.textColor = Color.WHITE
                } else {
                    barChart.xAxis.textColor = Color.BLACK
                    barChart.axisLeft.textColor = Color.BLACK
                    barChart.axisRight.textColor = Color.BLACK
                    barChart.legend.textColor = Color.BLACK
                }

                barChart.axisLeft.axisMinimum = 0f
                barChart.axisRight.isEnabled = false

                barChart.description.isEnabled = false
                barChart.setFitBars(true)
                barChart.invalidate()  // refresh
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to load steps", error.toException())
            }
        })
    }
}