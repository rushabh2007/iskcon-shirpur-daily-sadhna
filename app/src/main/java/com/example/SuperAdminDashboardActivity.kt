package com.example

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class SuperAdminDashboardActivity : ComponentActivity() {

  private lateinit var tvSuperAdminSubtitle: TextView
  private lateinit var btnSuperAdminLogout: Button
  private lateinit var pbSuperAdminLoading: ProgressBar
  private lateinit var btnCreateNewOrg: Button
  private lateinit var tvOrgCountHeader: TextView
  private lateinit var layoutOrgList: LinearLayout

  private var orgsListener: ListenerRegistration? = null
  private val organizationsList = mutableListOf<SessionManager.OrgInfo>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_super_admin_dashboard)
    findViewById<View>(R.id.rootSuperAdminLayout)?.let { InsetUtils.applySafeTopSpacing(it) }

    tvSuperAdminSubtitle = findViewById(R.id.tvSuperAdminSubtitle)
    btnSuperAdminLogout = findViewById(R.id.btnSuperAdminLogout)
    pbSuperAdminLoading = findViewById(R.id.pbSuperAdminLoading)
    btnCreateNewOrg = findViewById(R.id.btnCreateNewOrg)
    tvOrgCountHeader = findViewById(R.id.tvOrgCountHeader)
    layoutOrgList = findViewById(R.id.layoutOrgList)

    val profile = SessionManager.getSavedProfile(this)
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val isSuperAdmin = currentUser != null && (
      profile?.role?.equals("super_admin", ignoreCase = true) == true ||
      currentUser.email?.equals("iskconshirpur@gmail.com", ignoreCase = true) == true
    )

    if (!isSuperAdmin) {
      Toast.makeText(this, "Access Denied: Super Admin authorization required.", Toast.LENGTH_LONG).show()
      finish()
      return
    }

    val email = profile?.email ?: currentUser?.email ?: "iskconshirpur@gmail.com"
    tvSuperAdminSubtitle.text = "Logged in as $email (Super Admin)"

    btnSuperAdminLogout.setOnClickListener {
      SessionManager.clearSession(this)
      try {
        FirebaseAuth.getInstance().signOut()
      } catch (_: Exception) {}
      val intent = Intent(this, SelectOrganizationActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
      }
      startActivity(intent)
      finish()
    }

    btnCreateNewOrg.setOnClickListener {
      showCreateOrganizationDialog()
    }
  }

  override fun onStart() {
    super.onStart()
    startRealtimeOrgListener()
  }

  override fun onStop() {
    super.onStop()
    orgsListener?.remove()
    orgsListener = null
  }

  private fun startRealtimeOrgListener() {
    orgsListener?.remove()
    pbSuperAdminLoading.visibility = View.VISIBLE

    orgsListener = SessionManager.listenToOrganizations(
      onUpdate = { orgs ->
        pbSuperAdminLoading.visibility = View.GONE
        organizationsList.clear()
        organizationsList.addAll(orgs)
        renderOrganizations(orgs)
      },
      onError = { e ->
        pbSuperAdminLoading.visibility = View.GONE
        Toast.makeText(this, "Failed to load organisations: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  private fun renderOrganizations(orgs: List<SessionManager.OrgInfo>) {
    layoutOrgList.removeAllViews()
    tvOrgCountHeader.text = "Active Organisations (${orgs.size})"

    if (orgs.isEmpty()) {
      val emptyTv = TextView(this).apply {
        text = "No organisations found. Tap '+ ADD NEW ORGANISATION' to create one."
        setTextColor(Color.parseColor("#757575"))
        setPadding(16, 24, 16, 24)
        textSize = 14f
      }
      layoutOrgList.addView(emptyTv)
      return
    }

    for (org in orgs) {
      val card = createOrgCard(org)
      layoutOrgList.addView(card)
    }
  }

  private fun createOrgCard(org: SessionManager.OrgInfo): View {
    val card = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(20, 16, 20, 16)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 12)
      }
      layoutParams = params
    }

    val headerRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val nameTv = TextView(this).apply {
      text = "🏛️ ${org.name}"
      textSize = 16f
      setTextColor(Color.parseColor("#1A237E"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val statusBadge = TextView(this).apply {
      text = if (org.active) "ACTIVE" else "INACTIVE"
      textSize = 11f
      setTextColor(if (org.active) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setBackgroundColor(if (org.active) Color.parseColor("#E8F5E9") else Color.parseColor("#FFEBEE"))
      setPadding(10, 4, 10, 4)
    }

    headerRow.addView(nameTv)
    headerRow.addView(statusBadge)
    card.addView(headerRow)

    val detailsTv = TextView(this).apply {
      text = "City: ${if (org.city.isNotEmpty()) org.city else "Not specified"} • ID: ${org.id}"
      textSize = 12f
      setTextColor(Color.parseColor("#555555"))
      setPadding(0, 4, 0, 4)
    }
    card.addView(detailsTv)

    if (org.description.isNotEmpty()) {
      val descTv = TextView(this).apply {
        text = org.description
        textSize = 12f
        setTextColor(Color.parseColor("#757575"))
        setPadding(0, 0, 0, 8)
      }
      card.addView(descTv)
    }

    // Action button to add administrator for this org
    val btnAddAdmin = Button(this).apply {
      text = "+ Add Administrator for ${org.name}"
      textSize = 12f
      setBackgroundColor(Color.parseColor("#1A237E"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 6
      }
      layoutParams = p
      setOnClickListener {
        showAddAdminForOrgDialog(org)
      }
    }
    card.addView(btnAddAdmin)

    return card
  }

  private fun showCreateOrganizationDialog() {
    val dialogView = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(40, 24, 40, 16)
    }

    val etName = EditText(this).apply {
      hint = "Organisation Name (e.g. ISKCON Pune)"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }

    val etCity = EditText(this).apply {
      hint = "City / Centre (e.g. Pune)"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }

    val etDesc = EditText(this).apply {
      hint = "Description / Temple Address (optional)"
      inputType = InputType.TYPE_CLASS_TEXT
    }

    dialogView.addView(etName)
    dialogView.addView(etCity)
    dialogView.addView(etDesc)

    AlertDialog.Builder(this)
      .setTitle("Create New Organisation")
      .setMessage("Enter the details of the new ISKCON temple centre:")
      .setView(dialogView)
      .setPositiveButton("Create") { _, _ ->
        val name = etName.text.toString().trim()
        val city = etCity.text.toString().trim()
        val desc = etDesc.text.toString().trim()

        if (name.isEmpty() || city.isEmpty()) {
          Toast.makeText(this, "Organisation name and city are required.", Toast.LENGTH_SHORT).show()
          return@setPositiveButton
        }

        pbSuperAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.createOrganizationBySuperAdmin(name, city, desc)
          pbSuperAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            val newOrg = result.getOrThrow()
            Toast.makeText(this@SuperAdminDashboardActivity, "Organisation '${newOrg.name}' created successfully!", Toast.LENGTH_LONG).show()
          } else {
            val errorMsg = SessionManager.formatErrorMessage(result.exceptionOrNull())
            Toast.makeText(this@SuperAdminDashboardActivity, "Failed: $errorMsg", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showAddAdminForOrgDialog(org: SessionManager.OrgInfo) {
    val dialogView = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(40, 24, 40, 16)
    }

    val etAdminName = EditText(this).apply {
      hint = "Administrator Full Name (e.g. Radheshyam Das)"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }

    val etAdminEmail = EditText(this).apply {
      hint = "Admin Email Address"
      inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }

    val etAdminPassword = EditText(this).apply {
      hint = "Password (min 6 characters)"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    val etPasscode = EditText(this).apply {
      hint = "Admin Passcode (Mandatory)"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    dialogView.addView(etAdminName)
    dialogView.addView(etAdminEmail)
    dialogView.addView(etAdminPassword)
    dialogView.addView(etPasscode)

    AlertDialog.Builder(this)
      .setTitle("Create Administrator for ${org.name}")
      .setMessage("This user will be assigned as the Administrator for ${org.name}:")
      .setView(dialogView)
      .setPositiveButton("Create Admin") { _, _ ->
        val aName = etAdminName.text.toString().trim()
        val aEmail = etAdminEmail.text.toString().trim()
        val aPass = etAdminPassword.text.toString().trim()
        val aPasscode = etPasscode.text.toString().trim()

        if (aName.isEmpty() || aEmail.isEmpty() || aPass.length < 6) {
          Toast.makeText(this, "Please enter valid name, email, and password (min 6 chars).", Toast.LENGTH_SHORT).show()
          return@setPositiveButton
        }

        if (aPasscode != SessionManager.ADMIN_PASSCODE) {
          Toast.makeText(this, "Incorrect Admin Passcode. Administrator creation denied.", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }

        pbSuperAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.addAdmin(
            context = this@SuperAdminDashboardActivity,
            name = aName,
            email = aEmail,
            password = aPass,
            organizationId = org.id,
            organizationName = org.name
          )
          pbSuperAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@SuperAdminDashboardActivity, "Administrator for ${org.name} created successfully!", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(this@SuperAdminDashboardActivity, "Failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }
}
