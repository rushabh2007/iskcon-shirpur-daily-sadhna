package com.example

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
  private lateinit var tvLoginOrgName: TextView
  private lateinit var btnChangeOrgFromLogin: TextView

  private var isPasswordVisible = false
  private var selectedOrgId: String = SessionManager.DEFAULT_ORG_ID
  private var selectedOrgName: String = SessionManager.DEFAULT_ORG_NAME

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_login)
    findViewById<View>(R.id.rootLoginScroll)?.let { InsetUtils.applySafeTopSpacing(it) }

    etEmail = findViewById(R.id.etEmail)
    etPassword = findViewById(R.id.etPassword)
    ivTogglePassword = findViewById(R.id.ivTogglePassword)
    tvForgotPassword = findViewById(R.id.tvForgotPassword)
    btnLogin = findViewById(R.id.btnLogin)
    btnCreateAccount = findViewById(R.id.btnCreateAccount)
    tvErrorMessage = findViewById(R.id.tvErrorMessage)
    progressBar = findViewById(R.id.progressBar)
    tvLoginOrgName = findViewById(R.id.tvLoginOrgName)
    btnChangeOrgFromLogin = findViewById(R.id.btnChangeOrgFromLogin)

    val savedOrg = SessionManager.getSelectedOrg(this)
    selectedOrgId = intent.getStringExtra("EXTRA_ORG_ID") ?: savedOrg.id
    selectedOrgName = intent.getStringExtra("EXTRA_ORG_NAME") ?: savedOrg.name
    SessionManager.saveSelectedOrg(this, selectedOrgId, selectedOrgName)

    tvLoginOrgName.text = "Organisation: $selectedOrgName"

    btnChangeOrgFromLogin.setOnClickListener {
      val intent = Intent(this, SelectOrganizationActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      startActivity(intent)
      finish()
    }

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
      val intent = Intent(this, RegisterMenteeActivity::class.java).apply {
        putExtra("EXTRA_ORG_ID", selectedOrgId)
        putExtra("EXTRA_ORG_NAME", selectedOrgName)
      }
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
        val userOrg = profile.organizationId.ifEmpty { SessionManager.DEFAULT_ORG_ID }

        // Super Admin bypasses organization restriction on Page 1 (global management account)
        if (profile.role.equals("super_admin", ignoreCase = true) || profile.email.equals("iskconshirpur@gmail.com", ignoreCase = true)) {
          SessionManager.saveSession(this@LoginActivity, token, profile)
          navigateToRoleDashboard("super_admin")
          return@launch
        }

        if (!userOrg.equals(selectedOrgId, ignoreCase = true)) {
          val actualOrgName = profile.organizationName.ifEmpty { SessionManager.getOrgName(userOrg) }
          showError("This account belongs to $actualOrgName. Please select $actualOrgName on the first screen.")
          try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
          return@launch
        }

        if (profile.role.lowercase(Locale.getDefault()).trim() == "admin") {
          promptAdminPasscode(token, profile)
        } else {
          SessionManager.saveSession(this@LoginActivity, token, profile)
          navigateToRoleDashboard(profile.role)
        }
      } else {
        val formattedError = SessionManager.formatErrorMessage(result.exceptionOrNull())
        showError(formattedError)
      }
    }
  }

  private fun promptAdminPasscode(token: String, profile: UserProfile) {
    val input = EditText(this).apply {
      hint = "Enter Admin Passcode"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      setPadding(50, 40, 50, 40)
    }

    AlertDialog.Builder(this)
      .setTitle("Admin Passcode Verification")
      .setMessage("Please enter the Admin Passcode for ${profile.organizationName}:")
      .setView(input)
      .setCancelable(false)
      .setPositiveButton("Verify & Login") { dialog, _ ->
        val enteredPasscode = input.text.toString().trim()
        if (enteredPasscode == SessionManager.ADMIN_PASSCODE) {
          SessionManager.saveSession(this@LoginActivity, token, profile)
          navigateToRoleDashboard(profile.role)
        } else {
          showError("Incorrect Admin Passcode. Access denied.")
          try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        }
        dialog.dismiss()
      }
      .setNegativeButton("Cancel") { dialog, _ ->
        try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        dialog.dismiss()
      }
      .show()
  }

  private fun navigateToRoleDashboard(role: String) {
    when (role.lowercase(Locale.getDefault()).trim()) {
      "super_admin" -> navigateToDashboard(SuperAdminDashboardActivity::class.java)
      "admin" -> navigateToDashboard(AdminDashboardActivity::class.java)
      "mentor", "counsellor" -> navigateToDashboard(MentorDashboardActivity::class.java)
      "mentee", "counselee" -> navigateToDashboard(MenteeDashboardActivity::class.java)
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
