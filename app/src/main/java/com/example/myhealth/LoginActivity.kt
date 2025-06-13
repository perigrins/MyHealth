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

        // login is a default choice
        registerLayout.visibility = View.GONE

        // Login
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

        logoutButton.setOnClickListener {
            auth.signOut()
            //Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            logoutLayout.visibility = View.GONE
            loginLayout.visibility = View.VISIBLE
        }


        // register toggle
        toggleRegisterText.setOnClickListener {
            loginLayout.visibility = View.GONE
            registerLayout.visibility = View.VISIBLE
        }

        // login toggle
        toggleLoginText.setOnClickListener {
            registerLayout.visibility = View.GONE
            loginLayout.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        if (user != null) {
            showLogoutView(user.email ?: "unknown email")
        }
    }

    private fun showLogoutView(email: String) {
        loginLayout.visibility = View.GONE
        registerLayout.visibility = View.GONE

        val logoutLayout = findViewById<LinearLayout>(R.id.logoutLayout)
        val loggedText = findViewById<TextView>(R.id.loggedText)

        loggedText.text = "Hello \n$email!"
        logoutLayout.visibility = View.VISIBLE
    }

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

    private fun isValidEmail(email: String): Boolean {
        val pattern: Pattern = Patterns.EMAIL_ADDRESS
        return pattern.matcher(email).matches()
    }
}
