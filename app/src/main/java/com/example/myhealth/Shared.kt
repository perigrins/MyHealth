package com.example.myhealth

object SharedData {
    var stepsGoal: Int = 0
    var distanceGoal: Int = 0
    var caloriesGoal: Int = 0

    var currentSteps: Int = 0
    var currentDistance: Double = 0.0
    var currentCalories: Double = 0.0

    fun printStepsGoal() {
        println("Your goal: $stepsGoal")
    }

    fun printDistanceGoal() {
        println("Your goal: $distanceGoal")
    }

    fun printCaloriesGoal() {
        println("Your goal: $caloriesGoal")
    }
}