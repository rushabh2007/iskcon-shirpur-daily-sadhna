package com.example

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class LoginActivity : ComponentActivity() {

  private lateinit var etEmail: EditText
  private lateinit var etPassword: EditText
  private lateinit var ivTogglePassword: ImageView
  private lateinit var tvForgotPassword: TextView
  private lateinit var btnLogin: Button
  private lateinit var btnCreateAccount: Button
  private lateinit var tvErrorMessage: TextView
  private lateinit var progressBar: ProgressBar

  private var isPasswordVisible = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_login)

    etEmail = findViewById(R.id.etEmail)
    etPassword = findViewById(R.id.etPassword)
    ivTogglePassword = findViewById(R.id.ivTogglePassword)
    tvForgotPassword = findViewById(R.id.tvForgotPassword)
    btnLogin = findViewById(R.id.btnLogin)
    btnCreateAccount = findViewById(R.id.btnCreateAccount)
    tvErrorMessage = findViewById(R.id.tvErrorMessage)
    progressBar = findViewById(R.id.progressBar)

    // Check existing session
    val savedProfile = SessionManager.getSavedProfile(this)
    if (savedProfile != null) {
      navigateToRoleDashboard(savedProfile.role)
    }

    ivTogglePassword.setOnClickListener {
      togglePasswordVisibility()
    }

    tvForgotPassword.setOnClickListener {
      showForgotPasswordDialog()
    }

    btnLogin.setOnClickListener {
      performLogin()
    }

    btnCreateAccount.setOnClickListener {
      val intent = Intent(this, RegisterMenteeActivity::class.java)
      startActivity(intent)
    }
  }

  private fun togglePasswordVisibility() {
    isPasswordVisible = !isPasswordVisible
    if (isPasswordVisible) {
      etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
      ivTogglePassword.setImageResource(R.drawable.ic_eye_off_navy)
    } else {
      etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
      ivTogglePassword.setImageResource(R.drawable.ic_eye_navy)
    }
    etPassword.setSelection(etPassword.text.length)
  }

  private fun showForgotPasswordDialog() {
    val input = EditText(this).apply {
      hint = "Enter your registered email"
      setText(etEmail.text.toString().trim())
      setPadding(48, 32, 48, 32)
    }

    AlertDialog.Builder(this)
      .setTitle("Reset Password")
      .setMessage("Enter your email address to receive a password reset link from Firebase:")
      .setView(input)
      .setPositiveButton("Send Link") { _, _ ->
        val email = input.text.toString().trim()
        if (email.isNotEmpty()) {
          FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener {
              Toast.makeText(this, "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
              Toast.makeText(this, "Error: ${SessionManager.formatErrorMessage(e)}", Toast.LENGTH_LONG).show()
            }
        } else {
          Toast.makeText(this, "Please enter an email address.", Toast.LENGTH_SHORT).show()
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun performLogin() {
    val email = etEmail.text.toString().trim()
    val password = etPassword.text.toString().trim()

    hideError()

    if (email.isEmpty()) {
      showError("Please enter your email or username.")
      etEmail.requestFocus()
      return
    }

    if (password.isEmpty()) {
      showError("Please enter your password.")
      etPassword.requestFocus()
      return
    }

    setLoading(true)

    lifecycleScope.launch {
      val result = SessionManager.signIn(email, password)
      setLoading(false)

      if (result.isSuccess) {
        val (token, profile) = result.getOrThrow()
        SessionManager.saveSession(this@LoginActivity, token, profile)
        navigateToRoleDashboard(profile.role)
      } else {
        val formattedError = SessionManager.formatErrorMessage(result.exceptionOrNull())
        showError(formattedError)
      }
    }
  }

  private fun navigateToRoleDashboard(role: String) {
    when (role.lowercase(Locale.getDefault()).trim()) {
      "admin" -> navigateToDashboard(AdminDashboardActivity::class.java)
      "mentor" -> navigateToDashboard(MentorDashboardActivity::class.java)
      "mentee" -> navigateToDashboard(MenteeDashboardActivity::class.java)
      else -> {
        SessionManager.clearSession(this)
        showError("Invalid user role assigned. Please contact the administrator.")
      }
    }
  }

  private fun <T> navigateToDashboard(activityClass: Class<T>) {
    val intent = Intent(this, activityClass)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
  }

  private fun showError(message: String) {
    tvErrorMessage.text = message
    tvErrorMessage.visibility = View.VISIBLE
  }

  private fun hideError() {
    tvErrorMessage.visibility = View.GONE
  }

  private fun setLoading(isLoading: Boolean) {
    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    btnLogin.isEnabled = !isLoading
    btnCreateAccount.isEnabled = !isLoading
    etEmail.isEnabled = !isLoading
    etPassword.isEnabled = !isLoading
  }
}
