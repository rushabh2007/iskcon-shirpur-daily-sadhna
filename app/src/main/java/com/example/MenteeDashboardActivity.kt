package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
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

  private var profileListener: ListenerRegistration? = null
  private var mentorDocListener: ListenerRegistration? = null
  private var sadhnaListener: ListenerRegistration? = null

  private var currentMentorId: String? = null
  private var currentMentorName: String = "HG Counsellor"
  private val currentSadhnaRecords = mutableListOf<SadhnaSubmission>()
  private var currentProfile: UserProfile? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mentee_dashboard)

    tvMenteeWelcome = findViewById(R.id.tvMenteeWelcome)
    tvMenteeEmail = findViewById(R.id.tvMenteeEmail)
    tvAssignedMentorName = findViewById(R.id.tvAssignedMentorName)
    tvAssignedMentorEmail = findViewById(R.id.tvAssignedMentorEmail)
    btnLogout = findViewById(R.id.btnLogout)
    btnExportMenteeExcel = findViewById(R.id.btnExportMenteeExcel)
    pbMenteeLoading = findViewById(R.id.pbMenteeLoading)
    tvSadhnaHistoryHeader = findViewById(R.id.tvSadhnaHistoryHeader)
    layoutMenteeSadhnaList = findViewById(R.id.layoutMenteeSadhnaList)

    val savedProfile = SessionManager.getSavedProfile(this)
    currentProfile = savedProfile
    val menteeUid = savedProfile?.id

    if (menteeUid.isNullOrEmpty()) {
      performLogout()
      return
    }

    tvMenteeWelcome.text = "Hare Krishna, ${savedProfile.name}!"
    tvMenteeEmail.text = "Email: ${savedProfile.email}"

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }

    btnExportMenteeExcel.setOnClickListener {
      exportMySadhnaToExcel()
    }

    btnLogout.setOnClickListener {
      performLogout()
    }

    startRealtimeListeners(menteeUid)

    // Schedule 9:00 PM Daily Sadhna reminder and request Android 13+ permission if needed
    SadhnaReminderManager.scheduleDailyReminder(this)
    SadhnaReminderManager.requestNotificationPermissionIfNeeded(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    profileListener?.remove()
    profileListener = null
    mentorDocListener?.remove()
    mentorDocListener = null
    sadhnaListener?.remove()
    sadhnaListener = null
  }

  private fun startRealtimeListeners(menteeUid: String) {
    pbMenteeLoading.visibility = View.VISIBLE

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

        tvMenteeWelcome.text = "Hare Krishna, ${updatedProfile.name}!"
        tvMenteeEmail.text = "Email: ${updatedProfile.email}"

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

  private fun updateMentorDisplay(mentorId: String?) {
    mentorDocListener?.remove()
    if (mentorId.isNullOrEmpty()) {
      tvAssignedMentorName.text = "Not Assigned"
      tvAssignedMentorName.setTextColor(Color.parseColor("#D32F2F"))
      tvAssignedMentorEmail.text = "Please contact temple administration for counsellor assignment."
      return
    }

    // Fetch / Listen to Counsellor's profile
    val firestore = FirebaseFirestore.getInstance()
    mentorDocListener = firestore.collection("users").document(mentorId)
      .addSnapshotListener { mentorDoc, error ->
        if (error != null || mentorDoc == null || !mentorDoc.exists()) {
          // Fallback check in mentor_profiles
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
              tvAssignedMentorName.text = "HG Counsellor (ID: $mentorId)"
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
    pbMenteeLoading.visibility = View.VISIBLE

    val profile = currentProfile ?: SessionManager.getSavedProfile(this)
    val menteeName = profile?.name ?: "Devotee"
    val menteeEmail = profile?.email ?: ""
    val menteeId = profile?.id ?: ""

    lifecycleScope.launch {
      var records = currentSadhnaRecords.toList()
      if (records.isEmpty()) {
        val directFetch = SessionManager.fetchSadhnaRecordsForMenteeDirectly(menteeId)
        if (directFetch.isSuccess) {
          records = directFetch.getOrNull() ?: emptyList()
          currentSadhnaRecords.clear()
          currentSadhnaRecords.addAll(records)
        }
      }

      if (records.isEmpty()) {
        pbMenteeLoading.visibility = View.GONE
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

      val mentorMap = mapOf(mentorProfileObj.id to mentorProfileObj)
      val menteeMap = mapOf(userProfileObj.id to userProfileObj)

      try {
        val excelFile = ExcelExportHelper.generateExcelFile(
          context = this@MenteeDashboardActivity,
          submissions = records.sortedByDescending { it.date },
          mentorMap = mentorMap,
          menteeMap = menteeMap,
          filePrefix = "My_Sadhna_${menteeName.replace(" ", "_")}"
        )
        val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(this@MenteeDashboardActivity, excelFile)
        pbMenteeLoading.visibility = View.GONE
        showExportSuccessDialog(excelFile, records.size, menteeName, savedPath)
      } catch (e: Exception) {
        pbMenteeLoading.visibility = View.GONE
        Toast.makeText(this@MenteeDashboardActivity, "Failed to export Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
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
    val intent = Intent(this, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
  }
}
