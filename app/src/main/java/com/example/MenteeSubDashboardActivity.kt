package com.example

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
import kotlinx.coroutines.launch
import com.google.firebase.firestore.ListenerRegistration

class MenteeSubDashboardActivity : ComponentActivity() {

  companion object {
    const val EXTRA_MENTEE_ID = "extra_mentee_id"
    const val EXTRA_MENTEE_NAME = "extra_mentee_name"
    const val EXTRA_MENTEE_EMAIL = "extra_mentee_email"
    const val EXTRA_MENTOR_NAME = "extra_mentor_name"
    const val EXTRA_MENTOR_ID = "extra_mentor_id"
  }

  private lateinit var btnBack: Button
  private lateinit var btnExportMenteeSubExcel: Button
  private lateinit var tvMenteeSubName: TextView
  private lateinit var tvMenteeSubEmail: TextView
  private lateinit var tvMenteeSubMentor: TextView
  private lateinit var tvTotalSubmissionsCount: TextView
  private lateinit var tvAvgJapaRounds: TextView
  private lateinit var tvLatestEntryDate: TextView
  private lateinit var tvMenteeSadhnaHeader: TextView
  private lateinit var pbMenteeSubLoading: ProgressBar
  private lateinit var layoutMenteeSubSadhnaList: LinearLayout

  private var sadhnaListener: ListenerRegistration? = null
  private var menteeId: String = ""
  private var menteeName: String = "Devotee"
  private var menteeEmail: String = ""
  private var mentorName: String = "HG Counsellor"
  private var mentorId: String = ""
  private val loadedRecords = mutableListOf<SadhnaSubmission>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mentee_sub_dashboard)

    btnBack = findViewById(R.id.btnBack)
    btnExportMenteeSubExcel = findViewById(R.id.btnExportMenteeSubExcel)
    tvMenteeSubName = findViewById(R.id.tvMenteeSubName)
    tvMenteeSubEmail = findViewById(R.id.tvMenteeSubEmail)
    tvMenteeSubMentor = findViewById(R.id.tvMenteeSubMentor)
    tvTotalSubmissionsCount = findViewById(R.id.tvTotalSubmissionsCount)
    tvAvgJapaRounds = findViewById(R.id.tvAvgJapaRounds)
    tvLatestEntryDate = findViewById(R.id.tvLatestEntryDate)
    tvMenteeSadhnaHeader = findViewById(R.id.tvMenteeSadhnaHeader)
    pbMenteeSubLoading = findViewById(R.id.pbMenteeSubLoading)
    layoutMenteeSubSadhnaList = findViewById(R.id.layoutMenteeSubSadhnaList)

    menteeId = intent.getStringExtra(EXTRA_MENTEE_ID) ?: ""
    menteeName = intent.getStringExtra(EXTRA_MENTEE_NAME) ?: "Devotee"
    menteeEmail = intent.getStringExtra(EXTRA_MENTEE_EMAIL) ?: ""
    mentorName = intent.getStringExtra(EXTRA_MENTOR_NAME) ?: "HG Counsellor"
    mentorId = intent.getStringExtra(EXTRA_MENTOR_ID) ?: ""

    tvMenteeSubName.text = "👤 $menteeName"
    tvMenteeSubEmail.text = "✉️ ${menteeEmail.ifEmpty { "devotee@iskcon.org" }}"
    tvMenteeSubMentor.text = "🪷 Assigned Counsellor: $mentorName"

    btnBack.setOnClickListener {
      finish()
    }

    btnExportMenteeSubExcel.setOnClickListener {
      exportDevoteeSadhnaToExcel()
    }

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }

    if (menteeId.isNotEmpty()) {
      startRealtimeSadhnaListener(menteeId)
    } else {
      Toast.makeText(this, "Counsellee ID not found.", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    sadhnaListener?.remove()
    sadhnaListener = null
  }

  private fun startRealtimeSadhnaListener(uid: String) {
    pbMenteeSubLoading.visibility = View.VISIBLE

    sadhnaListener?.remove()
    sadhnaListener = SessionManager.listenToSadhnaRecordsForMentee(
      menteeUid = uid,
      onUpdate = { records ->
        pbMenteeSubLoading.visibility = View.GONE
        loadedRecords.clear()
        loadedRecords.addAll(records)
        layoutMenteeSubSadhnaList.removeAllViews()

        val sortedRecords = records.sortedByDescending { it.date }
        val count = sortedRecords.size
        tvMenteeSadhnaHeader.text = "Daily Sadhna Submissions ($count)"
        tvTotalSubmissionsCount.text = "$count"

        if (sortedRecords.isNotEmpty()) {
          val avgRounds = sortedRecords.map { it.japaRounds }.average()
          tvAvgJapaRounds.text = String.format("%.1f", avgRounds)
          tvLatestEntryDate.text = sortedRecords.first().date
        } else {
          tvAvgJapaRounds.text = "0"
          tvLatestEntryDate.text = "None"
        }

        if (sortedRecords.isEmpty()) {
          val emptyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 2f
            setPadding(24, 24, 24, 24)
            gravity = android.view.Gravity.CENTER
          }
          val emptyTv = TextView(this).apply {
            text = "No Sadhna records submitted yet by this devotee."
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
          }
          emptyCard.addView(emptyTv)
          layoutMenteeSubSadhnaList.addView(emptyCard)
        } else {
          for (record in sortedRecords) {
            val card = createSadhnaRecordCard(record)
            layoutMenteeSubSadhnaList.addView(card)
          }
        }
      },
      onError = { e ->
        pbMenteeSubLoading.visibility = View.GONE
        Toast.makeText(this, "Error loading devotee sadhna records: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )
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
        setMargins(0, 0, 0, 12)
      }
      layoutParams = params
      isClickable = true
      isFocusable = true
      setOnClickListener {
        SadhnaDetailDialog.show(this@MenteeSubDashboardActivity, record)
      }
    }

    // Row 1: Date & Japa Rounds badge
    val row1 = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val dateTv = TextView(this).apply {
      text = "📅 Date: ${record.date}"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val japaBadge = TextView(this).apply {
      text = "📿 ${record.japaRounds} Rounds"
      textSize = 13f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    row1.addView(dateTv)
    row1.addView(japaBadge)
    container.addView(row1)

    // Row 2: Wake Up & Sleeping time
    val timingsText = buildString {
      append("⏰ Wake Up: ${record.wakeUpTime.ifEmpty { "--:--" }}")
      if (record.sleepingTime.isNotEmpty()) {
        append("  |  Sleep: ${record.sleepingTime}")
      }
    }
    val timingsTv = TextView(this).apply {
      text = timingsText
      textSize = 13f
      setTextColor(Color.parseColor("#424242"))
      setPadding(0, 4, 0, 0)
    }
    container.addView(timingsTv)

    // Row 3: Activities attended count / chips
    val actCount = record.spiritualActivities.size
    val actText = if (actCount > 0) {
      "🪷 Attended $actCount Programs (${record.spiritualActivities.take(3).joinToString(", ")}${if (actCount > 3) "..." else ""})"
    } else {
      "🪷 No temple programs recorded"
    }
    val actTv = TextView(this).apply {
      text = actText
      textSize = 12f
      setTextColor(if (actCount > 0) Color.parseColor("#1B5E20") else Color.parseColor("#757575"))
      setPadding(0, 3, 0, 0)
    }
    container.addView(actTv)

    // Row 4: Reading & Seva summary
    val studySevaParts = mutableListOf<String>()
    if (record.bookReadingSubject.isNotEmpty()) {
      studySevaParts.add("📖 Reading: ${record.bookReadingSubject}")
    }
    if (record.hearingSubject.isNotEmpty()) {
      studySevaParts.add("🎧 Lecture: ${record.hearingSubject}")
    }
    if (record.sevaCompleted) {
      studySevaParts.add("🌺 Seva Done")
    }

    if (studySevaParts.isNotEmpty()) {
      val studyTv = TextView(this).apply {
        text = studySevaParts.joinToString("  •  ")
        textSize = 12f
        setTextColor(Color.parseColor("#555555"))
        setPadding(0, 3, 0, 0)
      }
      container.addView(studyTv)
    }

    // Row 5: Click to view details affordance
    val viewDetailTv = TextView(this).apply {
      text = "Tap to view complete details ➔"
      textSize = 12f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 6, 0, 0)
    }
    container.addView(viewDetailTv)

    return container
  }

  private fun exportDevoteeSadhnaToExcel() {
    pbMenteeSubLoading.visibility = View.VISIBLE

    lifecycleScope.launch {
      var records = loadedRecords.toList()
      if (records.isEmpty()) {
        val directFetch = SessionManager.fetchSadhnaRecordsForMenteeDirectly(menteeId)
        if (directFetch.isSuccess) {
          records = directFetch.getOrNull() ?: emptyList()
          loadedRecords.clear()
          loadedRecords.addAll(records)
        }
      }

      if (records.isEmpty()) {
        pbMenteeSubLoading.visibility = View.GONE
        Toast.makeText(this@MenteeSubDashboardActivity, "No Sadhna records available to export yet for this devotee.", Toast.LENGTH_LONG).show()
        return@launch
      }

      val menteeProfile = UserProfile(
        id = menteeId,
        name = menteeName,
        email = menteeEmail,
        role = "mentee",
        mentorId = mentorId,
        mentorName = mentorName
      )

      val mentorProfile = UserProfile(
        id = mentorId.ifEmpty { "mentor" },
        name = mentorName,
        email = "",
        role = "mentor"
      )

      val mentorMap = mapOf(mentorProfile.id to mentorProfile)
      val menteeMap = mapOf(menteeProfile.id to menteeProfile)

      try {
        val excelFile = ExcelExportHelper.generateExcelFile(
          context = this@MenteeSubDashboardActivity,
          submissions = records.sortedByDescending { it.date },
          mentorMap = mentorMap,
          menteeMap = menteeMap,
          filePrefix = "Sadhna_${menteeName.replace(" ", "_")}"
        )
        val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(this@MenteeSubDashboardActivity, excelFile)
        pbMenteeSubLoading.visibility = View.GONE
        showExportSuccessDialog(excelFile, records.size, menteeName, savedPath)
      } catch (e: Exception) {
        pbMenteeSubLoading.visibility = View.GONE
        Toast.makeText(this@MenteeSubDashboardActivity, "Failed to export Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
      text = "Exported $rowCount Sadhna records for $devoteeName to Microsoft Excel (.xlsx) file."
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
        val path = ExcelExportHelper.saveExcelToPublicDownloads(this@MenteeSubDashboardActivity, file)
        if (path != null) {
          Toast.makeText(this@MenteeSubDashboardActivity, "Saved to $path", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@MenteeSubDashboardActivity, "File is ready in app storage: ${file.name}", Toast.LENGTH_SHORT).show()
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
        ExcelExportHelper.openExcelFile(this@MenteeSubDashboardActivity, file)
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
        ExcelExportHelper.shareExcelFile(this@MenteeSubDashboardActivity, file, "ISKCON Daily Sadhna - $devoteeName")
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
}
