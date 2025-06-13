package com.example.myhealth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.core.content.edit

class GoalCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    // notifications vars
    private var textTitle = "Daily goals"
    private lateinit var textContentSteps: String
    private lateinit var textContentDistance: String
    private lateinit var textContentCalories: String
    private var CHANNEL_ID = "1"
    private lateinit var builder: NotificationCompat.Builder     // steps
    private lateinit var builder2: NotificationCompat.Builder    // distance
    private lateinit var builder3: NotificationCompat.Builder    // calories

    override suspend fun doWork(): Result {
        val db = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val stepsRef = db.getReference("steps").child(uid)
        val distanceRef = db.getReference("distance").child(uid)
        val caloriesRef = db.getReference("calories").child(uid)
        val goalsRef = db.getReference("goals").child(uid)

        val formatterDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val currentDate = LocalDateTime.now().format(formatterDate)

        val stepsSnapshot = stepsRef.child(currentDate).get().await()
        val distanceSnapshot = distanceRef.child(currentDate).get().await()
        val caloriesSnapshot = caloriesRef.child(currentDate).get().await()
        val goalsSnapshot = goalsRef.get().await()

        val steps = stepsSnapshot.getValue(Int::class.java) ?: 0
        val distance = distanceSnapshot.getValue(Int::class.java) ?: 0
        val calories = caloriesSnapshot.getValue(Int::class.java) ?: 0
        val goalSteps = goalsSnapshot.child("stepsGoal").getValue(Int::class.java) ?: 0
        val goalDistance = goalsSnapshot.child("distanceGoal").getValue(Int::class.java) ?: 0
        val goalCalories = goalsSnapshot.child("caloriesGoal").getValue(Int::class.java) ?: 0

        createNotifications(applicationContext)

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        // checks if notification has already been send that day
        val prefs = applicationContext.getSharedPreferences("notifications", Context.MODE_PRIVATE)
        val lastNotifiedDate = prefs.getString("notified_date", "")

        if (lastNotifiedDate != currentDate) {
            if (steps > goalSteps) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(1, builder.build())
                }
            }

            if (distance > goalDistance) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(2, builder2.build())
                }
            }

            if (calories > goalCalories) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(3, builder3.build())
                }
            }

            prefs.edit { putString("notified_date", currentDate) }
        }

        return Result.success()
    }

    private fun createNotifications(context: Context) {
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val name = "channel 1"
        val descriptionText = "Sending info about accomplishing goals"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, ProgressActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        textContentSteps = "Daily steps goal accomplished!"
        builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentTitle(textTitle)
            .setContentText(textContentSteps)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        textContentDistance = "Daily distance goal accomplished!"
        builder2 = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentTitle(textTitle)
            .setContentText(textContentDistance)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        textContentCalories = "Daily calories goal accomplished!"
        builder3 = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentTitle(textTitle)
            .setContentText(textContentCalories)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
    }
}



