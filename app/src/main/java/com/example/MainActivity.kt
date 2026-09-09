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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

  private var japaCount = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_daily_sadhna)

    val etDate = findViewById<EditText>(R.id.etDate)
    val etWakeUpTime = findViewById<EditText>(R.id.etWakeUpTime)
    val etSleepingTime = findViewById<EditText>(R.id.etSleepingTime)
    val btnJapaMinus = findViewById<Button>(R.id.btnJapaMinus)
    val btnJapaPlus = findViewById<Button>(R.id.btnJapaPlus)
    val tvJapaCount = findViewById<TextView>(R.id.tvJapaCount)
    val spinnerBookReadingTime = findViewById<Spinner>(R.id.spinnerBookReadingTime)
    val spinnerHearingTime = findViewById<Spinner>(R.id.spinnerHearingTime)
    val cbSeva = findViewById<CheckBox>(R.id.cbSeva)
    val etSevaHours = findViewById<EditText>(R.id.etSevaHours)
    val etSevaMinutes = findViewById<EditText>(R.id.etSevaMinutes)
    val btnSaveSadhna = findViewById<Button>(R.id.btnSaveSadhna)

    // Set today's date
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
    val etJapaFinishTime = findViewById<EditText>(R.id.etJapaFinishTime)
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
      Toast.makeText(this, "Daily Sadhna recorded successfully!", Toast.LENGTH_SHORT).show()
    }
  }
}
