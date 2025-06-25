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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // used to refresh UI
    private var stepsJob: Job? = null
    private var distanceJob: Job? = null
    private var caloriesJob: Job? = null
    private var stepsChartJob: Job? = null
    private var distanceChartJob: Job? = null
    private var caloriesChartJob: Job? = null

    /**
     * Called when the ProgressActivity is created
     *
     * This method sets up the UI with edge-to-edge layout support and initializes
     * Firebase database references and UI elements for displaying step, distance,
     * calories data, and weather information
     *
     * Initializes the step counter sensor and location services
     * Requests necessary permissions for activity recognition and notifications
     * Loads current and historical user activity data from Firebase
     *
     * Button click listeners are set up to fetch and display current day data or past data charts
     * for steps, distance, and calories from Firebase Realtime Database
     *
     * Fetches the last known location and updates weather information
     * Loads user goals from Firebase if a user is logged in
     *
     * @param savedInstanceState if the activity is being re-initialized after previously
     * being shut down, this Bundle contains the data it most recently supplied
     */
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

        displayCurrentStepsByDefault()
        displayStepsChartByDefault()

        stepsButton.setOnClickListener {
            distanceJob?.cancel()
            caloriesJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val stepsRef = db.getReference("steps").child(uid).child(currentDate)

            stepsJob = lifecycleScope.launch {
                while (isActive) {
                    stepsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val steps = snapshot.getValue(Int::class.java)
                            currentData.text = getString(R.string.steps_today, steps)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase", "query cancelled", error.toException())
                        }
                    })
                    delay(2000)
                }
            }
        }

        distanceButton.setOnClickListener {
            stepsJob?.cancel()
            caloriesJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val distanceRef = db.getReference("distance").child(uid).child(currentDate)

            distanceJob = lifecycleScope.launch {
                while (isActive) {
                    distanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val distance = snapshot.getValue(Int::class.java)
                            currentData.text = getString(R.string.distance_today, distance)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase", "query cancelled", error.toException())
                        }
                    })
                    delay(2000)
                }
            }
        }

        caloriesButton.setOnClickListener {
            stepsJob?.cancel()
            distanceJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val caloriesRef = db.getReference("calories").child(uid).child(currentDate)

            caloriesJob = lifecycleScope.launch {
                while (isActive) {
                    caloriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val calories = snapshot.getValue(Int::class.java)
                            currentData.text = getString(R.string.calories_today, calories)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("Firebase", "query cancelled", error.toException())
                        }
                    })
                    delay(2000)
                }
            }
        }

        // past plots ------------------------------------------------------------

        pastStepsButton.setOnClickListener {
            distanceChartJob?.cancel()
            caloriesChartJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            stepsChartJob = lifecycleScope.launch {
                while (isActive) {
                    val stepsRef = db.getReference("steps").child(uid)
                    loadDataAndShowChart(stepsRef, chartDatabase)
                    headerDbValues.text = getString(R.string.past_data_header_after_clicking, "steps")
                    delay(2000)
                }
            }
        }

        pastDistanceButton.setOnClickListener {
            stepsChartJob?.cancel()
            caloriesChartJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            distanceChartJob = lifecycleScope.launch {
                while (isActive) {
                    val distanceRef = db.getReference("distance").child(uid)
                    loadDataAndShowChart(distanceRef, chartDatabase)
                    headerDbValues.text = getString(R.string.past_data_header_after_clicking, "distance")
                    delay(2000)
                }
            }
        }

        pastCaloriesButton.setOnClickListener {
            stepsChartJob?.cancel()
            distanceChartJob?.cancel()
            val db = FirebaseDatabase.getInstance()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            caloriesChartJob = lifecycleScope.launch {
                while (isActive) {
                    val caloriesRef = db.getReference("calories").child(uid)
                    loadDataAndShowChart(caloriesRef, chartDatabase)
                    headerDbValues.text = getString(R.string.past_data_header_after_clicking, "burned calories")
                    delay(2000)
                }
            }
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

    /**
     * Initializes realtime database
     *
     * Sets the main database reference as the entry point for database operations
     */
    private fun initializeDbRef() {
        database = Firebase.database.reference
    }

    /**
     * Displays current steps value by default
     *
     * Refreshes UI every 5 seconds
     */
    private fun displayCurrentStepsByDefault() {
        val db = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val stepsRef = db.getReference("steps").child(uid).child(currentDate)

        stepsJob?.cancel()
        stepsJob = lifecycleScope.launch {
            while (isActive) {
                stepsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val steps = snapshot.getValue(Int::class.java)
                        currentData.text = getString(R.string.steps_today, steps)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Firebase", "query cancelled", error.toException())
                    }
                })
                delay(2000)
            }
        }
    }

    /**
     * Displays past steps chart by default
     *
     * Refreshes UI every 5 seconds
     */
    fun displayStepsChartByDefault(){
        val db = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        stepsChartJob?.cancel()
        stepsChartJob = lifecycleScope.launch {
            while (isActive) {
                val stepsRef = db.getReference("steps").child(uid)
                loadDataAndShowChart(stepsRef, chartDatabase)
                headerDbValues.text = getString(R.string.past_data_header_after_clicking, "steps")
                delay(2000)
            }
        }
    }

    /**
     * Checks if the app has the necessary location permissions and requests them if not granted
     *
     * Verifies the permissions for fine location, coarse location and background location
     * If any permissions are missing, requests them from the user
     * If all permissions are granted,
     * proceeds to get the last known location -> triggers [getLastLocation]
     */
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

    /**
     * Retrieves the device's last known location using fusedLocationClient
     *
     * Requires either `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` permission
     * If permissions are missing, the function returns without action
     * On successful location retrieval, updates latitude and longitude variables
     * and triggers a city name lookup bades on these values
     * On failure, shows a toast message indicating the failure
     */
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

    /**
     * Handles the result of permission requests
     *
     * Checks if the location permission request was granted
     * If all requested permissions are granted, proceeds to retrieve the last known location
     * Otherwise shows a toast warning the user that some features may not work properly
     *
     * @param requestCode the integer request code originally supplied to requestPermissions()
     * @param permissions the requested permissions
     * @param grantResults the grant results for the corresponding permissions
     */
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

    /**
     * Retrieves the city name based on the provided latitude and longitude
     *
     * Uses Android's geocoder to convert coordinates into a city name
     * If a city name is found, updates the UI and triggers the weather update function [weatherFun]
     * If the city name is not found or an error occurs, shows a toast message
     *
     * @param latitude the latitude coordinate
     * @param longitude the longitude coordinate
     */
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

    /**
     * Fetches the current weather data for the specified latitude and longitude
     *
     * Uses weatherViewModel to load the current weather asynchronously.
     * On a successful response, updates UI elements with temperature and feels like values
     * If data is missing or the response fails, shows appropriate toast messages
     *
     * @param lati the latitude coordinate
     * @param longi the longitude coordinate
     */
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

    /**
     * Imports user's goals from database
     *
     * Loads goals (steps, distance, calories) for a specific user
     * Fetches the data under the "goals/{uid}" node
     * Calls the provided onLoaded callback once loading is complete, regardless of success or failure
     *
     * @param uid the unique identifier of the user whose goals are being loaded
     * @param onLoaded callback invoked after data loading finishes
     */
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

    /**
    * Returns a list of strings representing the last three dates including today
    *
    * Dates are formatted as "dd-MM-yyyy"
    *
    * @return a list of three date strings: today, yesterday, and the day before yesterday
    */
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

    /**
     * Loads integer data for the last three dates from Firebase Realtime Database and displays it on a BarChart
     *
     * Triggers [getLast3Dates] and retrieves data for each of the last three days (including today)
     * Creates and configures a bar chart with this data
     * Adapts chart colors to the current UI theme (dark or light)
     *
     * @param dataRef reference to the firebase database node containing the data
     * @param barChart the BarChart view to display the loaded data
     */
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
