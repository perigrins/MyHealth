package com.example.myhealth

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
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

    /**
     * Called when the GoalsActivity is starting
     *
     * Sets up the UI with edge-to-edge layout and initializes database references
     * Loads current user goals from Firebase and populates the input fields
     * Handles validation and saving of updated goals to Firebase when the user clicks the save button
     *
     * @param savedInstanceState if the activity is being re-initialized after previously being shut down,
     * this Bundle contains the data it most recently supplied
     */
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

    /**
     * Initializes realtime database
     *
     * Sets the main database reference as the entry point for database operations
     */
    private fun initializeDbRef() {
        database = Firebase.database.reference
    }

    /**
     * Imports user's goals from database
     *
     * Loads goals (steps, distance, calories) for a specific user
     * Fetches the data under the "goals/{uid}" node and updates both the shared data model
     * and the corresponding UI input fields with the retrieved values
     *
     * @param uid The unique identifier of the user whose goals are being loaded
     */
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

    /**
     * Saves user's goals to database
     *
     * Saves goals (steps, distance, calories) for a specific user
     * Stores the provided goal data under the "goals/{uid}" node
     * Logs the result of the operation
     *
     * @param uid the unique identifier of the user
     * @param stepsGoal the target number of steps
     * @param distanceGoal the target distance
     * @param caloriesGoal the target number of calories
     */
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