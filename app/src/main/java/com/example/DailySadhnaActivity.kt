package com.example

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DailySadhnaActivity : ComponentActivity() {

  private var japaCount = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_daily_sadhna)
    findViewById<android.view.View>(R.id.rootDailySadhnaScroll)?.let { InsetUtils.applySafeTopSpacing(it) }

    val etDate = findViewById<EditText>(R.id.etDate)
    val etWakeUpTime = findViewById<EditText>(R.id.etWakeUpTime)
    val etSleepingTime = findViewById<EditText>(R.id.etSleepingTime)

    // Spiritual activities
    val cbMangalAarti = findViewById<CheckBox>(R.id.cbMangalAarti)
    val cbNarasimhaAarti = findViewById<CheckBox>(R.id.cbNarasimhaAarti)
    val cbTulsiAarti = findViewById<CheckBox>(R.id.cbTulsiAarti)
    val cbShringarAarti = findViewById<CheckBox>(R.id.cbShringarAarti)
    val cbGuruPuja = findViewById<CheckBox>(R.id.cbGuruPuja)
    val cbSrimadBhagvatam = findViewById<CheckBox>(R.id.cbSrimadBhagvatam)
    val cbSatsang = findViewById<CheckBox>(R.id.cbSatsang)

    // Japa
    val btnJapaMinus = findViewById<Button>(R.id.btnJapaMinus)
    val btnJapaPlus = findViewById<Button>(R.id.btnJapaPlus)
    val tvJapaCount = findViewById<TextView>(R.id.tvJapaCount)
    val etJapaFinishTime = findViewById<EditText>(R.id.etJapaFinishTime)

    // Book Reading
    val etBookReadingSubject = findViewById<EditText>(R.id.etBookReadingSubject)
    val spinnerBookReadingTime = findViewById<Spinner>(R.id.spinnerBookReadingTime)

    // Hearing
    val etHearingSubject = findViewById<EditText>(R.id.etHearingSubject)
    val spinnerHearingTime = findViewById<Spinner>(R.id.spinnerHearingTime)

    // Seva
    val cbSeva = findViewById<CheckBox>(R.id.cbSeva)
    val etSevaHours = findViewById<EditText>(R.id.etSevaHours)
    val etSevaMinutes = findViewById<EditText>(R.id.etSevaMinutes)
    val etSevaDescription = findViewById<EditText>(R.id.etSevaDescription)

    val btnSaveSadhna = findViewById<Button>(R.id.btnSaveSadhna)
    val btnSadhnaReminders = findViewById<Button>(R.id.btnSadhnaReminders)

    btnSadhnaReminders?.setOnClickListener {
      val intent = android.content.Intent(this, SadhnaRemindersActivity::class.java)
      startActivity(intent)
    }

    // Set today's date default
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    etDate.setText(dateFormat.format(calendar.time))

    // Spinners setup for Reading & Hearing Time
    val timeOptions = arrayOf("0 to 1 Hr", "1 to 2 Hr", "2 to 3 Hr", "3 to 4 Hr", "4+ Hr")
    val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeOptions)
    spinnerBookReadingTime.adapter = spinnerAdapter
    spinnerHearingTime.adapter = spinnerAdapter

    // Load existing record if already recorded for today
    fun loadExistingSadhnaForDate(dateStr: String) {
      val profile = SessionManager.getSavedProfile(this)
      val currentAuth = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
      val uid = currentAuth?.uid ?: profile?.id ?: return

      lifecycleScope.launch {
        val existing = SessionManager.fetchSadhnaRecordForDate(uid, dateStr)
        if (existing != null) {
          etWakeUpTime.setText(existing.wakeUpTime)
          etSleepingTime.setText(existing.sleepingTime)
          etJapaFinishTime.setText(existing.japaFinishTime)

          japaCount = existing.japaRounds
          tvJapaCount.text = japaCount.toString()

          cbMangalAarti.isChecked = existing.spiritualActivities.contains("Mangal Aarti")
          cbNarasimhaAarti.isChecked = existing.spiritualActivities.contains("Narasimha Aarti")
          cbTulsiAarti.isChecked = existing.spiritualActivities.contains("Tulsi Aarti")
          cbShringarAarti.isChecked = existing.spiritualActivities.contains("Shringar Aarti")
          cbGuruPuja.isChecked = existing.spiritualActivities.contains("Guru Puja")
          cbSrimadBhagvatam.isChecked = existing.spiritualActivities.contains("Srimad Bhagvatam")
          cbSatsang.isChecked = existing.spiritualActivities.contains("Satsang")

          etBookReadingSubject.setText(existing.bookReadingSubject)
          val bookIdx = timeOptions.indexOf(existing.bookReadingTime)
          if (bookIdx >= 0) spinnerBookReadingTime.setSelection(bookIdx)

          etHearingSubject.setText(existing.hearingSubject)
          val hearingIdx = timeOptions.indexOf(existing.hearingTime)
          if (hearingIdx >= 0) spinnerHearingTime.setSelection(hearingIdx)

          cbSeva.isChecked = existing.sevaCompleted
          etSevaHours.setText(existing.sevaHours)
          etSevaMinutes.setText(existing.sevaMinutes)
          etSevaDescription.setText(existing.sevaDescription)
        }
      }
    }

    loadExistingSadhnaForDate(etDate.text.toString().trim())

    // Date Picker Dialog
    etDate.setOnClickListener {
      val now = Calendar.getInstance()
      val datePickerDialog = DatePickerDialog(
        this,
        { _, year, month, dayOfMonth ->
          val selected = Calendar.getInstance()
          selected.set(year, month, dayOfMonth)
          val chosenDate = dateFormat.format(selected.time)
          etDate.setText(chosenDate)
          loadExistingSadhnaForDate(chosenDate)
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH)
      )
      datePickerDialog.show()
    }

    // Wake Up Time Picker Dialog
    etWakeUpTime.setOnClickListener {
      val now = Calendar.getInstance()
      val timePickerDialog = TimePickerDialog(
        this,
        { _, hourOfDay, minute ->
          val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
          etWakeUpTime.setText(formatted)
        },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        false
      )
      timePickerDialog.show()
    }

    // Finish Time of Japa Picker Dialog
    etJapaFinishTime.setOnClickListener {
      val now = Calendar.getInstance()
      val timePickerDialog = TimePickerDialog(
        this,
        { _, hourOfDay, minute ->
          val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
          etJapaFinishTime.setText(formatted)
        },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        false
      )
      timePickerDialog.show()
    }

    // Sleeping Time Picker Dialog
    etSleepingTime.setOnClickListener {
      val now = Calendar.getInstance()
      val timePickerDialog = TimePickerDialog(
        this,
        { _, hourOfDay, minute ->
          val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
          etSleepingTime.setText(formatted)
        },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        false
      )
      timePickerDialog.show()
    }

    // Japa Counter Logic
    tvJapaCount.text = japaCount.toString()
    btnJapaMinus.setOnClickListener {
      if (japaCount > 0) {
        japaCount--
        tvJapaCount.text = japaCount.toString()
      }
    }
    btnJapaPlus.setOnClickListener {
      japaCount++
      tvJapaCount.text = japaCount.toString()
    }

    // Seva Toggle Logic
    cbSeva.setOnCheckedChangeListener { _, isChecked ->
      etSevaHours.isEnabled = isChecked
      etSevaMinutes.isEnabled = isChecked
      if (!isChecked) {
        etSevaHours.text.clear()
        etSevaMinutes.text.clear()
      }
    }

    // Save Sadhna Button
    btnSaveSadhna.setOnClickListener {
      val profile = SessionManager.getSavedProfile(this)
      val currentAuth = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
      val menteeId = currentAuth?.uid ?: profile?.id ?: ""

      if (menteeId.isEmpty() || currentAuth == null) {
        Toast.makeText(this, "Session expired. Please log in again to record Sadhna.", Toast.LENGTH_LONG).show()
        return@setOnClickListener
      }

      val role = profile?.role?.lowercase(Locale.getDefault())?.trim() ?: "mentee"
      val isCounsellor = role == "mentor" || role == "counsellor"
      val isAdminRole = role == "admin" || role == "super_admin"
      val mentorId = if (isCounsellor || isAdminRole) null else profile?.mentorId?.takeIf { it.isNotEmpty() }

      // Collect checked spiritual activities
      val spiritualActivities = mutableListOf<String>()
      if (cbMangalAarti.isChecked) spiritualActivities.add("Mangal Aarti")
      if (cbNarasimhaAarti.isChecked) spiritualActivities.add("Narasimha Aarti")
      if (cbTulsiAarti.isChecked) spiritualActivities.add("Tulsi Aarti")
      if (cbShringarAarti.isChecked) spiritualActivities.add("Shringar Aarti")
      if (cbGuruPuja.isChecked) spiritualActivities.add("Guru Puja")
      if (cbSrimadBhagvatam.isChecked) spiritualActivities.add("Srimad Bhagvatam")
      if (cbSatsang.isChecked) spiritualActivities.add("Satsang")

      val japaFinish = etJapaFinishTime.text.toString().trim()
      val japaHours = if (japaFinish.contains(":")) japaFinish.substringBefore(":") else ""
      val japaMins = if (japaFinish.contains(":")) japaFinish.substringAfter(":") else ""

      val userOrgId = profile?.organizationId ?: SessionManager.DEFAULT_ORG_ID
      val devoteeName = when {
        !profile?.name.isNullOrEmpty() -> profile!!.name
        isAdminRole -> "HG Admin"
        isCounsellor -> "HG Counsellor"
        else -> "Devotee"
      }

      val submission = SadhnaSubmission(
        menteeId = menteeId,
        mentorId = mentorId,
        devoteeName = devoteeName,
        organizationId = userOrgId,
        date = etDate.text.toString().trim(),
        wakeUpTime = etWakeUpTime.text.toString().trim(),
        spiritualActivities = spiritualActivities,
        japaRounds = japaCount,
        japaCompletedHours = japaHours,
        japaCompletedMinutes = japaMins,
        japaFinishTime = japaFinish,
        bookReadingSubject = etBookReadingSubject.text.toString().trim(),
        bookReadingTime = spinnerBookReadingTime.selectedItem?.toString() ?: "0 to 1 Hr",
        hearingSubject = etHearingSubject.text.toString().trim(),
        hearingTime = spinnerHearingTime.selectedItem?.toString() ?: "0 to 1 Hr",
        sevaCompleted = cbSeva.isChecked,
        sevaHours = etSevaHours.text.toString().trim().ifEmpty { "0" },
        sevaMinutes = etSevaMinutes.text.toString().trim().ifEmpty { "0" },
        sevaDescription = etSevaDescription.text.toString().trim(),
        sleepingTime = etSleepingTime.text.toString().trim()
      )

      btnSaveSadhna.isEnabled = false
      btnSaveSadhna.text = "SAVING..."

      lifecycleScope.launch {
        // Fetch latest mentorId and organizationId directly from Firestore in case updated
        val (latestMentorId, latestOrgId) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          try {
            val docTask = com.google.firebase.firestore.FirebaseFirestore.getInstance()
              .collection("users").document(menteeId).get()
            val doc = com.google.android.gms.tasks.Tasks.await(docTask)
            val org = doc.getString("organizationId") ?: doc.getString("orgId") ?: userOrgId
            val mId = if (isCounsellor || isAdminRole) null else (doc.getString("mentorId") ?: mentorId)
            Pair(mId, org)
          } catch (_: Exception) {
            Pair(mentorId, userOrgId)
          }
        }

        val updatedSubmission = submission.copy(
          mentorId = latestMentorId,
          organizationId = latestOrgId
        )

        val result = SessionManager.saveSadhna(updatedSubmission)
        btnSaveSadhna.isEnabled = true
        btnSaveSadhna.text = "SAVE SADHNA"

        if (result.isSuccess) {
          SadhnaReminderManager.onSadhnaSubmitted(this@DailySadhnaActivity, updatedSubmission.date)
          Toast.makeText(this@DailySadhnaActivity, "Daily Sadhna recorded in Firebase! Hare Krishna!", Toast.LENGTH_LONG).show()
          finish()
        } else {
          val err = SessionManager.formatErrorMessage(result.exceptionOrNull())
          Toast.makeText(this@DailySadhnaActivity, "Error saving: $err", Toast.LENGTH_LONG).show()
        }
      }
    }
  }
}
