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

    val btnSaveSadhna = findViewById<Button>(R.id.btnSaveSadhna)

    // Set today's date default
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    etDate.setText(dateFormat.format(calendar.time))

    // Date Picker Dialog
    etDate.setOnClickListener {
      val now = Calendar.getInstance()
      val datePickerDialog = DatePickerDialog(
        this,
        { _, year, month, dayOfMonth ->
          val selected = Calendar.getInstance()
          selected.set(year, month, dayOfMonth)
          etDate.setText(dateFormat.format(selected.time))
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

    // Spinners setup for Reading & Hearing Time
    val timeOptions = arrayOf("0 to 1 Hr", "1 to 2 Hr", "2 to 3 Hr", "3 to 4 Hr", "4+ Hr")
    val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeOptions)
    spinnerBookReadingTime.adapter = spinnerAdapter
    spinnerHearingTime.adapter = spinnerAdapter

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
      val menteeId = profile?.id ?: "anonymous_user"
      val mentorId = profile?.mentorId ?: ""

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

      val submission = SadhnaSubmission(
        menteeId = menteeId,
        mentorId = mentorId.takeIf { it.isNotEmpty() },
        devoteeName = profile?.name ?: "Devotee",
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
        sleepingTime = etSleepingTime.text.toString().trim()
      )

      btnSaveSadhna.isEnabled = false
      btnSaveSadhna.text = "SAVING..."

      lifecycleScope.launch {
        // Fetch latest mentorId directly from Firestore in case admin recently updated assignment
        val latestMentorId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          try {
            val docTask = com.google.firebase.firestore.FirebaseFirestore.getInstance()
              .collection("users").document(menteeId).get()
            val doc = com.google.android.gms.tasks.Tasks.await(docTask)
            doc.getString("mentorId") ?: mentorId
          } catch (_: Exception) {
            mentorId
          }
        }

        val updatedSubmission = submission.copy(
          mentorId = latestMentorId.takeIf { it.isNotEmpty() }
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
