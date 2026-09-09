package com.example

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

class AdminDashboardActivity : ComponentActivity() {

  // Header Views
  private lateinit var tvAdminWelcome: TextView
  private lateinit var btnExportExcel: Button
  private lateinit var btnManageAdmins: Button
  private lateinit var btnLogout: Button
  private lateinit var pbAdminLoading: ProgressBar

  // Tab Buttons
  private lateinit var btnTabMentors: Button
  private lateinit var btnTabSadhna: Button

  // Section Layouts
  private lateinit var layoutSectionMentors: LinearLayout
  private lateinit var layoutSectionSadhna: LinearLayout

  // Section 1: Mentors Views
  private lateinit var tvMentorsHeader: TextView
  private lateinit var tvMentorsSubCount: TextView
  private lateinit var btnAddNewMentor: Button
  private lateinit var btnAddNewMentee: Button
  private lateinit var layoutAdminMentorsList: LinearLayout

  // Section 2: Sadhna Views
  private lateinit var spinnerFilterMentor: Spinner
  private lateinit var spinnerFilterMentee: Spinner
  private lateinit var btnFilterDate: Button
  private lateinit var btnClearFilter: Button
  private lateinit var tvAdminSadhnaHeader: TextView
  private lateinit var btnExportSadhnaSection: Button
  private lateinit var layoutAdminSadhnaList: LinearLayout

  // State & Data
  private var usersListener: ListenerRegistration? = null
  private var sadhnaListener: ListenerRegistration? = null

  private val allMentors = mutableListOf<UserProfile>()
  private val allMentees = mutableListOf<UserProfile>()
  private val allAdmins = mutableListOf<UserProfile>()
  private val allSadhnaRecords = mutableListOf<SadhnaSubmission>()

  private var selectedMentorFilterId: String? = null
  private var selectedMenteeFilterId: String? = null
  private var selectedDateFilter: String? = null
  private var activeSection = 1 // 1: Counsellors, 2: Sadhna

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_admin_dashboard)

    initViews()
    setupTabNavigation()
    setupFilterListeners()

    val profile = SessionManager.getSavedProfile(this)
    val name = profile?.name ?: "Administrator"
    tvAdminWelcome.text = "Hare Krishna, HG $name"

    btnLogout.setOnClickListener {
      performLogout()
    }

    btnExportExcel.setOnClickListener {
      showExportExcelDialog()
    }

    btnManageAdmins.setOnClickListener {
      showManageAdminsDialog()
    }

    btnAddNewMentor.setOnClickListener {
      showAddMentorDialog()
    }

    btnAddNewMentee.setOnClickListener {
      showAddMenteeDialog()
    }

    startRealtimeListeners()

    SadhnaReminderManager.scheduleDailyReminder(this)
    SadhnaReminderManager.requestNotificationPermissionIfNeeded(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    usersListener?.remove()
    usersListener = null
    sadhnaListener?.remove()
    sadhnaListener = null
  }

  private fun initViews() {
    tvAdminWelcome = findViewById(R.id.tvAdminWelcome)
    btnExportExcel = findViewById(R.id.btnExportExcel)
    btnManageAdmins = findViewById(R.id.btnManageAdmins)
    btnLogout = findViewById(R.id.btnLogout)
    pbAdminLoading = findViewById(R.id.pbAdminLoading)

    btnTabMentors = findViewById(R.id.btnTabMentors)
    btnTabSadhna = findViewById(R.id.btnTabSadhna)

    layoutSectionMentors = findViewById(R.id.layoutSectionMentors)
    layoutSectionSadhna = findViewById(R.id.layoutSectionSadhna)

    tvMentorsHeader = findViewById(R.id.tvMentorsHeader)
    tvMentorsSubCount = findViewById(R.id.tvMentorsSubCount)
    btnAddNewMentor = findViewById(R.id.btnAddNewMentor)
    btnAddNewMentee = findViewById(R.id.btnAddNewMentee)
    layoutAdminMentorsList = findViewById(R.id.layoutAdminMentorsList)

    btnAddNewMentor.setOnClickListener {
      showAddMentorDialog()
    }

    btnAddNewMentee.setOnClickListener {
      showAddMenteeDialog()
    }

    spinnerFilterMentor = findViewById(R.id.spinnerFilterMentor)
    spinnerFilterMentee = findViewById(R.id.spinnerFilterMentee)
    btnFilterDate = findViewById(R.id.btnFilterDate)
    btnClearFilter = findViewById(R.id.btnClearFilter)
    tvAdminSadhnaHeader = findViewById(R.id.tvAdminSadhnaHeader)
    btnExportSadhnaSection = findViewById(R.id.btnExportSadhnaSection)
    layoutAdminSadhnaList = findViewById(R.id.layoutAdminSadhnaList)

    btnExportSadhnaSection.setOnClickListener {
      showExportExcelDialog()
    }

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }
  }

  private fun setupTabNavigation() {
    btnTabMentors.setOnClickListener {
      switchSection(1)
    }
    btnTabSadhna.setOnClickListener {
      switchSection(2)
    }
  }

  private fun switchSection(section: Int) {
    activeSection = section

    val colorActive = Color.parseColor("#E65100")
    val colorInactive = Color.parseColor("#FFFFFF")
    val textActive = Color.parseColor("#FFFFFF")
    val textInactive = Color.parseColor("#424242")

    // Reset button states
    btnTabMentors.backgroundTintList = ColorStateList.valueOf(if (section == 1) colorActive else colorInactive)
    btnTabMentors.setTextColor(if (section == 1) textActive else textInactive)

    btnTabSadhna.backgroundTintList = ColorStateList.valueOf(if (section == 2) colorActive else colorInactive)
    btnTabSadhna.setTextColor(if (section == 2) textActive else textInactive)

    // Visibility
    layoutSectionMentors.visibility = if (section == 1) View.VISIBLE else View.GONE
    layoutSectionSadhna.visibility = if (section == 2) View.VISIBLE else View.GONE
  }

  private fun setupFilterListeners() {
    btnFilterDate.setOnClickListener {
      val calendar = Calendar.getInstance()
      val datePicker = DatePickerDialog(
        this,
        { _, year, month, dayOfMonth ->
          val formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
          selectedDateFilter = formattedDate
          btnFilterDate.text = "📅 Date: $formattedDate (Tap to change)"
          applySadhnaFilters()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
      )
      datePicker.show()
    }

    btnClearFilter.setOnClickListener {
      selectedDateFilter = null
      selectedMentorFilterId = null
      selectedMenteeFilterId = null
      btnFilterDate.text = "📅 Date: All Dates"
      spinnerFilterMentor.setSelection(0)
      spinnerFilterMentee.setSelection(0)
      applySadhnaFilters()
    }

    spinnerFilterMentor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        selectedMentorFilterId = if (position == 0) null else {
          allMentors.getOrNull(position - 1)?.id
        }
        applySadhnaFilters()
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    spinnerFilterMentee.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        selectedMenteeFilterId = if (position == 0) null else {
          allMentees.getOrNull(position - 1)?.id
        }
        applySadhnaFilters()
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
  }

  private fun startRealtimeListeners() {
    pbAdminLoading.visibility = View.VISIBLE

    // 1. Listen to all users
    usersListener?.remove()
    usersListener = SessionManager.listenToAllUsers(
      onUpdate = { users ->
        pbAdminLoading.visibility = View.GONE
        allMentors.clear()
        allMentees.clear()
        allAdmins.clear()

        for (u in users) {
          when (u.role.lowercase(Locale.getDefault())) {
            "mentor" -> allMentors.add(u)
            "mentee" -> allMentees.add(u)
            "admin" -> allAdmins.add(u)
            else -> allMentees.add(u)
          }
        }

        allMentors.sortBy { it.name }
        allMentees.sortBy { it.name }
        allAdmins.sortBy { it.name }

        renderMentorsSection()
        updateFilterSpinners()
        applySadhnaFilters()

        // Sync active counsellors to public mentor_profiles collection in background
        if (allMentors.isNotEmpty()) {
          SessionManager.syncMentorsToPublicProfiles(allMentors)
        }
      },
      onError = { e ->
        pbAdminLoading.visibility = View.GONE
        Toast.makeText(this, "Error syncing users: ${SessionManager.formatErrorMessage(e)}", Toast.LENGTH_SHORT).show()
      }
    )

    // 2. Listen to all Sadhna records
    sadhnaListener?.remove()
    sadhnaListener = SessionManager.listenToAllSadhnaRecords(
      onUpdate = { records ->
        allSadhnaRecords.clear()
        allSadhnaRecords.addAll(records.sortedByDescending { it.date })
        applySadhnaFilters()
      },
      onError = { e ->
        Toast.makeText(this, "Error loading sadhna records: ${SessionManager.formatErrorMessage(e)}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  private fun updateFilterSpinners() {
    // Mentor filter spinner
    val mentorOptions = mutableListOf("All Counsellors (${allMentors.size})")
    mentorOptions.addAll(allMentors.map { "HG ${it.name}" })
    val mentorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mentorOptions)
    spinnerFilterMentor.adapter = mentorAdapter

    // Mentee filter spinner
    val menteeOptions = mutableListOf("All Counsellees (${allMentees.size})")
    menteeOptions.addAll(allMentees.map { it.name })
    val menteeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, menteeOptions)
    spinnerFilterMentee.adapter = menteeAdapter
  }

  // =========================================================================
  // 1. MENTOR DATA SECTION
  // =========================================================================
  private fun renderMentorsSection() {
    layoutAdminMentorsList.removeAllViews()
    tvMentorsHeader.text = "Counsellors Directory (${allMentors.size})"
    tvMentorsSubCount.text = "Total ${allMentors.size} counsellors registered"

    if (allMentors.isEmpty()) {
      val emptyCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 2f
        setPadding(24, 24, 24, 24)
        gravity = android.view.Gravity.CENTER
      }
      val emptyTv = TextView(this).apply {
        text = "No counsellors registered yet.\nTap '+ ADD COUNSELLOR' to register a new counsellor."
        gravity = android.view.Gravity.CENTER
        setTextColor(Color.parseColor("#757575"))
        textSize = 14f
      }
      emptyCard.addView(emptyTv)
      layoutAdminMentorsList.addView(emptyCard)
      return
    }

    for (mentor in allMentors) {
      val assignedMentees = allMentees.filter { it.mentorId == mentor.id }
      val card = createMentorCard(mentor, assignedMentees)
      layoutAdminMentorsList.addView(card)
    }
  }

  private fun createMentorCard(mentor: UserProfile, assignedMentees: List<UserProfile>): View {
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
        openMentorSubDashboard(mentor)
      }
    }

    // Top Header: Mentor Name & Mentees Badge
    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val nameTv = TextView(this).apply {
      text = "🎓 HG ${mentor.name}"
      textSize = 16f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val countBadge = TextView(this).apply {
      text = "👥 ${assignedMentees.size} Counsellees"
      textSize = 12f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    topRow.addView(nameTv)
    topRow.addView(countBadge)
    container.addView(topRow)

    // Email
    val emailTv = TextView(this).apply {
      text = "✉️ ${mentor.email}"
      textSize = 13f
      setTextColor(Color.parseColor("#616161"))
      setPadding(0, 4, 0, 0)
    }
    container.addView(emailTv)

    // Action Buttons Row: [ Sub-Dashboard ] [ Edit ] [ Delete ]
    val actionsRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 12
      }
    }

    val btnSubDash = Button(this).apply {
      text = "View Counsellees (${assignedMentees.size}) ➔"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#E65100"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f).apply {
        rightMargin = 4
      }
      layoutParams = p
      setOnClickListener {
        openMentorSubDashboard(mentor)
      }
    }

    val btnEdit = Button(this).apply {
      text = "Edit"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#00695C"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f).apply {
        leftMargin = 4
        rightMargin = 4
      }
      layoutParams = p
      setOnClickListener {
        showEditUserDialog(mentor, "mentor")
      }
    }

    val btnDelete = Button(this).apply {
      text = "Delete"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#D32F2F"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f).apply {
        leftMargin = 4
      }
      layoutParams = p
      setOnClickListener {
        confirmDeleteMentor(mentor, assignedMentees.size)
      }
    }

    actionsRow.addView(btnSubDash)
    actionsRow.addView(btnEdit)
    actionsRow.addView(btnDelete)
    container.addView(actionsRow)

    return container
  }

  private fun openMentorSubDashboard(mentor: UserProfile) {
    val intent = Intent(this, MentorSubDashboardActivity::class.java).apply {
      putExtra(MentorSubDashboardActivity.EXTRA_MENTOR_ID, mentor.id)
      putExtra(MentorSubDashboardActivity.EXTRA_MENTOR_NAME, mentor.name)
      putExtra(MentorSubDashboardActivity.EXTRA_MENTOR_EMAIL, mentor.email)
    }
    startActivity(intent)
  }

  private fun confirmDeleteMentor(mentor: UserProfile, menteeCount: Int) {
    val warningMsg = if (menteeCount > 0) {
      "⚠️ WARNING:\nHG ${mentor.name} currently has $menteeCount assigned devotee(s)!\n\nDeleting this counsellor will safely unassign their counsellees (the devotees' user accounts and historical Sadhna records will stay completely safe).\n\nDo you want to permanently delete this counsellor?"
    } else {
      "Are you sure you want to delete counsellor HG ${mentor.name}?\n\nThis action cannot be undone."
    }

    AlertDialog.Builder(this)
      .setTitle("Delete Counsellor")
      .setMessage(warningMsg)
      .setPositiveButton("Delete Counsellor") { _, _ ->
        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.deleteMentor(mentor.id)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Counsellor HG ${mentor.name} removed successfully.", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to delete: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  // =========================================================================
  // 2. SADHNA SECTION (ALL SUBMISSIONS WITH FILTERS)
  // =========================================================================
  private fun applySadhnaFilters() {
    val mentorMap = allMentors.associateBy { it.id }
    val menteeMap = allMentees.associateBy { it.id }

    val filtered = allSadhnaRecords.filter { record ->
      var matches = true

      // Filter by Mentor
      if (selectedMentorFilterId != null) {
        val mentee = menteeMap[record.menteeId]
        val mId = record.mentorId ?: mentee?.mentorId
        if (mId != selectedMentorFilterId) {
          matches = false
        }
      }

      // Filter by Mentee
      if (matches && selectedMenteeFilterId != null) {
        if (record.menteeId != selectedMenteeFilterId) {
          matches = false
        }
      }

      // Filter by Date
      if (matches && selectedDateFilter != null) {
        if (record.date != selectedDateFilter) {
          matches = false
        }
      }

      matches
    }

    layoutAdminSadhnaList.removeAllViews()
    tvAdminSadhnaHeader.text = "Sadhna Records (${filtered.size} of ${allSadhnaRecords.size})"

    if (filtered.isEmpty()) {
      val emptyCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 2f
        setPadding(24, 24, 24, 24)
        gravity = android.view.Gravity.CENTER
      }
      val emptyTv = TextView(this).apply {
        text = "No Sadhna records found matching your active filter criteria."
        gravity = android.view.Gravity.CENTER
        setTextColor(Color.parseColor("#757575"))
        textSize = 14f
      }
      emptyCard.addView(emptyTv)
      layoutAdminSadhnaList.addView(emptyCard)
      return
    }

    for (record in filtered.take(100)) {
      val mentee = menteeMap[record.menteeId]
      val mentorName = (record.mentorId ?: mentee?.mentorId)?.let { mentorMap[it]?.name } ?: "Unassigned"
      val card = createAdminSadhnaRecordCard(record, mentorName)
      layoutAdminSadhnaList.addView(card)
    }
  }

  private fun createAdminSadhnaRecordCard(record: SadhnaSubmission, mentorName: String): View {
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
        SadhnaDetailDialog.show(this@AdminDashboardActivity, record)
      }
    }

    // Top Header: Date, Devotee, Japa Badge
    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val devoteeName = record.devoteeName?.ifEmpty { "Devotee" } ?: "Devotee"
    val headerTv = TextView(this).apply {
      text = "📅 ${record.date}  •  👤 $devoteeName"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val japaTv = TextView(this).apply {
      text = "📿 ${record.japaRounds} Rnds"
      textSize = 13f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    topRow.addView(headerTv)
    topRow.addView(japaTv)
    container.addView(topRow)

    // Mentor Line & Wake up
    val mentorAndWakeTv = TextView(this).apply {
      text = "🪷 Counsellor: HG $mentorName  |  ⏰ Wake Up: ${record.wakeUpTime.ifEmpty { "--:--" }}"
      textSize = 13f
      setTextColor(Color.parseColor("#424242"))
      setPadding(0, 4, 0, 0)
    }
    container.addView(mentorAndWakeTv)

    // Programs & Reading
    val programsText = if (record.spiritualActivities.isNotEmpty()) {
      "🪷 Programs: ${record.spiritualActivities.joinToString(", ")}"
    } else {
      "🪷 Programs: None attended"
    }
    val programsTv = TextView(this).apply {
      text = programsText
      textSize = 12f
      setTextColor(Color.parseColor("#00695C"))
      setPadding(0, 2, 0, 0)
    }
    container.addView(programsTv)

    val readingText = buildString {
      if (record.bookReadingSubject.isNotEmpty()) append("📖 ${record.bookReadingSubject} ")
      if (record.hearingSubject.isNotEmpty()) append("🎧 ${record.hearingSubject} ")
      if (record.sevaCompleted) append("🌺 Seva Done")
    }
    if (readingText.isNotEmpty()) {
      val readingTv = TextView(this).apply {
        text = readingText
        textSize = 12f
        setTextColor(Color.parseColor("#616161"))
        setPadding(0, 2, 0, 0)
      }
      container.addView(readingTv)
    }

    // Action Buttons: [ View Details ] [ Delete ]
    val actionRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 10
      }
    }

    val btnView = Button(this).apply {
      text = "🔍 View Complete Sadhna Details ➔"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#E65100"))
      setTextColor(Color.WHITE)
      val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        rightMargin = 4
      }
      layoutParams = p
      setOnClickListener {
        SadhnaDetailDialog.show(this@AdminDashboardActivity, record)
      }
    }

    val btnDel = Button(this).apply {
      text = "🗑️ Delete"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#D32F2F"))
      setTextColor(Color.WHITE)
      setOnClickListener {
        confirmDeleteSadhna(record)
      }
    }

    actionRow.addView(btnView)
    actionRow.addView(btnDel)
    container.addView(actionRow)

    return container
  }

  private fun confirmDeleteSadhna(record: SadhnaSubmission) {
    AlertDialog.Builder(this)
      .setTitle("Delete Sadhna Record")
      .setMessage("Are you sure you want to delete the Sadhna record for ${record.devoteeName ?: "Devotee"} on ${record.date}?")
      .setPositiveButton("Delete") { _, _ ->
        val recordId = record.id
        if (recordId != null) {
          pbAdminLoading.visibility = View.VISIBLE
          lifecycleScope.launch {
            val result = SessionManager.deleteSadhnaRecord(recordId)
            pbAdminLoading.visibility = View.GONE
            if (result.isSuccess) {
              Toast.makeText(this@AdminDashboardActivity, "Sadhna record deleted.", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(this@AdminDashboardActivity, "Failed to delete: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  // =========================================================================
  // COMPLETE MENTEE DETAILS & SADHNA HISTORY DIALOG
  // =========================================================================
  private fun showMenteeCompleteDetailsDialog(mentee: UserProfile, currentMentorName: String) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = ScrollView(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(28, 24, 28, 28)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    // Title
    val titleTv = TextView(this).apply {
      text = "Devotee Profile & Sadhna"
      textSize = 20f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
    }
    root.addView(titleTv)

    // Mentee Info Card
    val infoCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(20, 16, 20, 16)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 16, 0, 14)
      }
      layoutParams = p
    }

    val nameTv = TextView(this).apply {
      text = "👤 ${mentee.name}"
      textSize = 17f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }
    val emailTv = TextView(this).apply {
      text = "✉️ ${mentee.email}"
      textSize = 13f
      setTextColor(Color.parseColor("#616161"))
      setPadding(0, 2, 0, 0)
    }
    val mentorTv = TextView(this).apply {
      text = "🪷 Assigned Counsellor: HG $currentMentorName"
      textSize = 13f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 4, 0, 0)
    }
    val uidTv = TextView(this).apply {
      text = "🆔 UID: ${mentee.id}"
      textSize = 11f
      setTextColor(Color.parseColor("#9E9E9E"))
      setPadding(0, 2, 0, 0)
    }

    infoCard.addView(nameTv)
    infoCard.addView(emailTv)
    infoCard.addView(mentorTv)
    infoCard.addView(uidTv)
    root.addView(infoCard)

    // Sadhna Stats Card
    val devoteeRecords = allSadhnaRecords.filter { it.menteeId == mentee.id }.sortedByDescending { it.date }
    val totalEntries = devoteeRecords.size
    val avgRounds = if (totalEntries > 0) String.format(Locale.getDefault(), "%.1f", devoteeRecords.map { it.japaRounds }.average()) else "0"
    val latestDate = if (totalEntries > 0) devoteeRecords.first().date else "None"

    val statsCard = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(16, 14, 16, 14)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 16)
      }
      layoutParams = p
    }

    fun makeStatItem(value: String, label: String, colorHex: String): View {
      return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = android.view.Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val vTv = TextView(this@AdminDashboardActivity).apply {
          text = value
          textSize = 18f
          setTextColor(Color.parseColor(colorHex))
          setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val lTv = TextView(this@AdminDashboardActivity).apply {
          text = label
          textSize = 11f
          setTextColor(Color.parseColor("#757575"))
        }
        addView(vTv)
        addView(lTv)
      }
    }

    statsCard.addView(makeStatItem("$totalEntries", "Total Entries", "#E65100"))
    statsCard.addView(makeStatItem(avgRounds, "Avg Rounds", "#00695C"))
    statsCard.addView(makeStatItem(latestDate, "Latest Date", "#283593"))
    root.addView(statsCard)

    // Sadhna History Header
    val historyHeaderTv = TextView(this).apply {
      text = "Sadhna Submissions History ($totalEntries)"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 8)
    }
    root.addView(historyHeaderTv)

    // List of records
    if (devoteeRecords.isEmpty()) {
      val emptyTv = TextView(this).apply {
        text = "No Sadhna records submitted yet by this devotee."
        textSize = 13f
        setTextColor(Color.parseColor("#757575"))
        setPadding(0, 8, 0, 12)
      }
      root.addView(emptyTv)
    } else {
      for (rec in devoteeRecords) {
        val recItem = LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          setBackgroundColor(Color.WHITE)
          elevation = 2f
          setPadding(14, 12, 14, 12)
          val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply {
            setMargins(0, 0, 0, 8)
          }
          layoutParams = p
          isClickable = true
          isFocusable = true
          setOnClickListener {
            SadhnaDetailDialog.show(this@AdminDashboardActivity, rec)
          }
        }

        val row1 = LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
        }
        val dTv = TextView(this).apply {
          text = "📅 ${rec.date}"
          textSize = 14f
          setTextColor(Color.parseColor("#111111"))
          setTypeface(null, android.graphics.Typeface.BOLD)
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val jTv = TextView(this).apply {
          text = "📿 ${rec.japaRounds} Rnds"
          textSize = 13f
          setTextColor(Color.parseColor("#E65100"))
          setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row1.addView(dTv)
        row1.addView(jTv)
        recItem.addView(row1)

        val detailHintTv = TextView(this).apply {
          text = "⏰ Wake: ${rec.wakeUpTime.ifEmpty { "--:--" }} • Tap to inspect full report ➔"
          textSize = 11f
          setTextColor(Color.parseColor("#00695C"))
          setPadding(0, 4, 0, 0)
        }
        recItem.addView(detailHintTv)
        root.addView(recItem)
      }
    }

    // Export Single Mentee Excel Button
    val btnExportMentee = Button(this).apply {
      text = "📊 EXPORT ${mentee.name.uppercase()}'S EXCEL (.xlsx)"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#2E7D32"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 16
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
        val records = allSadhnaRecords.filter { it.menteeId == mentee.id }.sortedByDescending { it.date }
        if (records.isEmpty()) {
          Toast.makeText(this@AdminDashboardActivity, "No sadhna submissions found for ${mentee.name}", Toast.LENGTH_SHORT).show()
        } else {
          exportExcelData(records, "Sadhna_${mentee.name}", "${mentee.name}'s Sadhna Data")
        }
      }
    }
    root.addView(btnExportMentee)

    // Close Button
    val btnClose = Button(this).apply {
      text = "CLOSE"
      textSize = 14f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#E65100"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 10
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnClose)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  // =========================================================================
  // USER ACTIONS: ADD / EDIT / DELETE / REASSIGN
  // =========================================================================
  private fun showAddMentorDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
    }

    val etName = EditText(this).apply {
      hint = "Counsellor Full Name (e.g. HG Damodar Das)"
    }
    val etEmail = EditText(this).apply {
      hint = "Counsellor Email Address"
      inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }
    val etPassword = EditText(this).apply {
      hint = "Initial Password (min 6 chars)"
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    layout.addView(etName)
    layout.addView(etEmail)
    layout.addView(etPassword)

    AlertDialog.Builder(this)
      .setTitle("Add New Counsellor")
      .setView(layout)
      .setPositiveButton("Create Counsellor") { _, _ ->
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.length < 6) {
          Toast.makeText(this, "Please provide valid counsellor name, email, and 6+ char password.", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }

        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.addMentor(this@AdminDashboardActivity, name, email, password)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Counsellor HG $name created successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to create counsellor: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showAddMenteeDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
    }

    val etName = EditText(this).apply {
      hint = "Devotee Full Name"
    }
    val etEmail = EditText(this).apply {
      hint = "Devotee Email Address"
      inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }
    val etPassword = EditText(this).apply {
      hint = "Initial Password (min 6 chars)"
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    val mentorSpinnerLabel = TextView(this).apply {
      text = "Select Assigned Counsellor:"
      setPadding(0, 12, 0, 4)
      setTextColor(Color.parseColor("#424242"))
      textSize = 12f
    }

    val mentorOptions = mutableListOf("Unassigned (None)")
    mentorOptions.addAll(allMentors.map { "HG ${it.name}" })
    val spinner = Spinner(this).apply {
      adapter = ArrayAdapter(this@AdminDashboardActivity, android.R.layout.simple_spinner_dropdown_item, mentorOptions)
    }

    layout.addView(etName)
    layout.addView(etEmail)
    layout.addView(etPassword)
    layout.addView(mentorSpinnerLabel)
    layout.addView(spinner)

    AlertDialog.Builder(this)
      .setTitle("Add New Devotee (Counsellee)")
      .setView(layout)
      .setPositiveButton("Create Counsellee") { _, _ ->
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.length < 6) {
          Toast.makeText(this, "Please enter name, email, and 6+ char password.", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }

        val selectedPos = spinner.selectedItemPosition
        val mentorId = if (selectedPos > 0) allMentors.getOrNull(selectedPos - 1)?.id else null

        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.addMentee(this@AdminDashboardActivity, name, email, password, mentorId)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Devotee $name created successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to create: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showEditUserDialog(user: UserProfile, role: String) {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
    }

    val etName = EditText(this).apply {
      hint = "Full Name"
      setText(user.name)
    }
    val etEmail = EditText(this).apply {
      hint = "Email Address"
      inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
      setText(user.email)
    }

    layout.addView(etName)
    layout.addView(etEmail)

    val roleLabel = if (role == "mentor") "Counsellor" else "Devotee"

    AlertDialog.Builder(this)
      .setTitle("Edit $roleLabel Information")
      .setView(layout)
      .setPositiveButton("Save Changes") { _, _ ->
        val newName = etName.text.toString().trim()
        val newEmail = etEmail.text.toString().trim()

        if (newName.isEmpty() || newEmail.isEmpty()) {
          Toast.makeText(this, "Name and email cannot be empty.", Toast.LENGTH_SHORT).show()
          return@setPositiveButton
        }

        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.updateUserProfile(user.id, newName, newEmail, role, user.mentorId)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to update: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showReassignMentorDialog(mentee: UserProfile) {
    if (allMentors.isEmpty()) {
      Toast.makeText(this, "No counsellors available in system to assign.", Toast.LENGTH_SHORT).show()
      return
    }

    val mentorOptions = mutableListOf("Unassigned (None)")
    mentorOptions.addAll(allMentors.map { "HG ${it.name}" })

    val spinner = Spinner(this).apply {
      setPadding(16, 16, 16, 16)
      adapter = ArrayAdapter(this@AdminDashboardActivity, android.R.layout.simple_spinner_dropdown_item, mentorOptions)
      val currentIndex = allMentors.indexOfFirst { it.id == mentee.mentorId }
      if (currentIndex >= 0) {
        setSelection(currentIndex + 1)
      } else {
        setSelection(0)
      }
    }

    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
      addView(spinner)
    }

    AlertDialog.Builder(this)
      .setTitle("Reassign Counsellor for ${mentee.name}")
      .setView(layout)
      .setPositiveButton("Save Assignment") { _, _ ->
        val selectedPos = spinner.selectedItemPosition
        val newMentorId = if (selectedPos == 0) null else allMentors[selectedPos - 1].id

        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.reassignMenteeMentor(mentee.id, newMentorId)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Counsellor assigned successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to assign: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun confirmDeleteMentee(mentee: UserProfile) {
    AlertDialog.Builder(this)
      .setTitle("Delete Counsellee")
      .setMessage("Are you sure you want to delete ${mentee.name}?\n\nThis will remove their user account. (Their historical submitted Sadhna records will stay preserved in the database).")
      .setPositiveButton("Delete") { _, _ ->
        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.deleteMentee(mentee.id)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Devotee ${mentee.name} removed.", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to delete: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  // =========================================================================
  // ADMIN MANAGEMENT MODAL
  // =========================================================================
  private fun showManageAdminsDialog() {
    val currentAdminProfile = SessionManager.getSavedProfile(this)
    val currentAdminId = currentAdminProfile?.id ?: ""

    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = ScrollView(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(28, 24, 28, 28)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val headerRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
    }

    val titleTv = TextView(this).apply {
      text = "👑 Manage Admins (${allAdmins.size})"
      textSize = 18f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val btnAddAdmin = Button(this).apply {
      text = "+ ADD"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#4527A0"))
      setTextColor(Color.WHITE)
      setOnClickListener {
        dialog.dismiss()
        showAddAdminDialog()
      }
    }

    headerRow.addView(titleTv)
    headerRow.addView(btnAddAdmin)
    root.addView(headerRow)

    val subtitleTv = TextView(this).apply {
      text = "Administrators have full access to manage counsellors, devotees, and records."
      textSize = 12f
      setTextColor(Color.parseColor("#757575"))
      setPadding(0, 4, 0, 14)
    }
    root.addView(subtitleTv)

    for (admin in allAdmins) {
      val isSelf = (admin.id == currentAdminId)

      val adminCard = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 2f
        setPadding(16, 12, 16, 12)
        val p = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
          setMargins(0, 0, 0, 8)
        }
        layoutParams = p
      }

      val textLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

      val aNameTv = TextView(this).apply {
        text = if (isSelf) "👑 ${admin.name} (You)" else "👑 ${admin.name}"
        textSize = 14f
        setTextColor(Color.parseColor("#111111"))
        setTypeface(null, android.graphics.Typeface.BOLD)
      }
      val aEmailTv = TextView(this).apply {
        text = "✉️ ${admin.email}"
        textSize = 12f
        setTextColor(Color.parseColor("#616161"))
      }
      textLayout.addView(aNameTv)
      textLayout.addView(aEmailTv)
      adminCard.addView(textLayout)

      if (!isSelf) {
        val btnDel = Button(this).apply {
          text = "Delete"
          textSize = 11f
          minHeight = 34
          setBackgroundColor(Color.parseColor("#D32F2F"))
          setTextColor(Color.WHITE)
          setOnClickListener {
            dialog.dismiss()
            confirmDeleteAdmin(admin)
          }
        }
        adminCard.addView(btnDel)
      } else {
        val selfBadge = TextView(this).apply {
          text = "Protected"
          textSize = 11f
          setTextColor(Color.parseColor("#2E7D32"))
          setTypeface(null, android.graphics.Typeface.BOLD)
        }
        adminCard.addView(selfBadge)
      }

      root.addView(adminCard)
    }

    val btnClose = Button(this).apply {
      text = "CLOSE"
      textSize = 13f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#E65100"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 16
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnClose)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  private fun showAddAdminDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
    }

    val etName = EditText(this).apply {
      hint = "Administrator Full Name"
    }
    val etEmail = EditText(this).apply {
      hint = "Administrator Email Address"
      inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }
    val etPassword = EditText(this).apply {
      hint = "Initial Password (min 6 chars)"
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    layout.addView(etName)
    layout.addView(etEmail)
    layout.addView(etPassword)

    AlertDialog.Builder(this)
      .setTitle("Add New Administrator")
      .setView(layout)
      .setPositiveButton("Create Admin") { _, _ ->
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.length < 6) {
          Toast.makeText(this, "Please enter admin name, email, and 6+ char password.", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }

        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.addAdmin(this@AdminDashboardActivity, name, email, password)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Administrator $name added successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to add admin: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun confirmDeleteAdmin(admin: UserProfile) {
    AlertDialog.Builder(this)
      .setTitle("Delete Administrator")
      .setMessage("Are you sure you want to remove administrator privileges for ${admin.name} (${admin.email})?")
      .setPositiveButton("Delete Admin") { _, _ ->
        pbAdminLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.deleteAdmin(admin.id)
          pbAdminLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@AdminDashboardActivity, "Administrator removed.", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@AdminDashboardActivity, "Failed to delete: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun performLogout() {
    SessionManager.clearSession(this)
    val intent = Intent(this, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    finish()
  }

  private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

  // =========================================================================
  // 4. EXCEL EXPORT SYSTEM (ADMIN-ONLY)
  // =========================================================================
  fun showExportExcelDialog(preselectedMentee: UserProfile? = null) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = ScrollView(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(28, 24, 28, 28)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    // --- TITLE HEADER ---
    val headerLayout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER_HORIZONTAL
      setPadding(0, 0, 0, 16)
    }

    val titleTv = TextView(this).apply {
      text = "📊 Export Sadhna Data to Excel"
      textSize = 19f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
    }

    val subtitleTv = TextView(this).apply {
      text = "Generate professional .xlsx spreadsheets with frozen headers, column filters, and full spiritual activity columns."
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      gravity = android.view.Gravity.CENTER
      setPadding(0, 4, 0, 0)
    }

    headerLayout.addView(titleTv)
    headerLayout.addView(subtitleTv)
    root.addView(headerLayout)

    // Mode state: 0 = All Devotees, 1 = Specific Devotee, 2 = My Own Sadhna (Admin)
    val adminProfile = SessionManager.getSavedProfile(this)
    val adminUid = adminProfile?.id ?: ""
    val adminName = adminProfile?.name ?: "Admin"

    var exportScope = if (preselectedMentee != null) 1 else 0
    var selectedMenteeId: String? = preselectedMentee?.id
    var exportDateFilter: String? = selectedDateFilter // Inherit active date filter if any

    // --- SCOPE TOGGLE BUTTONS ---
    val scopeCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 14, 16, 14)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 14
      }
      layoutParams = p
    }

    val scopeLabel = TextView(this).apply {
      text = "Choose Export Scope:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 8)
    }
    scopeCard.addView(scopeLabel)

    val toggleLayout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val topToggleRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 6
      }
    }

    val btnScopeAll = Button(this).apply {
      text = "🌐 All Users (${allSadhnaRecords.size})"
      textSize = 11f
      layoutParams = LinearLayout.LayoutParams(0, 40.dpToPx(), 1f).apply { marginEnd = 4 }
    }

    val btnScopeMySadhna = Button(this).apply {
      val myCount = allSadhnaRecords.count { it.menteeId == adminUid }
      text = "📿 My Own Sadhna ($myCount)"
      textSize = 11f
      layoutParams = LinearLayout.LayoutParams(0, 40.dpToPx(), 1f).apply { marginStart = 4 }
    }

    topToggleRow.addView(btnScopeAll)
    topToggleRow.addView(btnScopeMySadhna)
    toggleLayout.addView(topToggleRow)

    val btnScopeSpecific = Button(this).apply {
      text = "👤 Specific Devotee / Counsellee Filter"
      textSize = 11f
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        40.dpToPx()
      )
    }
    toggleLayout.addView(btnScopeSpecific)
    scopeCard.addView(toggleLayout)
    root.addView(scopeCard)

    // --- SPECIFIC DEVOTEE FILTER CARD ---
    val devoteeCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 14, 16, 14)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 14
      }
      layoutParams = p
      visibility = if (exportScope == 1) View.VISIBLE else View.GONE
    }

    val devoteeLabel = TextView(this).apply {
      text = "Search & Select Devotee:"
      textSize = 13f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 0, 0, 6)
    }
    devoteeCard.addView(devoteeLabel)

    val etSearchDevotee = EditText(this).apply {
      hint = "🔍 Type devotee name or email..."
      textSize = 13f
      setBackgroundColor(Color.parseColor("#F5F5F5"))
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 8
      }
      layoutParams = p
    }
    devoteeCard.addView(etSearchDevotee)

    val spinnerMenteeSelect = Spinner(this).apply {
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        42.dpToPx()
      )
      setBackgroundResource(android.R.drawable.btn_dropdown)
    }
    devoteeCard.addView(spinnerMenteeSelect)
    root.addView(devoteeCard)

    // --- DATE FILTER CARD ---
    val dateCard = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 2f
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 14
      }
      layoutParams = p
    }

    val btnPickDate = Button(this).apply {
      text = if (exportDateFilter != null) "📅 Date: $exportDateFilter" else "📅 Date: All Dates"
      textSize = 11f
      setBackgroundColor(Color.parseColor("#00695C"))
      setTextColor(Color.WHITE)
      layoutParams = LinearLayout.LayoutParams(0, 38.dpToPx(), 1f).apply { marginEnd = 6 }
    }

    val btnResetDate = Button(this).apply {
      text = "Clear Date"
      textSize = 11f
      setBackgroundColor(Color.parseColor("#757575"))
      setTextColor(Color.WHITE)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        38.dpToPx()
      )
    }

    dateCard.addView(btnPickDate)
    dateCard.addView(btnResetDate)
    root.addView(dateCard)

    // --- LIVE SUMMARY / PREVIEW CARD ---
    val previewCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.parseColor("#E8F5E9"))
      setPadding(16, 12, 16, 12)
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 16
      }
      layoutParams = p
    }

    val previewTitleTv = TextView(this).apply {
      text = "📋 Export Summary"
      textSize = 13f
      setTextColor(Color.parseColor("#1B5E20"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    val previewDetailsTv = TextView(this).apply {
      text = "Calculating records..."
      textSize = 12f
      setTextColor(Color.parseColor("#2E7D32"))
      setPadding(0, 4, 0, 0)
    }

    previewCard.addView(previewTitleTv)
    previewCard.addView(previewDetailsTv)
    root.addView(previewCard)

    // Filter list of mentees helper
    var currentFilteredMentees = allMentees.toList()

    fun updateMenteeSpinner() {
      val query = etSearchDevotee.text.toString().trim().lowercase(Locale.getDefault())
      currentFilteredMentees = if (query.isEmpty()) {
        allMentees
      } else {
        allMentees.filter {
          it.name.lowercase(Locale.getDefault()).contains(query) ||
            it.email.lowercase(Locale.getDefault()).contains(query)
        }
      }

      val items = currentFilteredMentees.map { m ->
        val count = allSadhnaRecords.count { it.menteeId == m.id }
        "${m.name} (${m.email}) • $count records"
      }

      val adapter = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        if (items.isEmpty()) listOf("No devotee matches search") else items
      )
      spinnerMenteeSelect.adapter = adapter

      // Set selection if selectedMenteeId is in list
      if (selectedMenteeId != null) {
        val index = currentFilteredMentees.indexOfFirst { it.id == selectedMenteeId }
        if (index >= 0) {
          spinnerMenteeSelect.setSelection(index)
        }
      }
    }

    fun getRecordsToExport(): List<SadhnaSubmission> {
      return allSadhnaRecords.filter { record ->
        val matchesScope = when (exportScope) {
          0 -> true
          1 -> record.menteeId == selectedMenteeId
          2 -> record.menteeId == adminUid
          else -> true
        }

        val matchesDate = if (exportDateFilter == null) {
          true
        } else {
          record.date == exportDateFilter
        }

        matchesScope && matchesDate
      }.sortedByDescending { it.date }
    }

    fun updatePreview() {
      val records = getRecordsToExport()
      val targetDesc = when (exportScope) {
        0 -> "All Users & Devotees (${allSadhnaRecords.size} total submissions in database)"
        1 -> {
          val mentee = allMentees.find { it.id == selectedMenteeId }
          mentee?.let { "${it.name} (${it.email})" } ?: "Selected Devotee"
        }
        2 -> "Admin Own Sadhna (HG $adminName)"
        else -> "All Users"
      }

      val dateDesc = exportDateFilter ?: "All Dates"

      previewDetailsTv.text = "• Target: $targetDesc\n• Date: $dateDesc\n• Export Rows: ${records.size} records\n• Format: Microsoft Excel (.xlsx)\n• Columns: ${ExcelExportHelper.COLUMNS.size} (Date, Timings, Aartis, Japa, Books, Seva...)"
    }

    fun updateScopeUi() {
      val colorActive = Color.parseColor("#2E7D32")
      val colorInactive = Color.parseColor("#F5F5F5")
      val textActive = Color.WHITE
      val textInactive = Color.parseColor("#424242")

      btnScopeAll.setBackgroundColor(if (exportScope == 0) colorActive else colorInactive)
      btnScopeAll.setTextColor(if (exportScope == 0) textActive else textInactive)

      btnScopeSpecific.setBackgroundColor(if (exportScope == 1) colorActive else colorInactive)
      btnScopeSpecific.setTextColor(if (exportScope == 1) textActive else textInactive)

      btnScopeMySadhna.setBackgroundColor(if (exportScope == 2) colorActive else colorInactive)
      btnScopeMySadhna.setTextColor(if (exportScope == 2) textActive else textInactive)

      devoteeCard.visibility = if (exportScope == 1) View.VISIBLE else View.GONE
      updatePreview()
    }

    btnScopeAll.setOnClickListener {
      exportScope = 0
      updateScopeUi()
    }

    btnScopeMySadhna.setOnClickListener {
      exportScope = 2
      updateScopeUi()
    }

    btnScopeSpecific.setOnClickListener {
      exportScope = 1
      if (selectedMenteeId == null && allMentees.isNotEmpty()) {
        selectedMenteeId = allMentees.first().id
      }
      updateScopeUi()
      updateMenteeSpinner()
    }

    etSearchDevotee.doAfterTextChanged {
      updateMenteeSpinner()
      if (currentFilteredMentees.isNotEmpty()) {
        selectedMenteeId = currentFilteredMentees.first().id
      }
      updatePreview()
    }

    spinnerMenteeSelect.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position in currentFilteredMentees.indices) {
          selectedMenteeId = currentFilteredMentees[position].id
          updatePreview()
        }
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    btnPickDate.setOnClickListener {
      val c = Calendar.getInstance()
      val dp = DatePickerDialog(
        this,
        { _, year, month, dayOfMonth ->
          val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
          exportDateFilter = dateStr
          btnPickDate.text = "📅 Date: $dateStr"
          updatePreview()
        },
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH),
        c.get(Calendar.DAY_OF_MONTH)
      )
      dp.show()
    }

    btnResetDate.setOnClickListener {
      exportDateFilter = null
      btnPickDate.text = "📅 Date: All Dates"
      updatePreview()
    }

    // Initialize UI
    updateMenteeSpinner()
    updateScopeUi()

    // --- ACTION BUTTONS ---
    val btnGenerate = Button(this).apply {
      text = "📥 GENERATE & EXPORT EXCEL (.xlsx)"
      textSize = 14f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#2E7D32"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 8
      }
      layoutParams = p
      setOnClickListener {
        val inMemoryRecords = getRecordsToExport()
        dialog.dismiss()

        val prefix = when (exportScope) {
          0 -> "ISKCON_All_Sadhna"
          1 -> {
            val mentee = allMentees.find { it.id == selectedMenteeId }
            "Sadhna_${mentee?.name ?: "Devotee"}"
          }
          2 -> "My_Sadhna_Admin_${adminName.replace(" ", "_")}"
          else -> "ISKCON_Sadhna"
        }

        val desc = when (exportScope) {
          0 -> "All Users"
          1 -> {
            val mentee = allMentees.find { it.id == selectedMenteeId }
            "${mentee?.name ?: "Devotee"}"
          }
          2 -> "Admin ($adminName) Own Sadhna"
          else -> "Sadhna"
        }

        if (inMemoryRecords.isNotEmpty()) {
          exportExcelData(inMemoryRecords, prefix, desc)
        } else {
          // If in-memory list hasn't finished loading yet, direct fetch from Firestore
          pbAdminLoading.visibility = View.VISIBLE
          lifecycleScope.launch {
            val directFetch = SessionManager.fetchAllSadhnaRecordsDirectly()
            val directUsers = SessionManager.fetchAllUsersDirectly()
            pbAdminLoading.visibility = View.GONE

            if (directFetch.isSuccess) {
              val fetched = directFetch.getOrNull() ?: emptyList()
              if (directUsers.isSuccess) {
                val uList = directUsers.getOrNull() ?: emptyList()
                allMentees.clear()
                allMentees.addAll(uList.filter { it.role == "mentee" })
                allMentors.clear()
                allMentors.addAll(uList.filter { it.role == "mentor" })
              }
              allSadhnaRecords.clear()
              allSadhnaRecords.addAll(fetched)

              val filtered = getRecordsToExport()
              if (filtered.isEmpty()) {
                Toast.makeText(this@AdminDashboardActivity, "No Sadhna records found in database matching the chosen criteria.", Toast.LENGTH_LONG).show()
              } else {
                exportExcelData(filtered, prefix, "$desc (${filtered.size} records)")
              }
            } else {
              Toast.makeText(this@AdminDashboardActivity, "No Sadhna records found matching the chosen criteria.", Toast.LENGTH_LONG).show()
            }
          }
        }
      }
    }
    root.addView(btnGenerate)

    val btnClose = Button(this).apply {
      text = "CANCEL"
      textSize = 13f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#E0E0E0"))
      val p = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 10
      }
      layoutParams = p
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnClose)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }

  private fun exportExcelData(
    recordsToExport: List<SadhnaSubmission>,
    filePrefix: String,
    titleDescription: String
  ) {
    if (recordsToExport.isEmpty()) {
      Toast.makeText(this, "No Sadhna records found to export.", Toast.LENGTH_SHORT).show()
      return
    }

    pbAdminLoading.visibility = View.VISIBLE

    lifecycleScope.launch {
      try {
        val mentorMap = allMentors.associateBy { it.id }
        val menteeMap = allMentees.associateBy { it.id }

        val excelFile = ExcelExportHelper.generateExcelFile(
          context = this@AdminDashboardActivity,
          submissions = recordsToExport,
          mentorMap = mentorMap,
          menteeMap = menteeMap,
          filePrefix = filePrefix
        )

        // Automatically copy to public Downloads for instant access
        val savedPath = ExcelExportHelper.saveExcelToPublicDownloads(this@AdminDashboardActivity, excelFile)

        pbAdminLoading.visibility = View.GONE
        showExportSuccessDialog(excelFile, recordsToExport.size, titleDescription, savedPath)
      } catch (e: Exception) {
        pbAdminLoading.visibility = View.GONE
        Toast.makeText(this@AdminDashboardActivity, "Failed to generate Excel file: ${e.message}", Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun showExportSuccessDialog(file: File, rowCount: Int, titleDescription: String, savedPath: String? = null) {
    val dialog = Dialog(this)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

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
      text = "Excel File Ready!"
      textSize = 18f
      setTextColor(Color.parseColor("#1B5E20"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
      setPadding(0, 6, 0, 4)
    }
    root.addView(titleTv)

    val infoTv = TextView(this).apply {
      text = "Successfully generated real Microsoft Excel (.xlsx) file with $rowCount Sadhna entries for $titleDescription."
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
        val path = ExcelExportHelper.saveExcelToPublicDownloads(this@AdminDashboardActivity, file)
        if (path != null) {
          Toast.makeText(this@AdminDashboardActivity, "Saved to $path", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(this@AdminDashboardActivity, "File is ready in app storage: ${file.name}", Toast.LENGTH_SHORT).show()
        }
      }
    }
    root.addView(btnSaveDownloads)

    // Open Button
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
        ExcelExportHelper.openExcelFile(this@AdminDashboardActivity, file)
      }
    }
    root.addView(btnOpen)

    // Share Button
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
        ExcelExportHelper.shareExcelFile(this@AdminDashboardActivity, file, "ISKCON Sadhna Report - $titleDescription")
      }
    }
    root.addView(btnShare)

    // Done Button
    val btnDone = Button(this).apply {
      text = "DONE"
      textSize = 12f
      setTextColor(Color.parseColor("#616161"))
      setBackgroundColor(Color.parseColor("#EEEEEE"))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnDone)

    dialog.setContentView(root)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }
}
