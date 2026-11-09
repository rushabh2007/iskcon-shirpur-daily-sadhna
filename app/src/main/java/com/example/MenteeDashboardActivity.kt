package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MenteeDashboardActivity : ComponentActivity() {

  private lateinit var tvMenteeWelcome: TextView
  private lateinit var tvMenteeEmail: TextView
  private lateinit var tvAssignedMentorName: TextView
  private lateinit var tvAssignedMentorEmail: TextView
  private lateinit var btnLogout: Button
  private lateinit var btnExportMenteeExcel: Button
  private lateinit var pbMenteeLoading: ProgressBar
  private lateinit var tvSadhnaHistoryHeader: TextView
  private lateinit var layoutMenteeSadhnaList: LinearLayout

  // Join Request Section Views
  private lateinit var layoutAssignedMentorDisplay: LinearLayout
  private lateinit var layoutCounsellorRequestSection: LinearLayout
  private lateinit var tvRequestStatusBadge: TextView
  private lateinit var tvRequestStatusMessage: TextView
  private lateinit var layoutSendRequestForm: LinearLayout
  private lateinit var spinnerSelectCounsellor: Spinner
  private lateinit var btnSendJoinRequest: Button
  private lateinit var pbRequestActionLoading: ProgressBar

  private var profileListener: ListenerRegistration? = null
  private var mentorDocListener: ListenerRegistration? = null
  private var counselleeRequestListener: ListenerRegistration? = null
  private var sadhnaListener: ListenerRegistration? = null
  private var availableMentorsListener: ListenerRegistration? = null

  private var currentMentorId: String? = null
  private var currentMentorName: String = "HG Counsellor"
  private val currentSadhnaRecords = mutableListOf<SadhnaSubmission>()
  private var currentProfile: UserProfile? = null
  private val availableMentorsList = mutableListOf<MentorItem>()
  private var currentPendingRequest: SessionManager.CounsellorRequest? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mentee_dashboard)
    findViewById<View>(R.id.rootMenteeDashboardLayout)?.let { InsetUtils.applySafeTopSpacing(it) }

    tvMenteeWelcome = findViewById(R.id.tvMenteeWelcome)
    tvMenteeEmail = findViewById(R.id.tvMenteeEmail)
    tvAssignedMentorName = findViewById(R.id.tvAssignedMentorName)
    tvAssignedMentorEmail = findViewById(R.id.tvAssignedMentorEmail)
    btnLogout = findViewById(R.id.btnLogout)
    btnExportMenteeExcel = findViewById(R.id.btnExportMenteeExcel)
    pbMenteeLoading = findViewById(R.id.pbMenteeLoading)
    tvSadhnaHistoryHeader = findViewById(R.id.tvSadhnaHistoryHeader)
    layoutMenteeSadhnaList = findViewById(R.id.layoutMenteeSadhnaList)

    layoutAssignedMentorDisplay = findViewById(R.id.layoutAssignedMentorDisplay)
    layoutCounsellorRequestSection = findViewById(R.id.layoutCounsellorRequestSection)
    tvRequestStatusBadge = findViewById(R.id.tvRequestStatusBadge)
    tvRequestStatusMessage = findViewById(R.id.tvRequestStatusMessage)
    layoutSendRequestForm = findViewById(R.id.layoutSendRequestForm)
    spinnerSelectCounsellor = findViewById(R.id.spinnerSelectCounsellor)
    btnSendJoinRequest = findViewById(R.id.btnSendJoinRequest)
    pbRequestActionLoading = findViewById(R.id.pbRequestActionLoading)

    val savedProfile = SessionManager.getSavedProfile(this)
    currentProfile = savedProfile
    val menteeUid = savedProfile?.id

    if (menteeUid.isNullOrEmpty()) {
      performLogout()
      return
    }

    val orgName = savedProfile.organizationName.ifEmpty { SessionManager.getOrgName(savedProfile.organizationId) }
    tvMenteeWelcome.text = "Hare Krishna, ${savedProfile.name}!"
    tvMenteeEmail.text = "Email: ${savedProfile.email} • $orgName"

    updateMentorDisplay(savedProfile.mentorId)

    findViewById<Button>(R.id.btnRefreshCounselleeRequest)?.setOnClickListener {
      refreshCounselleeRequestDirectly()
    }

    btnSendJoinRequest.setOnClickListener {
      handleSendJoinRequest()
    }

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }

    btnExportMenteeExcel.setOnClickListener {
      exportMySadhnaToExcel()
    }

    findViewById<View>(R.id.btnMenteeReminders)?.setOnClickListener {
      val intent = Intent(this, SadhnaRemindersActivity::class.java)
      startActivity(intent)
    }

    btnLogout.setOnClickListener {
      performLogout()
    }

    startRealtimeListeners(menteeUid)

    // Schedule Sadhna reminders and request Android 13+ permission if needed
    SadhnaReminderManager.scheduleAllReminders(this)
    SadhnaReminderManager.requestNotificationPermissionIfNeeded(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    profileListener?.remove()
    profileListener = null
    mentorDocListener?.remove()
    mentorDocListener = null
    counselleeRequestListener?.remove()
    counselleeRequestListener = null
    sadhnaListener?.remove()
    sadhnaListener = null
    availableMentorsListener?.remove()
    availableMentorsListener = null
  }

  private fun startRealtimeListeners(menteeUid: String) {
    pbMenteeLoading.visibility = View.VISIBLE

    val orgId = currentProfile?.organizationId ?: SessionManager.DEFAULT_ORG_ID

    // 0. Real-time active counsellors listener for the join request dropdown
    availableMentorsListener?.remove()
    availableMentorsListener = SessionManager.listenToActiveMentors(
      organizationId = orgId,
      onUpdate = { mentors ->
        availableMentorsList.clear()
        availableMentorsList.addAll(mentors)
        updateCounsellorDropdown()
      },
      onError = { e ->
        Toast.makeText(this, "Error loading counsellors: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    )

    // Also trigger immediate one-shot fetch for instantaneous UI availability
    lifecycleScope.launch {
      val directMentors = SessionManager.fetchMentors(orgId)
      val mentors = directMentors.getOrNull() ?: emptyList()
      if (mentors.isNotEmpty() && availableMentorsList.isEmpty()) {
        availableMentorsList.clear()
        availableMentorsList.addAll(mentors)
        updateCounsellorDropdown()
      }
    }

    // 1. Real-time Profile & Mentor Assignment Listener
    profileListener?.remove()
    profileListener = SessionManager.listenToUserProfile(
      uid = menteeUid,
      onUpdate = { updatedProfile ->
        if (updatedProfile == null) {
          Toast.makeText(this, "Session expired or user deleted.", Toast.LENGTH_SHORT).show()
          performLogout()
          return@listenToUserProfile
        }

        val prevOrgId = currentProfile?.organizationId
        currentProfile = updatedProfile
        val token = SessionManager.getAccessToken(this) ?: ""
        SessionManager.saveSession(this, token, updatedProfile)

        // If organization changed, re-bind mentors listener
        if (updatedProfile.organizationId != prevOrgId) {
          availableMentorsListener?.remove()
          availableMentorsListener = SessionManager.listenToActiveMentors(
            organizationId = updatedProfile.organizationId,
            onUpdate = { mentors ->
              availableMentorsList.clear()
              availableMentorsList.addAll(mentors)
              updateCounsellorDropdown()
            }
          )
        }

        val orgName = updatedProfile.organizationName.ifEmpty { SessionManager.getOrgName(updatedProfile.organizationId) }
        tvMenteeWelcome.text = "Hare Krishna, ${updatedProfile.name}!"
        tvMenteeEmail.text = "Email: ${updatedProfile.email} • $orgName"

        // Check if mentor assignment changed
        if (updatedProfile.mentorId != currentMentorId || tvAssignedMentorName.text.contains("Loading")) {
          currentMentorId = updatedProfile.mentorId
          updateMentorDisplay(currentMentorId)
        }
      },
      onError = { e ->
        Toast.makeText(this, "Error syncing profile: ${SessionManager.formatErrorMessage(e)}", Toast.LENGTH_SHORT).show()
      }
    )

    // 2. Real-time Mentee Sadhna Submissions Listener
    sadhnaListener?.remove()
    sadhnaListener = SessionManager.listenToSadhnaRecordsForMentee(
      menteeUid = menteeUid,
      onUpdate = { records ->
        pbMenteeLoading.visibility = View.GONE
        currentSadhnaRecords.clear()
        currentSadhnaRecords.addAll(records)
        layoutMenteeSadhnaList.removeAllViews()
        tvSadhnaHistoryHeader.text = "My Sadhna Submissions (${records.size})"

        if (records.isEmpty()) {
          val emptyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 2f
            setPadding(16, 16, 16, 16)
            val params = LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
          }
          val emptyTv = TextView(this).apply {
            text = "No sadhna submissions recorded yet. Tap the '+' button below to record your first report!"
            setTextColor(Color.parseColor("#757575"))
            textSize = 13f
          }
          emptyCard.addView(emptyTv)
          layoutMenteeSadhnaList.addView(emptyCard)
        } else {
          for (record in records.sortedByDescending { it.date }) {
            val card = createSadhnaRecordCard(record)
            layoutMenteeSadhnaList.addView(card)
          }
        }
      },
      onError = { e ->
        pbMenteeLoading.visibility = View.GONE
        Toast.makeText(this, "Error loading sadhna records: ${SessionManager.formatErrorMessage(e)}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  private fun updateCounsellorDropdown() {
    if (availableMentorsList.isEmpty()) {
      val emptyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("No counsellors available"))
      spinnerSelectCounsellor.adapter = emptyAdapter
      btnSendJoinRequest.isEnabled = false
    } else {
      val mentorNames = availableMentorsList.map { "HG ${it.name} (${it.email})" }
      val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mentorNames)
      spinnerSelectCounsellor.adapter = adapter
      btnSendJoinRequest.isEnabled = true
    }
  }

  private fun handleSendJoinRequest() {
    val menteeUid = currentProfile?.id ?: ""
    if (menteeUid.isEmpty()) return

    // Prevent duplicate request if already pending
    if (currentPendingRequest?.status?.lowercase() == "pending") {
      Toast.makeText(this, "You already have a pending join request. Please wait for counsellor approval.", Toast.LENGTH_LONG).show()
      return
    }

    if (availableMentorsList.isEmpty()) {
      pbRequestActionLoading.visibility = View.VISIBLE
      lifecycleScope.launch {
        val orgId = currentProfile?.organizationId ?: SessionManager.DEFAULT_ORG_ID
        val fetched = SessionManager.fetchMentors(orgId)
        val mentors = fetched.getOrNull() ?: emptyList()
        pbRequestActionLoading.visibility = View.GONE
        if (mentors.isNotEmpty()) {
          availableMentorsList.clear()
          availableMentorsList.addAll(mentors)
          updateCounsellorDropdown()
          Toast.makeText(this@MenteeDashboardActivity, "Counsellors loaded. Please choose your counsellor and tap Send Join Request.", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MenteeDashboardActivity, "No counsellors are currently available in your organisation.", Toast.LENGTH_SHORT).show()
        }
      }
      return
    }

    val selectedPosition = spinnerSelectCounsellor.selectedItemPosition
    if (selectedPosition !in 0 until availableMentorsList.size) {
      Toast.makeText(this, "Please select a counsellor from the list.", Toast.LENGTH_SHORT).show()
      return
    }

    val chosenCounsellor = availableMentorsList[selectedPosition]
    val profile = currentProfile ?: return

    pbRequestActionLoading.visibility = View.VISIBLE
    btnSendJoinRequest.isEnabled = false

    lifecycleScope.launch {
      val result = SessionManager.sendCounsellorJoinRequest(
        counseleeId = menteeUid,
        counseleeName = profile.name,
        counseleeEmail = profile.email,
        counsellorId = chosenCounsellor.id,
        counsellorName = chosenCounsellor.name,
        orgId = profile.organizationId,
        organizationName = profile.organizationName
      )

      pbRequestActionLoading.visibility = View.GONE
      btnSendJoinRequest.isEnabled = true

      if (result.isSuccess) {
        showCounselleeStatusDialog(
          title = "Request Sent",
          message = "Waiting for Counsellor Approval\n\nYour join request has been sent to HG ${chosenCounsellor.name}. You will be notified as soon as your Counsellor responds."
        )
      } else {
        val err = SessionManager.formatErrorMessage(result.exceptionOrNull())
        Toast.makeText(this@MenteeDashboardActivity, "Failed to send request: $err", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun refreshCounselleeRequestDirectly() {
    val menteeUid = currentProfile?.id
      ?: SessionManager.getSavedProfile(this)?.id
      ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
      ?: ""
    if (menteeUid.isEmpty()) return

    Toast.makeText(this, "Refreshing request & counsellors...", Toast.LENGTH_SHORT).show()
    lifecycleScope.launch {
      val orgId = currentProfile?.organizationId ?: SessionManager.DEFAULT_ORG_ID
      val mentorsResult = SessionManager.fetchMentors(orgId)
      if (mentorsResult.isSuccess) {
        val mentors = mentorsResult.getOrNull() ?: emptyList()
        if (mentors.isNotEmpty()) {
          availableMentorsList.clear()
          availableMentorsList.addAll(mentors)
          updateCounsellorDropdown()
        }
      }

      val result = SessionManager.fetchCounselleeRequestDirectly(menteeUid)
      if (result.isSuccess) {
        val req = result.getOrNull()
        applyCounselleeRequestState(req)
        Toast.makeText(this@MenteeDashboardActivity, "Request status updated", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(this@MenteeDashboardActivity, "Could not refresh request", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun applyCounselleeRequestState(req: SessionManager.CounsellorRequest?) {
    currentPendingRequest = req
    if (req != null) {
      when (req.status.lowercase()) {
        "pending" -> {
          tvRequestStatusBadge.text = "WAITING FOR APPROVAL"
          tvRequestStatusBadge.setTextColor(Color.parseColor("#C2410C"))
          tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#FFEDD5"))
          val cName = if (req.counsellorName.isNotEmpty()) "HG ${req.counsellorName}" else "your chosen counsellor"
          tvRequestStatusMessage.text = "Waiting for Counsellor Approval\nYour join request to $cName is pending."
          layoutSendRequestForm.visibility = View.GONE
        }
        "accepted", "approved" -> {
          tvRequestStatusBadge.text = "ACCEPTED"
          tvRequestStatusBadge.setTextColor(Color.parseColor("#15803D"))
          tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7"))
          tvRequestStatusMessage.text = "Your request has been accepted! You are now connected with your Counsellor."
          layoutSendRequestForm.visibility = View.GONE

          // Deduplicate popup across app opens
          val popupKey = "popup_accepted_${req.id}"
          val prefs = getSharedPreferences("iskcon_counsellee_popups", MODE_PRIVATE)
          if (!prefs.getBoolean(popupKey, false)) {
            prefs.edit().putBoolean(popupKey, true).apply()
            showCounselleeStatusDialog(
              title = "Request Accepted 🎉",
              message = "Your request has been accepted! You are now connected with your Counsellor."
            )
          }
        }
        "declined", "rejected" -> {
          tvRequestStatusBadge.text = "DECLINED"
          tvRequestStatusBadge.setTextColor(Color.parseColor("#B91C1C"))
          tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#FEE2E2"))
          val cName = if (req.counsellorName.isNotEmpty()) "HG ${req.counsellorName}" else "the counsellor"
          tvRequestStatusMessage.text = "Your request has been declined by the Counsellor.\nPlease select another Counsellor below."
          layoutSendRequestForm.visibility = View.VISIBLE

          // Deduplicate popup across app opens
          val popupKey = "popup_declined_${req.id}"
          val prefs = getSharedPreferences("iskcon_counsellee_popups", MODE_PRIVATE)
          if (!prefs.getBoolean(popupKey, false)) {
            prefs.edit().putBoolean(popupKey, true).apply()
            showCounselleeStatusDialog(
              title = "Request Declined",
              message = "Your request has been declined by the Counsellor."
            )
          }
        }
        else -> {
          tvRequestStatusBadge.text = "NOT ASSIGNED"
          tvRequestStatusBadge.setTextColor(Color.parseColor("#4B5563"))
          tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#F3F4F6"))
          tvRequestStatusMessage.text = "Choose an available counsellor below to send a join request."
          layoutSendRequestForm.visibility = View.VISIBLE
        }
      }
    } else {
      tvRequestStatusBadge.text = "NOT ASSIGNED"
      tvRequestStatusBadge.setTextColor(Color.parseColor("#4B5563"))
      tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#F3F4F6"))
      tvRequestStatusMessage.text = "Choose an available counsellor below to send a join request."
      layoutSendRequestForm.visibility = View.VISIBLE
    }
  }

  private fun showCounselleeStatusDialog(title: String, message: String) {
    if (isFinishing || isDestroyed) return
    android.app.AlertDialog.Builder(this)
      .setTitle(title)
      .setMessage(message)
      .setPositiveButton("OK") { d, _ -> d.dismiss() }
      .setCancelable(false)
      .show()
  }

  private fun updateMentorDisplay(mentorId: String?) {
    mentorDocListener?.remove()
    counselleeRequestListener?.remove()

    if (mentorId.isNullOrEmpty()) {
      layoutAssignedMentorDisplay.visibility = View.GONE
      layoutCounsellorRequestSection.visibility = View.VISIBLE

      val menteeUid = currentProfile?.id.orEmpty().ifEmpty {
        SessionManager.getSavedProfile(this)?.id.orEmpty().ifEmpty {
          com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        }
      }
      if (menteeUid.isNotEmpty()) {
        counselleeRequestListener = SessionManager.listenToCounselleeRequest(
          counseleeId = menteeUid,
          onUpdate = { req ->
            applyCounselleeRequestState(req)
          },
          onError = { e ->
            android.util.Log.w("MenteeDashboard", "Error listening to counsellee request: ${e.message}")
          }
        )
      } else {
        applyCounselleeRequestState(null)
      }
      return
    }

    // Counsellor is assigned! Show assigned display and hide request section
    layoutAssignedMentorDisplay.visibility = View.VISIBLE
    layoutCounsellorRequestSection.visibility = View.GONE

    val firestore = FirebaseFirestore.getInstance()
    mentorDocListener = firestore.collection("users").document(mentorId)
      .addSnapshotListener { mentorDoc, error ->
        if (error != null || mentorDoc == null || !mentorDoc.exists()) {
          firestore.collection("mentor_profiles").document(mentorId).get()
            .addOnSuccessListener { pDoc ->
              val mName = pDoc.getString("name") ?: "HG Counsellor"
              val mEmail = pDoc.getString("email") ?: ""
              currentMentorName = mName
              tvAssignedMentorName.text = "HG $mName"
              tvAssignedMentorName.setTextColor(Color.parseColor("#1B5E20"))
              tvAssignedMentorEmail.text = if (mEmail.isNotEmpty()) "✉️ $mEmail" else ""
            }
            .addOnFailureListener {
              tvAssignedMentorName.text = "HG Counsellor"
              tvAssignedMentorName.setTextColor(Color.parseColor("#1B5E20"))
              tvAssignedMentorEmail.text = ""
            }
          return@addSnapshotListener
        }

        val name = mentorDoc.getString("name") ?: "Counsellor"
        val email = mentorDoc.getString("email") ?: ""
        currentMentorName = name
        tvAssignedMentorName.text = "HG $name"
        tvAssignedMentorName.setTextColor(Color.parseColor("#1B5E20"))
        tvAssignedMentorEmail.text = if (email.isNotEmpty()) "✉️ $email" else ""
      }
  }

  private fun exportMySadhnaToExcel() {
    val profile = currentProfile ?: SessionManager.getSavedProfile(this)
    val menteeName = profile?.name ?: "Devotee"
    val menteeEmail = profile?.email ?: ""
    val menteeId = profile?.id ?: ""

    lifecycleScope.launch {
      var records = currentSadhnaRecords.toList()
      if (records.isEmpty()) {
        pbMenteeLoading.visibility = View.VISIBLE
        val directFetch = SessionManager.fetchSadhnaRecordsForMenteeDirectly(menteeId)
        pbMenteeLoading.visibility = View.GONE
        if (directFetch.isSuccess) {
          records = directFetch.getOrNull() ?: emptyList()
          currentSadhnaRecords.clear()
          currentSadhnaRecords.addAll(records)
        }
      }

      if (records.isEmpty()) {
        Toast.makeText(this@MenteeDashboardActivity, "No Sadhna records available to export yet. Please submit your daily sadhna first.", Toast.LENGTH_LONG).show()
        return@launch
      }

      val userProfileObj = UserProfile(
        id = menteeId,
        name = menteeName,
        email = menteeEmail,
        role = "mentee",
        mentorId = currentMentorId,
        mentorName = currentMentorName
      )

      val mentorProfileObj = UserProfile(
        id = currentMentorId ?: "mentor",
        name = currentMentorName,
        email = "",
        role = "mentor"
      )

      MonthlyExcelExportDialog.show(
        activity = this@MenteeDashboardActivity,
        allRecords = records,
        userRole = "mentee",
        currentUserId = menteeId,
        currentUserName = menteeName,
        allDevotees = listOf(userProfileObj),
        mentorMap = mapOf(mentorProfileObj.id to mentorProfileObj),
        menteeMap = mapOf(userProfileObj.id to userProfileObj)
      )
    }
  }

  private fun showExportSuccessDialog(file: java.io.File, rowCount: Int, devoteeName: String, savedPath: String? = null) {
    val dialog = android.app.Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      setPadding(28, 24, 28, 24)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val iconTv = TextView(this).apply {
      text = "✅"
      textSize = 36f
      gravity = android.view.Gravity.CENTER
    }
    root.addView(iconTv)

    val titleTv = TextView(this).apply {
      text = "Excel File Exported!"
      textSize = 18f
      setTextColor(Color.parseColor("#1B5E20"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
      setPadding(0, 6, 0, 4)
    }
    root.addView(titleTv)

    val infoTv = TextView(this).apply {
      text = "Exported $rowCount Sadhna records for $devoteeName to a genuine Microsoft Excel (.xlsx) file with full columns and auto-filter."
      textSize = 13f
      setTextColor(Color.parseColor("#424242"))
      gravity = android.view.Gravity.CENTER
      setPadding(0, 0, 0, 12)
    }
    root.addView(infoTv)

    val fileDetailCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#F5F5F5"))
      setPadding(16, 10, 16, 10)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 16
      }
      layoutParams = p
    }

    val fileNameTv = TextView(this).apply {
      text = "📁 ${file.name}"
      textSize = 12f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    val fileSizeTv = TextView(this).apply {
      val sizeKb = file.length() / 1024
      val locInfo = if (savedPath != null) " • Saved to $savedPath" else ""
      text = "Size: ${sizeKb} KB • ${ExcelExportHelper.COLUMNS.size} Columns$locInfo"
      textSize = 11f
      setTextColor(Color.parseColor("#757575"))
    }
    fileDetailCard.addView(fileNameTv)
    fileDetailCard.addView(fileSizeTv)
    root.addView(fileDetailCard)

    // Save to Downloads Button
    val btnSaveDownloads = Button(this).apply {
      text = "💾 SAVE / COPY TO DOWNLOADS"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#388E3C"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        val path = ExcelExportHelper.saveExcelToPublicDownloads(this@MenteeDashboardActivity, file)
        if (path != null) {
          Toast.makeText(this@MenteeDashboardActivity, "Saved to $path", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MenteeDashboardActivity, "File is ready in app storage: ${file.name}", Toast.LENGTH_SHORT).show()
        }
      }
    }
    root.addView(btnSaveDownloads)

    val btnOpen = Button(this).apply {
      text = "📂 OPEN IN EXCEL / SHEETS"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#1976D2"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
        ExcelExportHelper.openExcelFile(this@MenteeDashboardActivity, file)
      }
    }
    root.addView(btnOpen)

    val btnShare = Button(this).apply {
      text = "📤 SHARE FILE (WhatsApp, Email...)"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#00796B"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
        ExcelExportHelper.shareExcelFile(this@MenteeDashboardActivity, file, "ISKCON Daily Sadhna - $devoteeName")
      }
    }
    root.addView(btnShare)

    val btnClose = Button(this).apply {
      text = "DONE"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnClose)

    dialog.setContentView(root)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  private fun createSadhnaRecordCard(record: SadhnaSubmission): View {
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(18, 16, 18, 16)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 14)
      }
      layoutParams = params
      isClickable = true
      isFocusable = true
      setOnClickListener {
        SadhnaDetailDialog.show(this@MenteeDashboardActivity, record)
      }
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val headerTv = TextView(this).apply {
      text = "📅 Date: ${record.date}"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val btnViewDetail = Button(this).apply {
      text = "View Details"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#E65100"))
      setTextColor(Color.WHITE)
      setOnClickListener {
        SadhnaDetailDialog.show(this@MenteeDashboardActivity, record)
      }
    }

    topRow.addView(headerTv)
    topRow.addView(btnViewDetail)

    val japaTv = TextView(this).apply {
      text = "📿 Japa: ${record.japaRounds} Rounds | Wake up: ${record.wakeUpTime.ifEmpty { "N/A" }}"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setPadding(0, 6, 0, 0)
    }

    val studyTv = TextView(this).apply {
      val bookText = if (record.bookReadingSubject.isNotEmpty()) "${record.bookReadingSubject} (${record.bookReadingTime})" else "None"
      val hearingText = if (record.hearingSubject.isNotEmpty()) "${record.hearingSubject} (${record.hearingTime})" else "None"
      text = "📖 Reading: $bookText\n🎧 Hearing: $hearingText"
      textSize = 12f
      setTextColor(Color.parseColor("#424242"))
      setPadding(0, 4, 0, 0)
    }

    val activitiesText = if (record.spiritualActivities.isNotEmpty()) {
      "🪷 Programs: " + record.spiritualActivities.joinToString(", ")
    } else {
      "🪷 Programs: No extra temple programs marked"
    }

    val activitiesTv = TextView(this).apply {
      text = activitiesText
      textSize = 12f
      setTextColor(Color.parseColor("#00695C"))
      setPadding(0, 4, 0, 0)
    }

    container.addView(topRow)
    container.addView(japaTv)
    container.addView(studyTv)
    container.addView(activitiesTv)
    return container
  }

  private fun performLogout() {
    SessionManager.clearSession(this)
    val intent = Intent(this, SelectOrganizationActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
  }
}
