package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.firestore.ListenerRegistration

/**
 * First Screen: Select Organization / Admin Panel.
 * Dynamically displays available organizations from Firestore in real-time via a dropdown.
 * Strictly isolates login and counsellee registration per organization.
 */
class SelectOrganizationActivity : ComponentActivity() {

  private lateinit var spinnerOrganization: Spinner
  private lateinit var pbOrgLoading: ProgressBar
  private lateinit var btnSelectOrgLogin: Button
  private lateinit var tvAdminContactEmail: TextView
  private lateinit var layoutRequestAdminCard: LinearLayout

  private val hintSelectOrg = "Select Organisation"
  private var organizations: List<SessionManager.OrgInfo> = emptyList()
  private var selectedOrg: SessionManager.OrgInfo? = null
  private var listenerRegistration: ListenerRegistration? = null
  private var isUpdatingSpinner = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_select_organization)
    findViewById<View>(R.id.rootSelectOrgScroll)?.let { InsetUtils.applySafeTopSpacing(it) }

    spinnerOrganization = findViewById(R.id.spinnerOrganization)
    pbOrgLoading = findViewById(R.id.pbOrgLoading)
    btnSelectOrgLogin = findViewById(R.id.btnSelectOrgLogin)
    tvAdminContactEmail = findViewById(R.id.tvAdminContactEmail)
    layoutRequestAdminCard = findViewById(R.id.layoutRequestAdminCard)

    // Initially setup placeholder spinner showing "Select Organisation"
    setupInitialSpinner()
    updateLoginButtonState()

    btnSelectOrgLogin.setOnClickListener {
      val currentOrg = selectedOrg
      if (currentOrg == null) {
        Toast.makeText(this, "Please select an organisation first", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }

      SessionManager.saveSelectedOrg(this, currentOrg.id, currentOrg.name)
      val intent = Intent(this, LoginActivity::class.java).apply {
        putExtra("EXTRA_ORG_ID", currentOrg.id)
        putExtra("EXTRA_ORG_NAME", currentOrg.name)
      }
      startActivity(intent)
    }

    val emailClickListener = View.OnClickListener {
      sendAdminRequestEmail()
    }
    val copyListener = View.OnLongClickListener {
      copyAdminEmailToClipboard()
      true
    }
    tvAdminContactEmail.setOnClickListener(emailClickListener)
    tvAdminContactEmail.setOnLongClickListener(copyListener)
    layoutRequestAdminCard.setOnClickListener(emailClickListener)
    layoutRequestAdminCard.setOnLongClickListener(copyListener)
  }

  override fun onStart() {
    super.onStart()
    startRealtimeOrganizationListener()
  }

  override fun onStop() {
    super.onStop()
    stopRealtimeOrganizationListener()
  }

  override fun onDestroy() {
    super.onDestroy()
    stopRealtimeOrganizationListener()
  }

  private fun stopRealtimeOrganizationListener() {
    listenerRegistration?.remove()
    listenerRegistration = null
  }

  private fun setupInitialSpinner() {
    val initialItems = listOf(hintSelectOrg)
    val adapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_organization, initialItems) {
      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.text = hintSelectOrg
        view.setTextColor(Color.parseColor("#6B7280"))
        view.setTypeface(null, Typeface.NORMAL)
        return view
      }

      override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.text = hintSelectOrg
        view.setTextColor(Color.parseColor("#9CA3AF"))
        view.setTypeface(null, Typeface.ITALIC)
        return view
      }
    }
    spinnerOrganization.adapter = adapter
  }

  private fun startRealtimeOrganizationListener() {
    stopRealtimeOrganizationListener()
    pbOrgLoading.visibility = View.VISIBLE

    listenerRegistration = SessionManager.listenToOrganizations(
      onUpdate = { list ->
        pbOrgLoading.visibility = View.GONE
        organizations = list

        val previousSelectedId = selectedOrg?.id
        updateSpinnerOptions(previousSelectedId)
      },
      onError = { e ->
        pbOrgLoading.visibility = View.GONE
        Log.w("SelectOrgActivity", "listenToOrganizations error: ${e.message}")
        if (organizations.isEmpty()) {
          organizations = SessionManager.DEFAULT_ORGANIZATIONS
          updateSpinnerOptions(selectedOrg?.id)
        }
      }
    )
  }

  private fun updateSpinnerOptions(previousSelectedId: String? = null) {
    val items = mutableListOf<String>()
    items.add(hintSelectOrg)
    for (org in organizations) {
      items.add(org.name)
    }

    val adapter = object : ArrayAdapter<String>(
      this,
      R.layout.item_spinner_organization,
      items
    ) {
      override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        if (position == 0) {
          view.text = hintSelectOrg
          view.setTextColor(Color.parseColor("#6B7280"))
          view.setTypeface(null, Typeface.NORMAL)
        } else {
          view.text = items[position]
          view.setTextColor(Color.parseColor("#111111"))
          view.setTypeface(null, Typeface.BOLD)
        }
        return view
      }

      override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (super.getDropDownView(position, convertView, parent) as? TextView)
          ?: View.inflate(context, R.layout.item_spinner_organization_dropdown, null) as TextView

        view.text = items[position]
        if (position == 0) {
          view.setTextColor(Color.parseColor("#9CA3AF"))
          view.setTypeface(null, Typeface.ITALIC)
        } else {
          view.setTextColor(Color.parseColor("#1F2937"))
          view.setTypeface(null, Typeface.NORMAL)
        }
        return view
      }
    }

    // Determine target selection
    var targetSelection = 0
    if (previousSelectedId != null) {
      val foundIndex = organizations.indexOfFirst { it.id.equals(previousSelectedId, ignoreCase = true) }
      if (foundIndex >= 0) {
        targetSelection = foundIndex + 1
        selectedOrg = organizations[foundIndex]
      } else {
        selectedOrg = null
        targetSelection = 0
      }
    } else {
      selectedOrg = null
      targetSelection = 0
    }

    isUpdatingSpinner = true
    spinnerOrganization.adapter = adapter
    spinnerOrganization.setSelection(targetSelection)
    isUpdatingSpinner = false

    spinnerOrganization.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (isUpdatingSpinner) return
        if (position == 0) {
          selectedOrg = null
        } else {
          val orgIndex = position - 1
          if (orgIndex in organizations.indices) {
            selectedOrg = organizations[orgIndex]
          }
        }
        updateLoginButtonState()
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {
        selectedOrg = null
        updateLoginButtonState()
      }
    }

    updateLoginButtonState()
  }

  private fun updateLoginButtonState() {
    btnSelectOrgLogin.text = "LOGIN"
    if (selectedOrg == null) {
      btnSelectOrgLogin.alpha = 0.55f
    } else {
      btnSelectOrgLogin.alpha = 1.0f
    }
  }

  private fun copyAdminEmailToClipboard() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Admin Email", SessionManager.ADMIN_REQUEST_EMAIL)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, "Email copied: ${SessionManager.ADMIN_REQUEST_EMAIL}", Toast.LENGTH_LONG).show()
  }

  private fun sendAdminRequestEmail() {
    try {
      val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${SessionManager.ADMIN_REQUEST_EMAIL}?subject=${Uri.encode("Request for New Organisation")}&body=${Uri.encode("Hare Krishna,\n\nI would like to request to create a new organisation.\n\nOrganisation / Centre Name:\nCity / Location:\nContact Person Name:\nContact Email:\nContact Phone:\n\nThank you.")}")
      }
      startActivity(Intent.createChooser(intent, "Send Email Request"))
    } catch (_: Exception) {
      copyAdminEmailToClipboard()
    }
  }
}
