package com.example.myhealth

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataForegroundService : Service(), SensorEventListener {

    private val CHANNEL_ID = "foreground_service_channel"

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private val prefs by lazy { getSharedPreferences("step_prefs", MODE_PRIVATE) }
    private var stepsAtStart: Int
        get() = prefs.getInt("stepsAtStart", -1)
        set(value) = prefs.edit { putInt("stepsAtStart", value) }

    private var lastSavedSteps: Int
        get() = prefs.getInt("lastSavedSteps", 0)
        set(value) = prefs.edit { putInt("lastSavedSteps", value) }

    private val stepLength = 0.78 // in meters
    private val caloriesPerStep = 0.04 // in kcal

    private val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val currentDate: String
        get() = LocalDateTime.now().format(formatterDate)

    // gps
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0

    /**
     * Initializes the activity
     *
     * Sets up the step counter sensor and registers this class as its listener
     * Initializes the fused location provider client and starts location updates
     */
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // gps initialization
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }

    /**
     * Starts location updates using the fused location provider
     *
     * Requests high accuracy location updates every 5 seconds (with a fastest interval of 3 seconds)
     * On receiving a new location, calculates the speed based on distance from the last location and time elapsed
     * Updates the current speed, last location, and last location timestamp accordingly
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val newLocation = locationResult.lastLocation ?: return
                val now = System.currentTimeMillis()

                if (lastLocation != null) {
                    val distance = lastLocation!!.distanceTo(newLocation) // meters
                    val timeDelta = (now - lastLocationTime) / 1000.0 // seconds
                    val speed = distance / timeDelta // m/s

                    currentSpeed = speed
                }

                lastLocation = newLocation
                lastLocationTime = now
            }
        }, mainLooper)
    }

    private var currentSpeed = 0.0 // m/s

    /**
     * Starts the service in the foreground with a persistent notification
     *
     * For Android 14+, uses the startForeground method
     * with the `FOREGROUND_SERVICE_TYPE_HEALTH` flag to specify the service type
     *
     * @param intent the Intent supplied to startService, if any
     * @param flags additional data about the start request
     * @param startId a unique integer representing this specific request to start
     * @return the startup mode for the service; here it returns [START_STICKY] to restart if killed
     */
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // android 14
            startForeground(
                1,
                notification,
                FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_HEALTH)
        }

        return START_STICKY
    }

    /**
     * Cleans up resources when the service/activity is destroyed
     *
     * Unregisters the sensor listener to prevent memory leaks
     */
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    /**
     * Returns null because this service is not designed for binding
     *
     * @param intent the Intent that was used to bind to this service
     * @return always null since binding is not allowed
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the sensor detects a change (step counter sensor)
     *
     * Calculates the number of new steps since the last update
     * Filters out unrealistic step counts if device rebooted (step count reset)
     * Checks if the user is likely walking based on the current speed
     *
     * If new steps are detected and the user is walking (speed < 4 m/s)
     * it calculates the distance and calories burned, then updates this data in Firebase
     *
     * @param event the sensor event containing the updated step count
     */
    override fun onSensorChanged(event: SensorEvent?) {
        val totalSteps = event?.values?.get(0)?.toInt() ?: return

        if (stepsAtStart == -1) {
            stepsAtStart = totalSteps
        }

        if (totalSteps < stepsAtStart) {
            stepsAtStart = totalSteps
            lastSavedSteps = 0
        }

        val currentSteps = totalSteps - stepsAtStart
        val newSteps = currentSteps - lastSavedSteps

        // 15 km/h = 4.16 m/s
        val isLikelyWalking = currentSpeed < 4.0

        if (newSteps > 0 && isLikelyWalking) {
            lastSavedSteps = currentSteps

            val distance = newSteps * stepLength
            val calories = newSteps * caloriesPerStep

            updateDataInFirebase(currentDate, newSteps, distance, calories)
        }

        /*if (newSteps > 0) {
            lastSavedSteps = currentSteps

            val distance = newSteps * stepLength
            val calories = newSteps * caloriesPerStep

            updateDataInFirebase(currentDate, newSteps, distance, calories)
        }*/
    }

    /**
     * Called when the accuracy of the registered sensor has changed
     *
     * @param sensor the sensor whose accuracy changed
     * @param accuracy the new accuracy of this sensor
     * */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Updates the user's daily step count, distance, and calories data in Firebase Realtime Database
     * Uses transactions to safely increment the existing values by the provided deltas
     *
     * @param date the date string (formatted) identifying the data entry to update
     * @param stepsDelta the number of steps to add to the current step count
     * @param distanceDelta the distance to add to the current distance
     * @param caloriesDelta the calories to add to the current calories count
     */
    private fun updateDataInFirebase(date: String, stepsDelta: Int, distanceDelta: Double, caloriesDelta: Double) {
        val database = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val stepsRef = database.getReference("steps").child(userId).child(date)
        val distanceRef = database.getReference("distance").child(userId).child(date)
        val caloriesRef = database.getReference("calories").child(userId).child(date)

        stepsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Int::class.java) ?: 0
                currentData.value = current + stepsDelta
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })

        distanceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Double::class.java) ?: 0.0
                currentData.value = current + distanceDelta
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })

        caloriesRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val current = currentData.getValue(Double::class.java) ?: 0.0
                currentData.value = current + caloriesDelta
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    /**
     * Creates a foreground service notification to indicate that activity tracking is running
     * Sets up a notification channel
     *
     * @param context the context used to create the notification and access system services
     * @return the constructed Notification instance for use in foreground service
     */
    private fun createNotification(context: Context): Notification {
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Step Tracker Channel"
            val descriptionText = "Foreground Service Channel"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking activity")
            .setContentText("Service is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
