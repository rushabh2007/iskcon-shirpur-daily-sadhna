package com.example

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class RegisterMenteeActivity : ComponentActivity() {

  private lateinit var etRegisterName: EditText
  private lateinit var etRegisterEmail: EditText
  private lateinit var etRegisterPassword: EditText
  private lateinit var etRegisterConfirmPassword: EditText
  private lateinit var spinnerRole: Spinner

  private lateinit var layoutMentorSection: LinearLayout
  private lateinit var spinnerMentor: Spinner

  private lateinit var layoutAdminPasscodeSection: LinearLayout
  private lateinit var etAdminPasscode: EditText

  private lateinit var btnCreateAccount: Button
  private lateinit var tvRegisterStatus: TextView
  private lateinit var tvRegisterError: TextView
  private lateinit var registerProgressBar: ProgressBar
  private lateinit var btnBackToLogin: Button

  private val mentorList = mutableListOf<MentorItem>()
  private var mentorListenerRegistration: ListenerRegistration? = null

  private val roleOptions = listOf("Counsellee", "Admin")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_register_mentee)

    etRegisterName = findViewById(R.id.etRegisterName)
    etRegisterEmail = findViewById(R.id.etRegisterEmail)
    etRegisterPassword = findViewById(R.id.etRegisterPassword)
    etRegisterConfirmPassword = findViewById(R.id.etRegisterConfirmPassword)
    spinnerRole = findViewById(R.id.spinnerRole)

    layoutMentorSection = findViewById(R.id.layoutMentorSection)
    spinnerMentor = findViewById(R.id.spinnerMentor)

    layoutAdminPasscodeSection = findViewById(R.id.layoutAdminPasscodeSection)
    etAdminPasscode = findViewById(R.id.etAdminPasscode)

    btnCreateAccount = findViewById(R.id.btnCreateAccount)
    tvRegisterStatus = findViewById(R.id.tvRegisterStatus)
    tvRegisterError = findViewById(R.id.tvRegisterError)
    registerProgressBar = findViewById(R.id.registerProgressBar)
    btnBackToLogin = findViewById(R.id.btnBackToLogin)

    setupRoleSpinner()
    setupRealtimeMentorListener()

    btnCreateAccount.setOnClickListener {
      handleCreateAccount()
    }

    btnBackToLogin.setOnClickListener {
      finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    mentorListenerRegistration?.remove()
    mentorListenerRegistration = null
  }

  private fun setupRoleSpinner() {
    val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleOptions)
    spinnerRole.adapter = roleAdapter

    spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        hideMessages()
        when (position) {
          0 -> {
            // Counsellee
            layoutMentorSection.visibility = View.VISIBLE
            layoutAdminPasscodeSection.visibility = View.GONE
            btnCreateAccount.text = "REGISTER AS COUNSELLEE"
          }
          1 -> {
            // Admin
            layoutMentorSection.visibility = View.GONE
            layoutAdminPasscodeSection.visibility = View.VISIBLE
            btnCreateAccount.text = "REGISTER AS ADMIN"
          }
        }
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
  }

  private fun setupRealtimeMentorListener() {
    // Initial loading placeholder if list is empty
    if (mentorList.isEmpty()) {
      val loadingAdapter = ArrayAdapter(
        this@RegisterMenteeActivity,
        android.R.layout.simple_spinner_dropdown_item,
        listOf("Loading counsellors...")
      )
      spinnerMentor.adapter = loadingAdapter
    }

    // Direct fetch immediately to reduce initial latency
    lifecycleScope.launch {
      val directResult = SessionManager.fetchMentors()
      if (directResult.isSuccess) {
        val directMentors = directResult.getOrNull() ?: emptyList()
        if (directMentors.isNotEmpty() && mentorList.isEmpty()) {
          mentorList.clear()
          mentorList.addAll(directMentors)
          val mentorNames = directMentors.map { mentor ->
            if (mentor.email.isNotEmpty()) {
              "HG ${mentor.name} (${mentor.email})"
            } else {
              "HG ${mentor.name}"
            }
          }
          val adapter = ArrayAdapter(this@RegisterMenteeActivity, android.R.layout.simple_spinner_dropdown_item, mentorNames)
          spinnerMentor.adapter = adapter
        }
      }
    }

    mentorListenerRegistration?.remove()
    mentorListenerRegistration = SessionManager.listenToActiveMentors(
      onUpdate = { mentors ->
        // Remember previously selected mentor ID to preserve selection during on-the-fly updates
        val previousSelectedMentorId = if (spinnerMentor.selectedItemPosition in 0 until mentorList.size) {
          mentorList[spinnerMentor.selectedItemPosition].id
        } else null

        mentorList.clear()
        mentorList.addAll(mentors)

        if (mentorList.isNotEmpty()) {
          val mentorNames = mentorList.map { mentor ->
            if (mentor.email.isNotEmpty()) {
              "HG ${mentor.name} (${mentor.email})"
            } else {
              "HG ${mentor.name}"
            }
          }
          val adapter = ArrayAdapter(this@RegisterMenteeActivity, android.R.layout.simple_spinner_dropdown_item, mentorNames)
          spinnerMentor.adapter = adapter

          // Restore selection if previously selected mentor is still in the list
          if (previousSelectedMentorId != null) {
            val preservedIndex = mentorList.indexOfFirst { it.id == previousSelectedMentorId }
            if (preservedIndex >= 0) {
              spinnerMentor.setSelection(preservedIndex)
            }
          }

          if (tvRegisterError.visibility == View.VISIBLE && (tvRegisterError.text.toString().contains("counsellor", ignoreCase = true) || tvRegisterError.text.toString().contains("mentor", ignoreCase = true))) {
            hideMessages()
          }
        } else {
          val emptyAdapter = ArrayAdapter(this@RegisterMenteeActivity, android.R.layout.simple_spinner_dropdown_item, listOf("No active counsellors available"))
          spinnerMentor.adapter = emptyAdapter
        }
      },
      onError = { exception ->
        // Keep previous valid data if available, otherwise show connection notice
        if (mentorList.isEmpty()) {
          val emptyAdapter = ArrayAdapter(this@RegisterMenteeActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Unable to load counsellors (Check connection)"))
          spinnerMentor.adapter = emptyAdapter
        }
      }
    )
  }

  private fun handleCreateAccount() {
    val selectedRolePos = spinnerRole.selectedItemPosition

    val name = etRegisterName.text.toString().trim()
    val email = etRegisterEmail.text.toString().trim()
    val password = etRegisterPassword.text.toString().trim()
    val confirmPassword = etRegisterConfirmPassword.text.toString().trim()

    hideMessages()

    if (name.isEmpty()) {
      showError("Please enter your full name.")
      etRegisterName.requestFocus()
      return
    }

    if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
      showError("Please enter a valid email address.")
      etRegisterEmail.requestFocus()
      return
    }

    if (password.length < 6) {
      showError("Password must be at least 6 characters.")
      etRegisterPassword.requestFocus()
      return
    }

    if (password != confirmPassword) {
      showError("Passwords do not match.")
      etRegisterConfirmPassword.requestFocus()
      return
    }

    // Role specific validation
    var selectedMentorId: String? = null
    val targetRole: String

    if (selectedRolePos == 1) {
      // Admin
      targetRole = "admin"
      val passcode = etAdminPasscode.text.toString().trim()
      if (passcode.isEmpty()) {
        showError("Please enter the Admin Authorization Passcode.")
        etAdminPasscode.requestFocus()
        return
      }
      if (passcode != "ISKCON@2026") {
        showError("Invalid Admin Passcode! Please enter the authorized passcode.")
        etAdminPasscode.requestFocus()
        return
      }
    } else {
      // Counsellee (DB role "mentee")
      targetRole = "mentee"
      if (mentorList.isNotEmpty()) {
        val selectedMentorIndex = spinnerMentor.selectedItemPosition
        if (selectedMentorIndex in 0 until mentorList.size) {
          selectedMentorId = mentorList[selectedMentorIndex].id
        }
      }
    }

    setLoading(true)
    showStatus(if (targetRole == "admin") "Creating Administrator account..." else "Creating Devotee account...")

    lifecycleScope.launch {
      val result = SessionManager.registerUser(
        context = this@RegisterMenteeActivity,
        name = name,
        email = email,
        password = password,
        role = targetRole,
        mentorId = selectedMentorId
      )
      setLoading(false)

      if (result.isSuccess) {
        showSuccessDialog(name, targetRole)
      } else {
        val errorMsg = SessionManager.formatErrorMessage(result.exceptionOrNull())
        showError(errorMsg)
      }
    }
  }

  private fun showSuccessDialog(userName: String, role: String) {
    val roleTitle = if (role == "admin") "Administrator" else "Devotee"
    AlertDialog.Builder(this)
      .setTitle("Registration Successful!")
      .setMessage("Hare Krishna, $userName!\n\nYour $roleTitle account has been created successfully.")
      .setPositiveButton("Go to Dashboard") { _, _ ->
        val targetClass = if (role == "admin") AdminDashboardActivity::class.java else MenteeDashboardActivity::class.java
        val intent = Intent(this, targetClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
      }
      .setCancelable(false)
      .show()
  }

  private fun showStatus(message: String) {
    tvRegisterStatus.text = message
    tvRegisterStatus.visibility = View.VISIBLE
    tvRegisterError.visibility = View.GONE
  }

  private fun showError(message: String) {
    tvRegisterError.text = message
    tvRegisterError.visibility = View.VISIBLE
    tvRegisterStatus.visibility = View.GONE
  }

  private fun hideMessages() {
    tvRegisterStatus.visibility = View.GONE
    tvRegisterError.visibility = View.GONE
  }

  private fun setLoading(isLoading: Boolean) {
    registerProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    btnCreateAccount.isEnabled = !isLoading
    btnBackToLogin.isEnabled = !isLoading
    etRegisterName.isEnabled = !isLoading
    etRegisterEmail.isEnabled = !isLoading
    etRegisterPassword.isEnabled = !isLoading
    etRegisterConfirmPassword.isEnabled = !isLoading
    spinnerRole.isEnabled = !isLoading
    spinnerMentor.isEnabled = !isLoading
    etAdminPasscode.isEnabled = !isLoading
  }
}
