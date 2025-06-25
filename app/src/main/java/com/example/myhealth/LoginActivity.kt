package com.example.myhealth

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var emailEditTextRegister: EditText
    private lateinit var passwordEditTextRegister: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var toggleRegisterText: TextView
    private lateinit var toggleLoginText: TextView
    private lateinit var loginLayout: LinearLayout
    private lateinit var registerLayout: LinearLayout
    private lateinit var logoutLayout: LinearLayout
    private lateinit var logoutButton: Button
    private lateinit var resetButton: Button
    private lateinit var resetLayout: LinearLayout
    private lateinit var toggleResetText: TextView
    private lateinit var emailEditTextReset: EditText

    /**
     * Called when the LoginActivity is created
     *
     * Sets up the UI with edge-to-edge layout, applies dark/light status bar styling
     * Initializes FirebaseAuth and all UI components related to login, registration,
     * password reset and logout
     *
     * Handles input validation and triggers login, registration, and password reset actions
     * Manages switching between login, registration, password reset, and logout views
     *
     * @param savedInstanceState if the activity is being re-initialized after previously
     * being shut down, this Bundle contains the data it most recently supplied
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {

            window.statusBarColor = ContextCompat.getColor(this, R.color.black)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        } else {
            window.statusBarColor = ContextCompat.getColor(this, R.color.white)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        }

        auth = FirebaseAuth.getInstance()

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        emailEditTextRegister = findViewById(R.id.editTextEmailRegister)
        passwordEditTextRegister = findViewById(R.id.editTextPasswordRegister)
        loginButton = findViewById(R.id.buttonLogin)
        registerButton = findViewById(R.id.buttonRegister)
        toggleRegisterText = findViewById(R.id.textToggleRegister)
        toggleLoginText = findViewById(R.id.textToggleLogin)
        loginLayout = findViewById(R.id.loginLayout)
        registerLayout = findViewById(R.id.registerLayout)
        logoutLayout = findViewById(R.id.logoutLayout)
        logoutButton = findViewById(R.id.buttonLogout)
        resetButton = findViewById(R.id.buttonResetPassword)
        resetLayout = findViewById(R.id.passwordResetLayout)
        toggleResetText = findViewById(R.id.textToggleResetPassword)
        emailEditTextReset = findViewById(R.id.resetEmailText)

        // login
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (isValidEmail(email))
            {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    loginUser(email, password)
                } else {
                    Toast.makeText(this, "All the fields must be filled", Toast.LENGTH_SHORT).show()
                }
            }
            else {
                emailEditText.error = "Enter a valid email"
            }
        }

        // register
        registerButton.setOnClickListener {
            val email = emailEditTextRegister.text.toString().trim()
            val password = passwordEditTextRegister.text.toString().trim()

            if(isValidEmail(email))
            {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    registerUser(email, password)
                } else {
                    Toast.makeText(this, "All the fields must be filled", Toast.LENGTH_SHORT).show()
                }
            }
            else {
                emailEditText.error = "Enter a valid email"
            }
        }

        resetButton.setOnClickListener {
            val email = emailEditTextReset.text.toString().trim()
            if(isValidEmail(email))
            {
                if (email.isNotEmpty()) {
                    resetPassword(email)
                } else {
                    Toast.makeText(this, "All the fields must be filled", Toast.LENGTH_SHORT).show()
                }
            }
            else {
                emailEditText.error = "Enter a valid email"
            }
        }

        toggleRegisterText.setOnClickListener {
            showOnlyLayout(registerLayout)
        }

        toggleLoginText.setOnClickListener {
            showOnlyLayout(loginLayout)
        }

        toggleResetText.setOnClickListener {
            showOnlyLayout(resetLayout)
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            showOnlyLayout(loginLayout)
        }
    }

    /**
     * Called when the activity becomes visible to the user
     *
     * Checks if a user is currently authenticated
     * If the user is authenticated, it displays the logout view using the user's email address
     */
    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        if (user != null) {
            showLogoutView(user.email ?: "unknown email")
        }
    }

    /**
     * Shows specific layout
     *
     * Shows only the specified layout and hides all others (login, register, reset, logout)
     * @param visibleLayout the layout to be made visible
     */
    private fun showOnlyLayout(visibleLayout: View) {
        loginLayout.visibility = View.GONE
        registerLayout.visibility = View.GONE
        resetLayout.visibility = View.GONE
        logoutLayout.visibility = View.GONE

        visibleLayout.visibility = View.VISIBLE
    }

    /**
     * Shows logout view
     *
     * Updates the logout view with the user's email and displays it
     * Sets a greeting message with the provided email and shows the logout layout
     *
     * @param email user's email to be used in a greeting
     */
    private fun showLogoutView(email: String) {
        val loggedText = findViewById<TextView>(R.id.loggedText)
        loggedText.text = "Hello \n$email!"
        showOnlyLayout(logoutLayout)
    }

    /**
     * Attempts to sign in user with the provided email and password using Firebase Authentication
     *
     * On successful login, shows a confirmation toast, starts `ProgressActivity`,
     * and finishes the current activity
     * On failure, shows an error toast
     *
     * @param email user's email to be used in signing in
     * @param password user's password to be used in signing in
     */
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                    startActivity(Intent(this, ProgressActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    /**
     * Attempts to register user with the provided email and password using Firebase Authentication
     *
     * On successful register, shows a confirmation toast, starts ProgressActivity
     * and finishes the current activity
     * On failure, shows an error toast
     *
     * @param email user's email to be used in registering
     * @param password user's password to be used in registering
     */
    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

                    startActivity(Intent(this, ProgressActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    /**
     * Checks the password's pattern
     *
     * Checks whether the provided email string matches the standard email pattern
     * Uses Android's built-in email address pattern to validate the format
     *
     * @param email user's email to be checked
     * @return true if the email is valid, false otherwise
     */
    private fun isValidEmail(email: String): Boolean {
        val pattern: Pattern = Patterns.EMAIL_ADDRESS
        return pattern.matcher(email).matches()
    }

    /**
     * Sends a password reset email to the specified address using Firebase Authentication
     *
     * If the email is blank, shows a toast prompting the user to enter their email
     * Otherwise, attempts to send the reset email and notifies the user of success or
     * failure via toast messages
     *
     * @param email the email address to send the password reset link to
     */
    private fun resetPassword (email: String) {
        if (email.isBlank()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Email with reset link sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Restoring password failed", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
