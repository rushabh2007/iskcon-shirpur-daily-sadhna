package com.example

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

object SadhnaDataManagementDialog {

  fun show(
    activity: Activity,
    allSadhnaRecords: List<SadhnaSubmission>,
    allDevotees: List<UserProfile>,
    adminOrgId: String,
    adminName: String,
    mentorMap: Map<String, UserProfile>,
    menteeMap: Map<String, UserProfile>,
    onRecordsDeleted: (Int) -> Unit
  ) {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = ScrollView(activity).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(26, 22, 26, 26)
      layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // Title
    val titleTv = TextView(activity).apply {
      text = "🗑️ Sadhna Data Management"
      textSize = 20f
      setTextColor(Color.parseColor("#B71C1C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
    }
    root.addView(titleTv)

    // Critical Safety Banner
    val bannerCard = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#FFF3E0"))
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = 10
        bottomMargin = 14
      }
      layoutParams = p
    }

    val bannerTitle = TextView(activity).apply {
      text = "🛡️ CRITICAL SAFETY GUARANTEE"
      textSize = 12f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    val bannerBody = TextView(activity).apply {
      text = "This management tool STRICTLY deletes daily activity entries (japa, aartis, readings, seva) from the database.\n\nUser accounts, Firebase Authentication logins, devotee profiles, Counsellor assignments, and join requests are PROTECTED and can NEVER be deleted."
      textSize = 11f
      setTextColor(Color.parseColor("#5D4037"))
      setPadding(0, 4, 0, 0)
    }
    bannerCard.addView(bannerTitle)
    bannerCard.addView(bannerBody)
    root.addView(bannerCard)

    // Current date helpers
    val cal = Calendar.getInstance()
    val curYear = cal.get(Calendar.YEAR)
    val curMonth = cal.get(Calendar.MONTH) + 1

    val monthNames = arrayOf(
      "01 - January", "02 - February", "03 - March", "04 - April",
      "05 - May", "06 - June", "07 - July", "08 - August",
      "09 - September", "10 - October", "11 - November", "12 - December"
    )

    // State
    // Mode: 0 = Month & Year, 1 = Date Range
    var filterMode = 0
    var selectedMonthIdx = curMonth
    var selectedYear = curYear
    var startDateNorm: String? = null
    var endDateNorm: String? = null

    // Target User Scope: 0 = All Users, 1 = Specific Devotee
    var targetUserScope = 0
    var targetedDevoteeId: String? = null

    // 1. TARGET USER SELECTION CARD
    val userScopeCard = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 12
      }
      layoutParams = p
    }

    val userScopeLabel = TextView(activity).apply {
      text = "1. Select User Scope:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 6)
    }
    userScopeCard.addView(userScopeLabel)

    val rgUserScope = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
    val rbAllUsers = RadioButton(activity).apply {
      text = "All Devotees & Users (${allSadhnaRecords.size} total entries)"
      isChecked = true
    }
    val rbSpecificUser = RadioButton(activity).apply {
      text = "Specific Devotee Only"
    }
    rgUserScope.addView(rbAllUsers)
    rgUserScope.addView(rbSpecificUser)
    userScopeCard.addView(rgUserScope)

    val devoteePickerLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      setPadding(0, 6, 0, 0)
    }
    val devoteeSpinner = Spinner(activity).apply {
      val names = allDevotees.map { "${it.name} (${it.email})" }
      adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, names)
    }
    devoteePickerLayout.addView(devoteeSpinner)
    userScopeCard.addView(devoteePickerLayout)
    root.addView(userScopeCard)

    // 2. TIMEFRAME SELECTION CARD
    val timeCard = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 14
      }
      layoutParams = p
    }

    val timeLabel = TextView(activity).apply {
      text = "2. Select Timeframe Criteria:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 6)
    }
    timeCard.addView(timeLabel)

    val rgTime = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
    val rbMonthYear = RadioButton(activity).apply {
      text = "By Month and Year"
      isChecked = true
    }
    val rbDateRange = RadioButton(activity).apply {
      text = "Between Two Calendar Dates (Date Range)"
    }
    rgTime.addView(rbMonthYear)
    rgTime.addView(rbDateRange)
    timeCard.addView(rgTime)

    // Month & Year sub-controls
    val monthYearLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, 6, 0, 0)
    }
    val spinnerMonth = Spinner(activity).apply {
      adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, monthNames)
      setSelection(curMonth - 1)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
        marginEnd = 6
      }
      layoutParams = p
    }
    val years = arrayOf((curYear - 2).toString(), (curYear - 1).toString(), curYear.toString(), (curYear + 1).toString())
    val spinnerYear = Spinner(activity).apply {
      adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, years)
      setSelection(2) // current year
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
      layoutParams = p
    }
    monthYearLayout.addView(spinnerMonth)
    monthYearLayout.addView(spinnerYear)
    timeCard.addView(monthYearLayout)

    // Date Range sub-controls
    val dateRangeLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      setPadding(0, 6, 0, 0)
    }
    val btnStartDate = Button(activity).apply {
      text = "📅 Start Date: Pick"
      textSize = 12f
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      setTextColor(Color.parseColor("#333333"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 6
      }
      layoutParams = p
    }
    val btnEndDate = Button(activity).apply {
      text = "📅 End Date: Pick"
      textSize = 12f
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      setTextColor(Color.parseColor("#333333"))
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    dateRangeLayout.addView(btnStartDate)
    dateRangeLayout.addView(btnEndDate)
    timeCard.addView(dateRangeLayout)

    root.addView(timeCard)

    // 3. PREVIEW CONTAINER
    val previewCard = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 14
      }
      layoutParams = p
    }

    val previewHeader = TextView(activity).apply {
      text = "3. Preview Affected Entries:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 4)
    }
    previewCard.addView(previewHeader)

    val previewCountTv = TextView(activity).apply {
      text = "0 records matching criteria"
      textSize = 13f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 8)
    }
    previewCard.addView(previewCountTv)

    val previewListLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    previewCard.addView(previewListLayout)
    root.addView(previewCard)

    // Matching logic
    fun getMatchingRecords(): List<SadhnaSubmission> {
      return allSadhnaRecords.filter { record ->
        // User filter
        if (targetUserScope == 1 && targetedDevoteeId != null) {
          if (record.menteeId != targetedDevoteeId) return@filter false
        }

        // Timeframe filter
        if (filterMode == 0) {
          ExcelExportHelper.isDateInMonthYear(record.date, selectedMonthIdx, selectedYear)
        } else {
          ExcelExportHelper.isDateInRange(record.date, startDateNorm, endDateNorm)
        }
      }
    }

    fun renderPreview() {
      val matching = getMatchingRecords()
      val timeDesc = if (filterMode == 0) {
        "${monthNames.getOrElse(selectedMonthIdx - 1) { "" }} $selectedYear"
      } else {
        "${startDateNorm ?: "Start"} to ${endDateNorm ?: "End"}"
      }
      val targetDesc = if (targetUserScope == 1) {
        val dev = allDevotees.find { it.id == targetedDevoteeId }
        dev?.name ?: "Selected Devotee"
      } else {
        "All Devotees"
      }

      previewCountTv.text = "• Target: $targetDesc\n• Criteria: $timeDesc\n• Found ${matching.size} Sadhna activity records"

      previewListLayout.removeAllViews()
      if (matching.isEmpty()) {
        val emptyTv = TextView(activity).apply {
          text = "No Sadhna records found matching these criteria."
          setTextColor(Color.parseColor("#757575"))
          textSize = 12f
          setPadding(0, 4, 0, 4)
        }
        previewListLayout.addView(emptyTv)
      } else {
        // Show up to 15 sample preview rows
        for (rec in matching.take(15)) {
          val row = TextView(activity).apply {
            val devName = rec.devoteeName?.ifEmpty { "Devotee" } ?: "Devotee"
            val progs = if (rec.spiritualActivities.isNotEmpty()) rec.spiritualActivities.joinToString(", ") else "None"
            text = "📅 ${rec.date} • 👤 $devName • 📿 ${rec.japaRounds} Rnds • 🪷 $progs"
            textSize = 11f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 2, 0, 2)
          }
          previewListLayout.addView(row)
        }
        if (matching.size > 15) {
          val moreTv = TextView(activity).apply {
            text = "+ ${matching.size - 15} more records will be affected."
            textSize = 11f
            setTextColor(Color.parseColor("#B71C1C"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 0)
          }
          previewListLayout.addView(moreTv)
        }
      }
    }

    // Connect radio listeners
    rgUserScope.setOnCheckedChangeListener { _, checkedId ->
      if (checkedId == rbAllUsers.id) {
        targetUserScope = 0
        targetedDevoteeId = null
        devoteePickerLayout.visibility = View.GONE
      } else {
        targetUserScope = 1
        devoteePickerLayout.visibility = View.VISIBLE
        if (allDevotees.isNotEmpty()) {
          targetedDevoteeId = allDevotees[devoteeSpinner.selectedItemPosition].id
        }
      }
      renderPreview()
    }

    devoteeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        if (pos in allDevotees.indices) {
          targetedDevoteeId = allDevotees[pos].id
          renderPreview()
        }
      }
      override fun onNothingSelected(p0: AdapterView<*>?) {}
    }

    rgTime.setOnCheckedChangeListener { _, checkedId ->
      if (checkedId == rbMonthYear.id) {
        filterMode = 0
        monthYearLayout.visibility = View.VISIBLE
        dateRangeLayout.visibility = View.GONE
      } else {
        filterMode = 1
        monthYearLayout.visibility = View.GONE
        dateRangeLayout.visibility = View.VISIBLE
      }
      renderPreview()
    }

    spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        selectedMonthIdx = pos + 1
        renderPreview()
      }
      override fun onNothingSelected(p0: AdapterView<*>?) {}
    }

    spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        selectedYear = years[pos].toIntOrNull() ?: curYear
        renderPreview()
      }
      override fun onNothingSelected(p0: AdapterView<*>?) {}
    }

    btnStartDate.setOnClickListener {
      val c = Calendar.getInstance()
      DatePickerDialog(
        activity,
        { _, y, m, d ->
          val norm = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
          val disp = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y)
          startDateNorm = norm
          btnStartDate.text = "📅 Start Date: $disp"
          renderPreview()
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
      ).show()
    }

    btnEndDate.setOnClickListener {
      val c = Calendar.getInstance()
      DatePickerDialog(
        activity,
        { _, y, m, d ->
          val norm = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
          val disp = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y)
          endDateNorm = norm
          btnEndDate.text = "📅 End Date: $disp"
          renderPreview()
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
      ).show()
    }

    renderPreview()

    // 4. ACTION BUTTONS: EXPORT BEFORE DELETION & DELETE
    val btnExportBackup = Button(activity).apply {
      text = "📊 EXPORT MATCHING TO EXCEL BEFORE DELETING"
      textSize = 12f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#2E7D32"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = 4
        bottomMargin = 10
      }
      layoutParams = p
      setOnClickListener {
        val records = getMatchingRecords()
        if (records.isEmpty()) {
          Toast.makeText(activity, "No records matching criteria to export.", Toast.LENGTH_SHORT).show()
          return@setOnClickListener
        }
        try {
          val excelFile = ExcelExportHelper.generateExcelFile(
            context = activity,
            submissions = records.sortedByDescending { it.date },
            mentorMap = mentorMap,
            menteeMap = menteeMap,
            filePrefix = "Backup_Before_Deletion"
          )
          val path = ExcelExportHelper.saveExcelToPublicDownloads(activity, excelFile)
          Toast.makeText(activity, "Backup Excel created! Saved to Downloads (${records.size} entries).", Toast.LENGTH_LONG).show()
          ExcelExportHelper.openExcelFile(activity, excelFile)
        } catch (e: Exception) {
          Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
      }
    }
    root.addView(btnExportBackup)

    // Progress Bar during deletion
    val pbDeleting = ProgressBar(activity).apply {
      visibility = View.GONE
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        bottomMargin = 8
      }
      layoutParams = p
    }
    root.addView(pbDeleting)

    // Delete Button
    val btnDelete = Button(activity).apply {
      text = "🗑️ PERMANENTLY DELETE SADHNA RECORDS"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#D32F2F"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8
      }
      layoutParams = p
    }

    btnDelete.setOnClickListener {
        val matching = getMatchingRecords()
        if (matching.isEmpty()) {
          Toast.makeText(activity, "No Sadhna records found matching criteria to delete.", Toast.LENGTH_LONG).show()
          return@setOnClickListener
        }

        // Detailed Confirmation Dialog
        val confirmDialog = Dialog(activity)
        confirmDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val confirmRoot = LinearLayout(activity).apply {
          orientation = LinearLayout.VERTICAL
          setBackgroundColor(Color.WHITE)
          setPadding(26, 22, 26, 24)
          layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val warnTitle = TextView(activity).apply {
          text = "⚠️ CONFIRM SADHNA DELETION"
          textSize = 17f
          setTextColor(Color.parseColor("#C62828"))
          setTypeface(null, android.graphics.Typeface.BOLD)
          gravity = android.view.Gravity.CENTER
        }
        confirmRoot.addView(warnTitle)

        val targetDesc = if (targetUserScope == 1) {
          val dev = allDevotees.find { it.id == targetedDevoteeId }
          dev?.name ?: "Selected Devotee"
        } else {
          "ALL devotees and users"
        }

        val timeDesc = if (filterMode == 0) {
          "${monthNames.getOrElse(selectedMonthIdx - 1) { "" }} $selectedYear"
        } else {
          "${startDateNorm ?: "Start"} to ${endDateNorm ?: "End"}"
        }

        val warnMsg = TextView(activity).apply {
          text = "You are about to delete ${matching.size} Sadhna activity record(s) for $targetDesc during $timeDesc.\n\n🛡️ SAFE: User profiles, Auth logins, and Counsellor assignments will NOT be touched.\n\nType DELETE in the box below to authorize this permanent deletion:"
          textSize = 12f
          setTextColor(Color.parseColor("#333333"))
          setPadding(0, 10, 0, 12)
        }
        confirmRoot.addView(warnMsg)

        val etConfirm = EditText(activity).apply {
          hint = "Type DELETE to confirm"
          textSize = 14f
          inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
          setBackgroundColor(Color.parseColor("#F5F5F5"))
          setPadding(14, 12, 14, 12)
          val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14
          }
          layoutParams = p
        }
        confirmRoot.addView(etConfirm)

        val btnExecuteDelete = Button(activity).apply {
          text = "CONFIRM & PERMANENTLY DELETE"
          textSize = 12f
          setTextColor(Color.WHITE)
          setBackgroundColor(Color.parseColor("#D32F2F"))
          val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 6
          }
          layoutParams = p
          setOnClickListener {
            val typed = etConfirm.text.toString().trim()
            if (typed != "DELETE") {
              Toast.makeText(activity, "Please type DELETE exactly to confirm deletion.", Toast.LENGTH_SHORT).show()
              return@setOnClickListener
            }

            confirmDialog.dismiss()
            pbDeleting.visibility = View.VISIBLE
            btnDelete.isEnabled = false

            val recordIdsToDelete = matching.mapNotNull { it.id }

            CoroutineScope(Dispatchers.Main).launch {
              val result = withContext(Dispatchers.IO) {
                SessionManager.deleteSadhnaRecordsBatch(recordIdsToDelete)
              }
              pbDeleting.visibility = View.GONE
              btnDelete.isEnabled = true

              if (result.isSuccess) {
                val deletedCount = result.getOrNull() ?: 0
                dialog.dismiss()
                onRecordsDeleted(deletedCount)

                AlertDialog.Builder(activity)
                  .setTitle("✅ Deletion Complete")
                  .setMessage("Successfully deleted $deletedCount Sadhna activity records from the database.\n\nAll user accounts and devotee profiles remained safe and intact.")
                  .setPositiveButton("OK", null)
                  .show()
              } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                Toast.makeText(activity, "Failed to delete: $err", Toast.LENGTH_LONG).show()
              }
            }
          }
        }
        confirmRoot.addView(btnExecuteDelete)

        val btnCancelConfirm = Button(activity).apply {
          text = "CANCEL"
          textSize = 12f
          setTextColor(Color.parseColor("#616161"))
          setBackgroundColor(Color.parseColor("#E0E0E0"))
          layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
          setOnClickListener { confirmDialog.dismiss() }
        }
        confirmRoot.addView(btnCancelConfirm)

        confirmDialog.setContentView(confirmRoot)
        confirmDialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        confirmDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        confirmDialog.show()
      }
    root.addView(btnDelete)

    val btnClose = Button(activity).apply {
      text = "CLOSE"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
      setOnClickListener { dialog.dismiss() }
    }
    root.addView(btnClose)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }
}
