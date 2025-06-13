package com.example.myhealth

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var buttonLogin: Button
    private lateinit var buttonProgress: Button
    private lateinit var buttonGoals: Button
    private lateinit var stepsCounter : TextView
    private lateinit var currentDate : String

    private val sensorManager by lazy {
        getSystemService(SENSOR_SERVICE) as SensorManager
    }
    private val sensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // checking notifications conditions and sending if they are met
        val request = PeriodicWorkRequestBuilder<GoalCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
            "goal-check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // foreground service
        val serviceIntent = Intent(this, DataForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        buttonLogin = findViewById(R.id.buttonLoginScreen)
        buttonProgress = findViewById(R.id.buttonProgress)
        buttonGoals = findViewById(R.id.buttonGoals)
        stepsCounter = findViewById(R.id.textViewCurrentSteps)

        stepsCounter.text = SharedData.currentSteps.toString()

        buttonLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        buttonProgress.setOnClickListener {
            val intent = Intent(this, ProgressActivity::class.java)
            startActivity(intent)
        }

        buttonGoals.setOnClickListener {
            val intent = Intent(this, GoalsActivity::class.java)
            startActivity(intent)
        }

        obtainingStepCounterSensor()

        val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        currentDate = LocalDateTime.now().format(formatterDate)

        lifecycleScope.launch {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val db = FirebaseDatabase.getInstance()
                val uid = user.uid
                val stepsRef = db.getReference("steps").child(uid).child(currentDate)

                val stepsListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val steps = snapshot.getValue(Int::class.java) ?: 0
                        stepsCounter.text = getString(R.string.steps_today, steps)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Firebase", "query cancelled (obtaining steps)", error.toException())
                    }
                }
                stepsRef.addValueEventListener(stepsListener)
            }
        }

    }

    // checking if sensor is available on device
    private fun obtainingStepCounterSensor() {
        if (sensor == null) {
            Toast.makeText(this, "Step counter sensor is not present on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
