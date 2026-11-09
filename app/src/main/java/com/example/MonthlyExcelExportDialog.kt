package com.example

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import java.io.File
import java.util.Calendar
import java.util.Locale

object MonthlyExcelExportDialog {

  fun show(
    activity: Activity,
    allRecords: List<SadhnaSubmission>,
    userRole: String, // "admin", "mentor", "mentee"
    currentUserId: String,
    currentUserName: String,
    allDevotees: List<UserProfile> = emptyList(),
    mentorMap: Map<String, UserProfile> = emptyMap(),
    menteeMap: Map<String, UserProfile> = emptyMap(),
    preselectedDevoteeId: String? = null
  ) {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = ScrollView(activity).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(28, 24, 28, 24)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    // Title
    val titleTv = TextView(activity).apply {
      text = "📊 Export Sadhna to Excel (.xlsx)"
      textSize = 19f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
    }
    root.addView(titleTv)

    val subTv = TextView(activity).apply {
      text = "Download genuine Microsoft Excel spreadsheet with dates, timings, japa rounds, reading, hearing, and seva records."
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      gravity = android.view.Gravity.CENTER
      setPadding(0, 4, 0, 14)
    }
    root.addView(subTv)

    // Current date helpers
    val cal = Calendar.getInstance()
    val curYear = cal.get(Calendar.YEAR)
    val curMonth = cal.get(Calendar.MONTH) + 1 // 1-12

    val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    val prevYear = prevCal.get(Calendar.YEAR)
    val prevMonth = prevCal.get(Calendar.MONTH) + 1

    val monthNames = arrayOf(
      "All Months",
      "01 - January", "02 - February", "03 - March", "04 - April",
      "05 - May", "06 - June", "07 - July", "08 - August",
      "09 - September", "10 - October", "11 - November", "12 - December"
    )

    // State
    // Preset: 0 = Current Month, 1 = Previous Month, 2 = Month & Year, 3 = Date Range, 4 = All History
    var selectedPreset = 0
    var selectedMonthIdx = curMonth
    var selectedYear = curYear
    var startDateNorm: String? = null
    var endDateNorm: String? = null

    // Scope for admin/mentor: 0 = All Users, 1 = My Own Sadhna, 2 = Specific Devotee
    var exportScope = if (userRole == "admin") 0 else if (userRole == "mentor") (if (preselectedDevoteeId == currentUserId) 1 else 0) else 1
    var targetedDevoteeId: String? = if (userRole == "mentee") currentUserId else preselectedDevoteeId

    // 1. SCOPE SELECTION (For Admin and Counsellor with counsellees)
    if (userRole == "admin" || (userRole == "mentor" && allDevotees.isNotEmpty())) {
      val scopeCard = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 2f
        setPadding(16, 12, 16, 12)
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
          bottomMargin = 12
        }
        layoutParams = p
      }

      val scopeLabel = TextView(activity).apply {
        text = "Select Export Target:"
        textSize = 13f
        setTextColor(Color.parseColor("#111111"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, 8)
      }
      scopeCard.addView(scopeLabel)

      val rgScope = RadioGroup(activity).apply {
        orientation = RadioGroup.VERTICAL
      }
      val rbAll = RadioButton(activity).apply {
        text = if (userRole == "mentor") "All My Assigned Counsellees" else "All Devotees & Users"
        isChecked = preselectedDevoteeId == null
      }
      val rbMy = RadioButton(activity).apply {
        text = "My Own Sadhna (HG $currentUserName)"
        isChecked = preselectedDevoteeId == currentUserId
      }
      val rbSpecific = RadioButton(activity).apply {
        text = if (userRole == "mentor") "Specific Counsellee" else "Specific Devotee / Counsellee"
        isChecked = preselectedDevoteeId != null && preselectedDevoteeId != currentUserId
      }
      rgScope.addView(rbAll)
      rgScope.addView(rbMy)
      rgScope.addView(rbSpecific)
      scopeCard.addView(rgScope)

      // Devotee picker container (shown when rbSpecific is checked)
      val devoteePickerLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(0, 8, 0, 0)
      }

      val spinnerDevotee = Spinner(activity).apply {
        val names = allDevotees.map { "${it.name} (${it.email})" }
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, names)
      }
      devoteePickerLayout.addView(spinnerDevotee)
      scopeCard.addView(devoteePickerLayout)

      rgScope.setOnCheckedChangeListener { _, checkedId ->
        when (checkedId) {
          rbAll.id -> {
            exportScope = 0
            targetedDevoteeId = null
            devoteePickerLayout.visibility = View.GONE
          }
          rbMy.id -> {
            exportScope = 1
            targetedDevoteeId = currentUserId
            devoteePickerLayout.visibility = View.GONE
          }
          rbSpecific.id -> {
            exportScope = 2
            devoteePickerLayout.visibility = View.VISIBLE
            if (allDevotees.isNotEmpty()) {
              targetedDevoteeId = allDevotees[spinnerDevotee.selectedItemPosition].id
            }
          }
        }
      }

      spinnerDevotee.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
          if (pos in allDevotees.indices) {
            targetedDevoteeId = allDevotees[pos].id
          }
        }
        override fun onNothingSelected(p0: AdapterView<*>?) {}
      }

      root.addView(scopeCard)
    }

    // 2. TIMEFRAME PRESET CARD
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
      text = "Select Timeframe:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 6)
    }
    timeCard.addView(timeLabel)

    val rgTime = RadioGroup(activity).apply {
      orientation = RadioGroup.VERTICAL
    }

    val curMonthName = monthNames.getOrElse(curMonth) { "Current Month" }
    val prevMonthName = monthNames.getOrElse(prevMonth) { "Previous Month" }

    val rbCurMonth = RadioButton(activity).apply {
      text = "🗓️ Current Month ($curMonthName $curYear)"
      isChecked = true
    }
    val rbPrevMonth = RadioButton(activity).apply {
      text = "🗓️ Previous Month ($prevMonthName $prevYear)"
    }
    val rbMonthYear = RadioButton(activity).apply {
      text = "📅 Month & Year Selector"
    }
    val rbDateRange = RadioButton(activity).apply {
      text = "📆 Custom Date Range"
    }
    val rbAllHistory = RadioButton(activity).apply {
      text = "📚 All Records (Entire History)"
    }

    rgTime.addView(rbCurMonth)
    rgTime.addView(rbPrevMonth)
    rgTime.addView(rbMonthYear)
    rgTime.addView(rbDateRange)
    rgTime.addView(rbAllHistory)
    timeCard.addView(rgTime)

    // Sub-layout: Month & Year Selector
    val monthYearLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      visibility = View.GONE
      setPadding(0, 8, 0, 4)
    }
    val spinnerMonth = Spinner(activity).apply {
      adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, monthNames.sliceArray(1 until monthNames.size))
      setSelection(curMonth - 1)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
        marginEnd = 6
      }
      layoutParams = p
    }
    val years = arrayOf((curYear - 1).toString(), curYear.toString(), (curYear + 1).toString())
    val spinnerYear = Spinner(activity).apply {
      adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, years)
      setSelection(1) // current year
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
      layoutParams = p
    }
    monthYearLayout.addView(spinnerMonth)
    monthYearLayout.addView(spinnerYear)
    timeCard.addView(monthYearLayout)

    // Sub-layout: Custom Date Range Selector
    val dateRangeLayout = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      setPadding(0, 8, 0, 4)
    }
    val btnStartDate = Button(activity).apply {
      text = "📅 Start Date: Select"
      textSize = 12f
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      setTextColor(Color.parseColor("#333333"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 6
      }
      layoutParams = p
    }
    val btnEndDate = Button(activity).apply {
      text = "📅 End Date: Select"
      textSize = 12f
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      setTextColor(Color.parseColor("#333333"))
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    dateRangeLayout.addView(btnStartDate)
    dateRangeLayout.addView(btnEndDate)
    timeCard.addView(dateRangeLayout)

    root.addView(timeCard)

    // 3. PREVIEW SUMMARY
    val previewTv = TextView(activity).apply {
      textSize = 13f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 14)
    }
    root.addView(previewTv)

    fun filterMatchingRecords(): List<SadhnaSubmission> {
      return allRecords.filter { record ->
        // User target check
        if (exportScope == 1 && targetedDevoteeId != null) {
          if (record.menteeId != targetedDevoteeId) return@filter false
        } else if (exportScope == 2 && targetedDevoteeId != null) {
          if (record.menteeId != targetedDevoteeId) return@filter false
        }

        // Timeframe check
        when (selectedPreset) {
          0 -> ExcelExportHelper.isDateInMonthYear(record.date, curMonth, curYear)
          1 -> ExcelExportHelper.isDateInMonthYear(record.date, prevMonth, prevYear)
          2 -> ExcelExportHelper.isDateInMonthYear(record.date, selectedMonthIdx, selectedYear)
          3 -> ExcelExportHelper.isDateInRange(record.date, startDateNorm, endDateNorm)
          4 -> true
          else -> true
        }
      }
    }

    fun updatePreview() {
      val count = filterMatchingRecords().size
      val timeDesc = when (selectedPreset) {
        0 -> "$curMonthName $curYear"
        1 -> "$prevMonthName $prevYear"
        2 -> "${monthNames.getOrElse(selectedMonthIdx) { "" }} $selectedYear"
        3 -> "${startDateNorm ?: "Start"} to ${endDateNorm ?: "End"}"
        else -> "Entire History"
      }
      previewTv.text = "• Selected Timeframe: $timeDesc\n• Matching Sadhna Entries: $count record(s)\n• Format: Microsoft Excel (.xlsx)"
    }

    // Radio button changes
    rgTime.setOnCheckedChangeListener { _, checkedId ->
      monthYearLayout.visibility = View.GONE
      dateRangeLayout.visibility = View.GONE

      when (checkedId) {
        rbCurMonth.id -> selectedPreset = 0
        rbPrevMonth.id -> selectedPreset = 1
        rbMonthYear.id -> {
          selectedPreset = 2
          monthYearLayout.visibility = View.VISIBLE
        }
        rbDateRange.id -> {
          selectedPreset = 3
          dateRangeLayout.visibility = View.VISIBLE
        }
        rbAllHistory.id -> selectedPreset = 4
      }
      updatePreview()
    }

    spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        selectedMonthIdx = pos + 1
        updatePreview()
      }
      override fun onNothingSelected(p0: AdapterView<*>?) {}
    }

    spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        selectedYear = years[pos].toIntOrNull() ?: curYear
        updatePreview()
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
          updatePreview()
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
          updatePreview()
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
      ).show()
    }

    updatePreview()

    // 4. ACTION BUTTONS
    val btnExport = Button(activity).apply {
      text = "📥 GENERATE & DOWNLOAD EXCEL (.xlsx)"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#2E7D32"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = 6
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        val records = filterMatchingRecords()
        if (records.isEmpty()) {
          Toast.makeText(activity, "No Sadhna records found matching the chosen criteria.", Toast.LENGTH_LONG).show()
          return@setOnClickListener
        }

        dialog.dismiss()

        val prefix = when {
          exportScope == 1 -> "My_Sadhna_${currentUserName.replace(" ", "_")}"
          exportScope == 2 -> {
            val dev = allDevotees.find { it.id == targetedDevoteeId }
            "Sadhna_${(dev?.name ?: "Devotee").replace(" ", "_")}"
          }
          else -> "ISKCON_Sadhna_Report"
        }

        try {
          val excelFile = ExcelExportHelper.generateExcelFile(
            context = activity,
            submissions = records.sortedByDescending { it.date },
            mentorMap = mentorMap,
            menteeMap = menteeMap,
            filePrefix = prefix
          )

          val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(activity, excelFile)
          Toast.makeText(activity, "Excel generated successfully (${records.size} entries)!", Toast.LENGTH_SHORT).show()
          showExportCompleteDialog(activity, excelFile, records.size, savedPath)
        } catch (e: Exception) {
          Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
    root.addView(btnExport)

    val btnCancel = Button(activity).apply {
      text = "CANCEL"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
      setOnClickListener { dialog.dismiss() }
    }
    root.addView(btnCancel)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  private fun showExportCompleteDialog(
    activity: Activity,
    file: File,
    rowCount: Int,
    savedPath: String?
  ) {
    val d = Dialog(activity)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val root = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      setPadding(28, 24, 28, 24)
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    val iconTv = TextView(activity).apply {
      text = "✅"
      textSize = 36f
      gravity = android.view.Gravity.CENTER
    }
    root.addView(iconTv)

    val tTv = TextView(activity).apply {
      text = "Excel File Ready!"
      textSize = 18f
      setTextColor(Color.parseColor("#1B5E20"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
      setPadding(0, 6, 0, 4)
    }
    root.addView(tTv)

    val iTv = TextView(activity).apply {
      val loc = if (savedPath != null) "\nSaved to: $savedPath" else ""
      text = "Generated real Microsoft Excel file with $rowCount Sadhna records.$loc"
      textSize = 13f
      setTextColor(Color.parseColor("#424242"))
      gravity = android.view.Gravity.CENTER
      setPadding(0, 0, 0, 14)
    }
    root.addView(iTv)

    val btnOpen = Button(activity).apply {
      text = "📂 OPEN IN EXCEL / SHEETS"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#1976D2"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        d.dismiss()
        ExcelExportHelper.openExcelFile(activity, file)
      }
    }
    root.addView(btnOpen)

    val btnShare = Button(activity).apply {
      text = "📤 SHARE FILE (WhatsApp, Email...)"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#00796B"))
      val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        d.dismiss()
        ExcelExportHelper.shareExcelFile(activity, file, "ISKCON Sadhna Report")
      }
    }
    root.addView(btnShare)

    val btnDone = Button(activity).apply {
      text = "DONE"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#EEEEEE"))
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
      setOnClickListener { d.dismiss() }
    }
    root.addView(btnDone)

    d.setContentView(root)
    d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    d.show()
  }
}
