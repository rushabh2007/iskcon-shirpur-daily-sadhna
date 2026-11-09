package com.example

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
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

  private lateinit var spinnerRole: Spinner
  private lateinit var layoutAdminPasscodeSection: LinearLayout
  private lateinit var etRegisterAdminPasscode: EditText

  private lateinit var etRegisterName: EditText
  private lateinit var etRegisterEmail: EditText
  private lateinit var etRegisterPassword: EditText
  private lateinit var etRegisterConfirmPassword: EditText
  private lateinit var spinnerOrganization: Spinner

  private lateinit var layoutMentorSection: LinearLayout
  private lateinit var spinnerMentor: Spinner

  private lateinit var btnCreateAccount: Button
  private lateinit var tvRegisterStatus: TextView
  private lateinit var tvRegisterError: TextView
  private lateinit var registerProgressBar: ProgressBar
  private lateinit var btnBackToLogin: Button

  private val mentorList = mutableListOf<MentorItem>()
  private var mentorListenerRegistration: ListenerRegistration? = null

  private var selectedOrgId: String = SessionManager.DEFAULT_ORG_ID
  private var selectedOrgName: String = SessionManager.DEFAULT_ORG_NAME

  private lateinit var tvRegisterSelectedOrgName: TextView
  private lateinit var btnChangeOrgFromRegister: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_register_mentee)
    findViewById<View>(R.id.rootRegisterScroll)?.let { InsetUtils.applySafeTopSpacing(it) }

    val savedOrg = SessionManager.getSelectedOrg(this)
    selectedOrgId = intent.getStringExtra("EXTRA_ORG_ID") ?: savedOrg.id
    selectedOrgName = intent.getStringExtra("EXTRA_ORG_NAME") ?: savedOrg.name
    SessionManager.saveSelectedOrg(this, selectedOrgId, selectedOrgName)

    tvRegisterSelectedOrgName = findViewById(R.id.tvRegisterSelectedOrgName)
    btnChangeOrgFromRegister = findViewById(R.id.btnChangeOrgFromRegister)

    tvRegisterSelectedOrgName.text = "Organisation: $selectedOrgName"
    btnChangeOrgFromRegister.setOnClickListener {
      val intent = Intent(this, SelectOrganizationActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
      startActivity(intent)
      finish()
    }

    spinnerRole = findViewById(R.id.spinnerRole)
    layoutAdminPasscodeSection = findViewById(R.id.layoutAdminPasscodeSection)
    etRegisterAdminPasscode = findViewById(R.id.etRegisterAdminPasscode)

    val roleAdapter = ArrayAdapter(
      this,
      android.R.layout.simple_spinner_dropdown_item,
      listOf("Counsellee", "Admin")
    )
    spinnerRole.adapter = roleAdapter
    spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val isCounsellee = position == 0
        layoutMentorSection.visibility = if (isCounsellee) View.VISIBLE else View.GONE
        layoutAdminPasscodeSection.visibility = if (isCounsellee) View.GONE else View.VISIBLE
        hideMessages()
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    etRegisterName = findViewById(R.id.etRegisterName)
    etRegisterEmail = findViewById(R.id.etRegisterEmail)
    etRegisterPassword = findViewById(R.id.etRegisterPassword)
    etRegisterConfirmPassword = findViewById(R.id.etRegisterConfirmPassword)
    spinnerOrganization = findViewById(R.id.spinnerOrganization)

    layoutMentorSection = findViewById(R.id.layoutMentorSection)
    spinnerMentor = findViewById(R.id.spinnerMentor)

    btnCreateAccount = findViewById(R.id.btnCreateAccount)
    tvRegisterStatus = findViewById(R.id.tvRegisterStatus)
    tvRegisterError = findViewById(R.id.tvRegisterError)
    registerProgressBar = findViewById(R.id.registerProgressBar)
    btnBackToLogin = findViewById(R.id.btnBackToLogin)

    btnCreateAccount.setOnClickListener {
      handleCreateAccount()
    }

    btnBackToLogin.setOnClickListener {
      finish()
    }
  }

  override fun onStart() {
    super.onStart()
    startRealtimeMentorListener(selectedOrgId)
  }

  override fun onStop() {
    super.onStop()
    stopRealtimeMentorListener()
  }

  override fun onDestroy() {
    super.onDestroy()
    stopRealtimeMentorListener()
  }

  private fun stopRealtimeMentorListener() {
    mentorListenerRegistration?.remove()
    mentorListenerRegistration = null
  }

  private fun startRealtimeMentorListener(orgId: String = selectedOrgId) {
    stopRealtimeMentorListener()

    // Show initial loading indicator if the list is currently empty
    if (mentorList.isEmpty()) {
      val loadingAdapter = ArrayAdapter(
        this@RegisterMenteeActivity,
        android.R.layout.simple_spinner_dropdown_item,
        listOf("Loading counsellors...")
      )
      spinnerMentor.adapter = loadingAdapter
    }

    // Start realtime listener scoped strictly to the selected organization.
    // The realtime listener is the authoritative source of truth.
    mentorListenerRegistration = SessionManager.listenToActiveMentors(
      organizationId = orgId,
      onUpdate = { mentors ->
        val previousSelectedMentorId = if (spinnerMentor.selectedItemPosition in 0 until mentorList.size) {
          mentorList[spinnerMentor.selectedItemPosition].id
        } else null

        mentorList.clear()
        mentorList.addAll(mentors)
        updateMentorSpinnerUI(previousSelectedMentorId)

        if (mentorList.isNotEmpty() && tvRegisterError.visibility == View.VISIBLE &&
          (tvRegisterError.text.toString().contains("counsellor", ignoreCase = true) ||
           tvRegisterError.text.toString().contains("mentor", ignoreCase = true))
        ) {
          hideMessages()
        }
      },
      onError = { e ->
        Log.w("RegisterMenteeActivity", "Realtime mentor error: ${e.message}")
        if (mentorList.isEmpty()) {
          val errAdapter = ArrayAdapter(
            this@RegisterMenteeActivity,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("No active counsellors available")
          )
          spinnerMentor.adapter = errAdapter
        }
      }
    )
  }

  private fun updateMentorSpinnerUI(previousSelectedId: String? = null) {
    if (mentorList.isNotEmpty()) {
      val mentorNames = mentorList.map { mentor ->
        if (mentor.email.isNotEmpty()) {
          "HG ${mentor.name} (${mentor.email})"
        } else {
          "HG ${mentor.name}"
        }
      }
      val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mentorNames)
      spinnerMentor.adapter = adapter

      // Preserve selection across on-the-fly additions and deletions
      if (previousSelectedId != null) {
        val preservedIndex = mentorList.indexOfFirst { it.id == previousSelectedId }
        if (preservedIndex >= 0) {
          spinnerMentor.setSelection(preservedIndex)
        }
      }
    } else {
      val emptyAdapter = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        listOf("No active counsellors available")
      )
      spinnerMentor.adapter = emptyAdapter
    }
  }

  private fun handleCreateAccount() {
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

    if (email.equals("iskconshirpur@gmail.com", ignoreCase = true)) {
      showError("This email is reserved for Super Admin and cannot be registered publicly.")
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

    val isCounsellee = spinnerRole.selectedItemPosition == 0
    val targetRole = if (isCounsellee) "counselee" else "admin"
    var selectedMentorId: String? = null

    if (isCounsellee) {
      if (mentorList.isEmpty()) {
        showError("No counsellors are available for this organisation. Please contact the administrator.")
        return
      }

      val selectedMentorIndex = spinnerMentor.selectedItemPosition
      if (selectedMentorIndex !in 0 until mentorList.size) {
        showError("Please select your assigned counsellor.")
        return
      }
      selectedMentorId = mentorList[selectedMentorIndex].id
    } else {
      // Validate Admin Passcode
      val enteredPasscode = etRegisterAdminPasscode.text.toString().trim()
      if (enteredPasscode.isEmpty()) {
        showError("Please enter the Admin Passcode to register as Administrator.")
        etRegisterAdminPasscode.requestFocus()
        return
      }
      if (enteredPasscode != SessionManager.ADMIN_PASSCODE) {
        showError("Incorrect Admin Passcode. Administrator registration denied.")
        etRegisterAdminPasscode.requestFocus()
        return
      }
    }

    val orgIdToRegister = selectedOrgId
    val orgNameToRegister = selectedOrgName

    setLoading(true)
    showStatus(if (isCounsellee) "Creating Devotee account..." else "Creating Administrator account...")

    lifecycleScope.launch {
      val result = SessionManager.registerUser(
        context = this@RegisterMenteeActivity,
        name = name,
        email = email,
        password = password,
        role = targetRole,
        mentorId = selectedMentorId,
        organizationId = orgIdToRegister,
        organizationName = orgNameToRegister
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
    val isCounsellee = role == "counselee" || role == "mentee"
    val title = if (isCounsellee) "Registration Successful!" else "Admin Registration Successful!"
    val message = if (isCounsellee) {
      "Hare Krishna, $userName!\n\nYour Devotee account has been created successfully.\n\nA request has been sent to your chosen Counsellor for approval."
    } else {
      "Hare Krishna, $userName!\n\nAdministrator account for $selectedOrgName has been created successfully."
    }

    AlertDialog.Builder(this)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton("Go to Dashboard") { _, _ ->
        val targetClass = if (isCounsellee) MenteeDashboardActivity::class.java else AdminDashboardActivity::class.java
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
    spinnerOrganization.isEnabled = !isLoading
    spinnerMentor.isEnabled = !isLoading
  }
}
