package com.example.myhealth

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

class GoalsActivity : AppCompatActivity(){

    private lateinit var editGoalSteps : EditText
    private lateinit var editGoalDistance : EditText
    private lateinit var editGoalCalories : EditText
    private lateinit var savingChangesButton : Button
    private lateinit var database: DatabaseReference


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_goals)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.goals_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // database initialization
        initializeDbRef()

        // variables
        editGoalSteps = findViewById(R.id.editTextSteps)
        editGoalDistance = findViewById(R.id.editTextDistance)
        editGoalCalories = findViewById(R.id.editTextCalories)
        savingChangesButton = findViewById(R.id.buttonSave)

        //val db = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        loadGoals(uid)

        savingChangesButton.setOnClickListener {
            val stepsText = editGoalSteps.text.toString()
            val distanceText = editGoalDistance.text.toString()
            val caloriesText = editGoalCalories.text.toString()

            val goal_steps = stepsText.toIntOrNull()
            val goal_dist = distanceText.toIntOrNull()
            val goal_cal = caloriesText.toIntOrNull()

            if (goal_steps == null) {
                editGoalSteps.error = "Enter a valid integer"
                return@setOnClickListener
            }

            if (goal_dist == null) {
                editGoalDistance.error = "Enter a valid integer"
                return@setOnClickListener
            }

            if (goal_cal == null) {
                editGoalCalories.error = "Enter a valid integer"
                return@setOnClickListener
            }

            saveGoalsToFirebase(uid, goal_steps, goal_dist, goal_cal)
            loadGoals(uid)
        }

    }

    private fun initializeDbRef() {
        database = Firebase.database.reference
    }

    fun loadGoals(uid: String) {
        val db = FirebaseDatabase.getInstance()
        val goalsRef = db.getReference("goals").child(uid)

        goalsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stepsGoal = snapshot.child("stepsGoal").getValue(Int::class.java) ?: 0
                val distanceGoal = snapshot.child("distanceGoal").getValue(Int::class.java) ?: 0
                val caloriesGoal = snapshot.child("caloriesGoal").getValue(Int::class.java) ?: 0

                SharedData.stepsGoal = stepsGoal
                SharedData.distanceGoal = distanceGoal
                SharedData.caloriesGoal = caloriesGoal

                editGoalSteps.setText(stepsGoal.toString())
                editGoalDistance.setText(distanceGoal.toString())
                editGoalCalories.setText(caloriesGoal.toString())

                Log.d("Goals", "Steps: $stepsGoal, Distance: $distanceGoal, Calories: $caloriesGoal")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to load goals", error.toException())
            }
        })
    }

    fun saveGoalsToFirebase(uid: String, stepsGoal: Int, distanceGoal: Int, caloriesGoal: Int) {
        val goalsRef = FirebaseDatabase.getInstance().getReference("goals").child(uid)
        val goalsMap = mapOf(
            "stepsGoal" to stepsGoal,
            "distanceGoal" to distanceGoal,
            "caloriesGoal" to caloriesGoal
        )
        goalsRef.setValue(goalsMap)
            .addOnSuccessListener {
                Log.d("Firebase", "Goals saved successfully")
            }
            .addOnFailureListener {
                Log.e("Firebase", "Failed to save goals", it)
            }
    }
}