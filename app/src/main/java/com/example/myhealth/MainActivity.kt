package com.example.myhealth

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var buttonLogin: Button
    private lateinit var buttonProgress: Button
    private lateinit var buttonGoals: Button
    private lateinit var stepsCounter: TextView
    private lateinit var currentDate: String
    private lateinit var stepsRef: DatabaseReference
    private var stepsListener: ValueEventListener? = null

    private val sensorManager by lazy {
        getSystemService(SENSOR_SERVICE) as SensorManager
    }
    private val sensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    /**
     * Called when the activity is starting
     *
     * Initializes the UI, configures edge-to-edge layout,
     * sets up sensor listeners, and starts necessary background services
     *
     * Handles button click listeners for login/logout, progress screen and goals screen navigation
     *
     * @param savedInstanceState if the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        buttonLogin = findViewById(R.id.buttonLoginScreen)
        buttonProgress = findViewById(R.id.buttonProgress)
        buttonGoals = findViewById(R.id.buttonGoals)
        stepsCounter = findViewById(R.id.textViewCurrentSteps)

        obtainingStepCounterSensor()

        // Set current date
        val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        currentDate = LocalDateTime.now().format(formatterDate)

        // Start background services
        startGoalChecker()
        startStepService()

        // Button actions
        buttonLogin.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                FirebaseAuth.getInstance().signOut()
                refreshUI(null)
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        buttonProgress.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }

        buttonGoals.setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
    }

    /**
     * Function refreshing the UI
     *
     * Checks if user is logged in
     * Triggers [refreshUI]
     */
    override fun onStart() {
        super.onStart()
        val user = FirebaseAuth.getInstance().currentUser
        refreshUI(user)
    }

    /**
     * Changes the UI
     *
     * Refreshes the UI every 5 seconds
     * Displays different layout if the user is logged in or not
     * Helps with smooth updating stepsCounter value
     *
     * @param user instance of [FirebaseAuth] user, can be null
     */
    private fun refreshUI(user: com.google.firebase.auth.FirebaseUser?) {
        if (user != null) {
            buttonProgress.visibility = View.VISIBLE
            buttonGoals.visibility = View.VISIBLE
            buttonLogin.setText(R.string.button_log_out)

            val db = FirebaseDatabase.getInstance()
            val uid = user.uid
            stepsRef = db.getReference("steps").child(uid).child(currentDate)

            stepsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val steps = snapshot.getValue(Int::class.java) ?: 0
                    stepsCounter.text = getString(R.string.steps_today, steps)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "query cancelled (obtaining steps)", error.toException())
                }
            }
            stepsRef.addValueEventListener(stepsListener!!)

            lifecycleScope.launch {
                while (true) {
                    delay(5000)
                }
            }
        } else {
            stepsCounter.setText(R.string.current_steps_hint)
            buttonLogin.setText(R.string.login_or_register)
            buttonProgress.visibility = View.INVISIBLE
            buttonGoals.visibility = View.INVISIBLE
        }
    }

    /**
     * Starts periodic checking of user's goals
     *
     * Creates task GoalCheckWorker
     * Checks if the user's goals were achieved every 15 minutes
     */
    private fun startGoalChecker() {
        val request = PeriodicWorkRequestBuilder<GoalCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "goal-check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Starts foreground service DataForegroundService
     *
     * Runs in the background
     * Handles sending data to realtime database (firebase)
     */
    private fun startStepService() {
        val serviceIntent = Intent(this, DataForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    /**
     * Checks the presence of stepCounterSensor on current device
     *
     * If stepCounterSensor is not present -> displays a suitable toast
     * If stepCounterSensor is present -> nothing happens
     */
    private fun obtainingStepCounterSensor() {
        if (sensor == null) {
            Toast.makeText(this, "Step counter sensor is not present on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
