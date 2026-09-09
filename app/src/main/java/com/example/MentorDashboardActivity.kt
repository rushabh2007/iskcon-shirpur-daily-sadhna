package com.example

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MentorDashboardActivity : ComponentActivity() {

  private lateinit var tvMentorWelcome: TextView
  private lateinit var btnLogout: Button
  private lateinit var pbMentorLoading: ProgressBar

  // Tabs
  private lateinit var btnMentorTabMentees: Button
  private lateinit var btnMentorTabMenteesSadhna: Button

  // Sections
  private lateinit var layoutMentorSectionMentees: LinearLayout
  private lateinit var layoutMentorSectionMenteesSadhna: LinearLayout
  private lateinit var layoutMentorSectionMySadhna: LinearLayout

  // Lists & Headers
  private lateinit var layoutMenteesList: LinearLayout
  private lateinit var layoutSadhnaList: LinearLayout
  private lateinit var layoutMentorMySadhnaList: LinearLayout
  private lateinit var tvMenteesHeader: TextView
  private lateinit var tvSadhnaRecordsHeader: TextView
  private lateinit var tvMentorMySadhnaHeader: TextView
  private lateinit var btnExportMenteesSadhna: Button
  private lateinit var btnExportMentorMySadhna: Button

  private var menteesListener: ListenerRegistration? = null
  private var menteesSadhnaListener: ListenerRegistration? = null
  private var mySadhnaListener: ListenerRegistration? = null

  private val menteesList = mutableListOf<UserProfile>()
  private val menteesSadhnaRecords = mutableListOf<SadhnaSubmission>()
  private val myOwnSadhnaRecords = mutableListOf<SadhnaSubmission>()

  private var currentMentorProfile: UserProfile? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mentor_dashboard)

    tvMentorWelcome = findViewById(R.id.tvMentorWelcome)
    btnLogout = findViewById(R.id.btnLogout)
    pbMentorLoading = findViewById(R.id.pbMentorLoading)

    btnMentorTabMentees = findViewById(R.id.btnMentorTabMentees)
    btnMentorTabMenteesSadhna = findViewById(R.id.btnMentorTabMenteesSadhna)

    layoutMentorSectionMentees = findViewById(R.id.layoutMentorSectionMentees)
    layoutMentorSectionMenteesSadhna = findViewById(R.id.layoutMentorSectionMenteesSadhna)
    layoutMentorSectionMySadhna = findViewById(R.id.layoutMentorSectionMySadhna)

    layoutMenteesList = findViewById(R.id.layoutMenteesList)
    layoutSadhnaList = findViewById(R.id.layoutSadhnaList)
    layoutMentorMySadhnaList = findViewById(R.id.layoutMentorMySadhnaList)

    tvMenteesHeader = findViewById(R.id.tvMenteesHeader)
    tvSadhnaRecordsHeader = findViewById(R.id.tvSadhnaRecordsHeader)
    tvMentorMySadhnaHeader = findViewById(R.id.tvMentorMySadhnaHeader)

    btnExportMenteesSadhna = findViewById(R.id.btnExportMenteesSadhna)
    btnExportMentorMySadhna = findViewById(R.id.btnExportMentorMySadhna)

    val profile = SessionManager.getSavedProfile(this)
    currentMentorProfile = profile
    val name = profile?.name ?: "Counsellor"
    tvMentorWelcome.text = "Welcome, HG $name"

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }

    btnLogout.setOnClickListener {
      performLogout()
    }

    setupTabs()

    btnExportMenteesSadhna.setOnClickListener {
      exportMenteesSadhnaToExcel()
    }

    btnExportMentorMySadhna.setOnClickListener {
      exportMentorMySadhnaToExcel()
    }

    val mentorUid = profile?.id
    if (!mentorUid.isNullOrEmpty()) {
      startRealtimeListeners(mentorUid)
    }

    SadhnaReminderManager.scheduleDailyReminder(this)
    SadhnaReminderManager.requestNotificationPermissionIfNeeded(this)
  }

  private fun setupTabs() {
    btnMentorTabMentees.setOnClickListener {
      selectTab(0)
    }
    btnMentorTabMenteesSadhna.setOnClickListener {
      selectTab(1)
    }
    selectTab(0)
  }

  private fun selectTab(tabIndex: Int) {
    val activeColor = Color.parseColor("#E65100")
    val inactiveColor = Color.WHITE
    val activeTextColor = Color.WHITE
    val inactiveTextColor = Color.parseColor("#424242")

    btnMentorTabMentees.backgroundTintList = android.content.res.ColorStateList.valueOf(if (tabIndex == 0) activeColor else inactiveColor)
    btnMentorTabMentees.setTextColor(if (tabIndex == 0) activeTextColor else inactiveTextColor)

    btnMentorTabMenteesSadhna.backgroundTintList = android.content.res.ColorStateList.valueOf(if (tabIndex == 1) activeColor else inactiveColor)
    btnMentorTabMenteesSadhna.setTextColor(if (tabIndex == 1) activeTextColor else inactiveTextColor)

    layoutMentorSectionMentees.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
    layoutMentorSectionMenteesSadhna.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
    layoutMentorSectionMySadhna.visibility = View.GONE
  }

  override fun onDestroy() {
    super.onDestroy()
    menteesListener?.remove()
    menteesListener = null
    menteesSadhnaListener?.remove()
    menteesSadhnaListener = null
    mySadhnaListener?.remove()
    mySadhnaListener = null
  }

  private fun startRealtimeListeners(mentorUid: String) {
    pbMentorLoading.visibility = View.VISIBLE

    // 1. Real-time listener for assigned mentees
    menteesListener?.remove()
    menteesListener = SessionManager.listenToMenteesForMentor(
      mentorUid = mentorUid,
      onUpdate = { mentees ->
        pbMentorLoading.visibility = View.GONE
        menteesList.clear()
        menteesList.addAll(mentees)
        layoutMenteesList.removeAllViews()
        tvMenteesHeader.text = "My Assigned Counsellees (${mentees.size})"

        if (mentees.isEmpty()) {
          val emptyTv = TextView(this@MentorDashboardActivity).apply {
            text = "No counsellees assigned yet. When devotees register and choose you as their counsellor, they will appear here automatically."
            setTextColor(Color.parseColor("#757575"))
            setPadding(12, 8, 12, 8)
          }
          layoutMenteesList.addView(emptyTv)
        } else {
          for (mentee in mentees) {
            val card = createMenteeCard(mentee)
            layoutMenteesList.addView(card)
          }
        }
      },
      onError = { e ->
        pbMentorLoading.visibility = View.GONE
        Toast.makeText(this, "Error loading counsellees: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )

    // 2. Real-time listener for assigned Mentees' Sadhna submissions
    menteesSadhnaListener?.remove()
    menteesSadhnaListener = SessionManager.listenToSadhnaRecordsForMentor(
      mentorUid = mentorUid,
      onUpdate = { records ->
        menteesSadhnaRecords.clear()
        menteesSadhnaRecords.addAll(records)
        layoutSadhnaList.removeAllViews()
        tvSadhnaRecordsHeader.text = "Counsellees Sadhna Submissions (${records.size})"

        if (records.isEmpty()) {
          val emptyTv = TextView(this@MentorDashboardActivity).apply {
            text = "No sadhna submissions recorded yet by your counsellees."
            setTextColor(Color.parseColor("#757575"))
            setPadding(12, 8, 12, 8)
          }
          layoutSadhnaList.addView(emptyTv)
        } else {
          for (record in records.sortedByDescending { it.date }) {
            val card = createSadhnaRecordCard(record)
            layoutSadhnaList.addView(card)
          }
        }
      },
      onError = { e ->
        Toast.makeText(this, "Error loading sadhna records: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )

    // 3. Real-time listener for Mentor's OWN Sadhna submissions
    mySadhnaListener?.remove()
    mySadhnaListener = SessionManager.listenToSadhnaRecordsForMentee(
      menteeUid = mentorUid,
      onUpdate = { records ->
        myOwnSadhnaRecords.clear()
        myOwnSadhnaRecords.addAll(records)
        layoutMentorMySadhnaList.removeAllViews()
        tvMentorMySadhnaHeader.text = "My Sadhna Submissions (${records.size})"

        if (records.isEmpty()) {
          val emptyTv = TextView(this@MentorDashboardActivity).apply {
            text = "You haven't recorded your sadhna yet. Tap the '+' button below to record today's report!"
            setTextColor(Color.parseColor("#757575"))
            setPadding(12, 8, 12, 8)
          }
          layoutMentorMySadhnaList.addView(emptyTv)
        } else {
          for (record in records.sortedByDescending { it.date }) {
            val card = createSadhnaRecordCard(record)
            layoutMentorMySadhnaList.addView(card)
          }
        }
      },
      onError = { e ->
        Toast.makeText(this, "Error loading my sadhna: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  private fun exportMenteesSadhnaToExcel() {
    pbMentorLoading.visibility = View.VISIBLE

    val mentorProfile = currentMentorProfile ?: SessionManager.getSavedProfile(this)
    val mentorName = mentorProfile?.name ?: "Counsellor"
    val mentorId = mentorProfile?.id ?: "mentor"

    lifecycleScope.launch {
      var records = menteesSadhnaRecords.toList()
      if (records.isEmpty()) {
        val directFetch = SessionManager.fetchSadhnaRecordsForMentorDirectly(mentorId)
        if (directFetch.isSuccess) {
          records = directFetch.getOrNull() ?: emptyList()
          menteesSadhnaRecords.clear()
          menteesSadhnaRecords.addAll(records)
        }
      }

      if (records.isEmpty()) {
        pbMentorLoading.visibility = View.GONE
        Toast.makeText(this@MentorDashboardActivity, "No counsellees Sadhna records found in database.", Toast.LENGTH_LONG).show()
        return@launch
      }

      val mentorMap = mapOf(mentorId to (mentorProfile ?: UserProfile(id = mentorId, name = mentorName, email = "", role = "mentor")))
      val menteeMap = menteesList.associateBy { it.id }

      try {
        val excelFile = ExcelExportHelper.generateExcelFile(
          context = this@MentorDashboardActivity,
          submissions = records.sortedByDescending { it.date },
          mentorMap = mentorMap,
          menteeMap = menteeMap,
          filePrefix = "Counsellees_Sadhna_HG_${mentorName.replace(" ", "_")}"
        )
        val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(this@MentorDashboardActivity, excelFile)
        pbMentorLoading.visibility = View.GONE
        showExportSuccessDialog(excelFile, records.size, "Assigned Counsellees (HG $mentorName)", savedPath)
      } catch (e: Exception) {
        pbMentorLoading.visibility = View.GONE
        Toast.makeText(this@MentorDashboardActivity, "Failed to export Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun exportMentorMySadhnaToExcel() {
    pbMentorLoading.visibility = View.VISIBLE

    val mentorProfile = currentMentorProfile ?: SessionManager.getSavedProfile(this)
    val mentorName = mentorProfile?.name ?: "Counsellor"
    val mentorId = mentorProfile?.id ?: "mentor"

    lifecycleScope.launch {
      var records = myOwnSadhnaRecords.toList()
      if (records.isEmpty()) {
        val directFetch = SessionManager.fetchSadhnaRecordsForMenteeDirectly(mentorId)
        if (directFetch.isSuccess) {
          records = directFetch.getOrNull() ?: emptyList()
          myOwnSadhnaRecords.clear()
          myOwnSadhnaRecords.addAll(records)
        }
      }

      if (records.isEmpty()) {
        pbMentorLoading.visibility = View.GONE
        Toast.makeText(this@MentorDashboardActivity, "No Sadhna records available to export yet. Please submit your daily sadhna first.", Toast.LENGTH_LONG).show()
        return@launch
      }

      val mentorMap = mapOf(mentorId to (mentorProfile ?: UserProfile(id = mentorId, name = mentorName, email = "", role = "mentor")))
      val menteeMap = mapOf(mentorId to (mentorProfile ?: UserProfile(id = mentorId, name = mentorName, email = "", role = "mentor")))

      try {
        val excelFile = ExcelExportHelper.generateExcelFile(
          context = this@MentorDashboardActivity,
          submissions = records.sortedByDescending { it.date },
          mentorMap = mentorMap,
          menteeMap = menteeMap,
          filePrefix = "My_Sadhna_HG_${mentorName.replace(" ", "_")}"
        )
        val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(this@MentorDashboardActivity, excelFile)
        pbMentorLoading.visibility = View.GONE
        showExportSuccessDialog(excelFile, records.size, "HG $mentorName (My Own Sadhna)", savedPath)
      } catch (e: Exception) {
        pbMentorLoading.visibility = View.GONE
        Toast.makeText(this@MentorDashboardActivity, "Failed to export Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun showExportSuccessDialog(file: java.io.File, rowCount: Int, titleLabel: String, savedPath: String? = null) {
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
      text = "Exported $rowCount Sadhna records for $titleLabel to a formatted Microsoft Excel (.xlsx) file."
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
        val path = ExcelExportHelper.saveExcelToPublicDownloads(this@MentorDashboardActivity, file)
        if (path != null) {
          Toast.makeText(this@MentorDashboardActivity, "Saved to $path", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MentorDashboardActivity, "File is ready in app storage: ${file.name}", Toast.LENGTH_SHORT).show()
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
        ExcelExportHelper.openExcelFile(this@MentorDashboardActivity, file)
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
        ExcelExportHelper.shareExcelFile(this@MentorDashboardActivity, file, "ISKCON Daily Sadhna - $titleLabel")
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
    dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  private fun createMenteeCard(mentee: UserProfile): View {
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(16, 14, 16, 14)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 10)
      }
      layoutParams = params
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val nameTv = TextView(this).apply {
      text = "👤 ${mentee.name}"
      textSize = 15f
      setTextColor(Color.parseColor("#1C1B1F"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val btnViewSadhna = Button(this).apply {
      text = "View Sadhna"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#E65100"))
      setTextColor(Color.WHITE)
      setOnClickListener {
        openMenteeSubDashboard(mentee)
      }
    }

    val btnDetails = Button(this).apply {
      text = "Info"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#00695C"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        leftMargin = 6
      }
      layoutParams = p
      setOnClickListener {
        showMenteeDetailsDialog(mentee)
      }
    }

    val btnRemove = Button(this).apply {
      text = "Remove"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#D32F2F"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        leftMargin = 6
      }
      layoutParams = p
      setOnClickListener {
        confirmRemoveMentee(mentee)
      }
    }

    topRow.addView(nameTv)
    topRow.addView(btnViewSadhna)
    topRow.addView(btnDetails)
    topRow.addView(btnRemove)

    val emailTv = TextView(this).apply {
      text = "✉️ ${mentee.email}"
      textSize = 13f
      setTextColor(Color.parseColor("#616161"))
      setPadding(0, 4, 0, 0)
    }

    val actionHintTv = TextView(this).apply {
      text = "Tap to open Devotee Sadhna Sub-Dashboard ➔"
      textSize = 12f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 4, 0, 0)
    }

    container.isClickable = true
    container.isFocusable = true
    container.setOnClickListener {
      openMenteeSubDashboard(mentee)
    }

    container.addView(topRow)
    container.addView(emailTv)
    container.addView(actionHintTv)
    return container
  }

  private fun openMenteeSubDashboard(mentee: UserProfile) {
    val mentorProfile = SessionManager.getSavedProfile(this)
    val mentorName = mentorProfile?.name ?: "HG Counsellor"
    val mentorId = mentorProfile?.id ?: ""

    val intent = Intent(this, MenteeSubDashboardActivity::class.java).apply {
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_ID, mentee.id)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_NAME, mentee.name)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_EMAIL, mentee.email)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTOR_NAME, mentorName)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTOR_ID, mentorId)
    }
    startActivity(intent)
  }

  private fun showMenteeDetailsDialog(mentee: UserProfile) {
    val mentorProfile = SessionManager.getSavedProfile(this)
    val mentorName = mentorProfile?.name ?: "HG Counsellor"

    AlertDialog.Builder(this)
      .setTitle("Devotee Profile: ${mentee.name}")
      .setMessage("Devotee Details:\n\n• Full Name: ${mentee.name}\n• Email: ${mentee.email}\n• Assigned Counsellor: HG $mentorName\n• Devotee UID: ${mentee.id}")
      .setPositiveButton("Close", null)
      .show()
  }

  private fun confirmRemoveMentee(mentee: UserProfile) {
    AlertDialog.Builder(this)
      .setTitle("Remove Counsellee")
      .setMessage("Are you sure you want to remove ${mentee.name} from your counselling list?\n\nThe counsellee account and historical Sadhna records will remain safe and visible to the administrator.")
      .setPositiveButton("Remove") { _, _ ->
        pbMentorLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.removeMenteeFromMentor(mentee.id)
          pbMentorLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@MentorDashboardActivity, "${mentee.name} removed from counselling list.", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@MentorDashboardActivity, "Failed to remove counsellee: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
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
        SadhnaDetailDialog.show(this@MentorDashboardActivity, record)
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
      text = "📅 ${record.date} — ${record.devoteeName ?: "Devotee"}"
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
        SadhnaDetailDialog.show(this@MentorDashboardActivity, record)
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

    val activitiesText = if (record.spiritualActivities.isNotEmpty()) {
      "🌸 Aarti / Programs: " + record.spiritualActivities.joinToString(", ")
    } else {
      "🌸 Aarti / Programs: None attended"
    }
    val activitiesTv = TextView(this).apply {
      text = activitiesText
      textSize = 12f
      setTextColor(Color.parseColor("#424242"))
      setPadding(0, 2, 0, 0)
    }

    val readingHearingTv = TextView(this).apply {
      val reading = if (record.bookReadingSubject.isNotEmpty()) "${record.bookReadingSubject} (${record.bookReadingTime})" else "None"
      val hearing = if (record.hearingSubject.isNotEmpty()) "${record.hearingSubject} (${record.hearingTime})" else "None"
      text = "📖 Reading: $reading | 🎧 Hearing: $hearing"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setPadding(0, 2, 0, 0)
    }

    container.addView(topRow)
    container.addView(japaTv)
    container.addView(activitiesTv)
    container.addView(readingHearingTv)
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
