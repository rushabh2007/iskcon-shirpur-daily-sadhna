package com.example

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SadhnaRemindersActivity : ComponentActivity() {

  private lateinit var btnBack: Button
  private lateinit var btnTestNotification: Button
  private lateinit var layoutPermissionWarning: LinearLayout
  private lateinit var btnGrantPermission: Button
  private lateinit var switchMaster: Switch
  private lateinit var layoutNextReminderCard: LinearLayout
  private lateinit var tvNextReminderText: TextView
  private lateinit var btnAddCustomReminder: Button
  private lateinit var btnResetDefaults: Button
  private lateinit var llCommitmentList: LinearLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_sadhna_reminders)
    findViewById<View>(R.id.rootRemindersLayout)?.let { InsetUtils.applySafeTopSpacing(it) }

    btnBack = findViewById(R.id.btnBack)
    btnTestNotification = findViewById(R.id.btnTestNotification)
    layoutPermissionWarning = findViewById(R.id.layoutPermissionWarning)
    btnGrantPermission = findViewById(R.id.btnGrantPermission)
    switchMaster = findViewById(R.id.switchMaster)
    layoutNextReminderCard = findViewById(R.id.layoutNextReminderCard)
    tvNextReminderText = findViewById(R.id.tvNextReminderText)
    btnAddCustomReminder = findViewById(R.id.btnAddCustomReminder)
    btnResetDefaults = findViewById(R.id.btnResetDefaults)
    llCommitmentList = findViewById(R.id.llCommitmentList)

    btnBack.setOnClickListener {
      finish()
    }

    btnTestNotification.setOnClickListener {
      SadhnaReminderManager.sendTestNotification(this)
      Toast.makeText(this, "🔔 Test notification sent! Check your notification shade.", Toast.LENGTH_SHORT).show()
    }

    btnGrantPermission.setOnClickListener {
      requestNotificationPermission()
    }

    switchMaster.isChecked = SadhnaReminderManager.isMasterEnabled(this)
    switchMaster.setOnCheckedChangeListener { _, isChecked ->
      SadhnaReminderManager.setMasterEnabled(this, isChecked)
      refreshCommitmentsList()
      val msg = if (isChecked) "All Sadhna reminders enabled & scheduled" else "All Sadhna reminders disabled"
      Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    btnAddCustomReminder.setOnClickListener {
      showAddCustomReminderDialog()
    }

    btnResetDefaults.setOnClickListener {
      AlertDialog.Builder(this)
        .setTitle("Reset to Recommended Times?")
        .setMessage("This will restore the standard temple practice reminder schedule (Mangala Aarti, Japa, Reading, Hearing, Evening Aarti, Submission).")
        .setPositiveButton("Reset") { _, _ ->
          SadhnaReminderManager.resetToDefaults(this)
          refreshCommitmentsList()
          Toast.makeText(this, "Reminders reset to default temple schedule", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Cancel", null)
        .show()
    }

    checkNotificationPermissions()
    refreshCommitmentsList()
  }

  override fun onResume() {
    super.onResume()
    checkNotificationPermissions()
    refreshCommitmentsList()
  }

  private fun checkNotificationPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      layoutPermissionWarning.visibility = if (isGranted) View.GONE else View.VISIBLE
    } else {
      layoutPermissionWarning.visibility = View.GONE
    }
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          SadhnaReminderManager.NOTIFICATION_PERMISSION_REQUEST_CODE
        )
      } else {
        try {
          val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
          }
          startActivity(intent)
        } catch (_: Exception) {
          ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            SadhnaReminderManager.NOTIFICATION_PERMISSION_REQUEST_CODE
          )
        }
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == SadhnaReminderManager.NOTIFICATION_PERMISSION_REQUEST_CODE) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        layoutPermissionWarning.visibility = View.GONE
        Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
      } else {
        layoutPermissionWarning.visibility = View.VISIBLE
      }
    }
  }

  private fun refreshCommitmentsList() {
    llCommitmentList.removeAllViews()
    val isMasterActive = SadhnaReminderManager.isMasterEnabled(this)
    val commitments = SadhnaReminderManager.getCommitments(this)
    val inflater = LayoutInflater.from(this)

    for (item in commitments) {
      val itemView = inflater.inflate(R.layout.item_sadhna_commitment, llCommitmentList, false)

      val tvEmoji = itemView.findViewById<TextView>(R.id.tvCommitmentEmoji)
      val tvTitle = itemView.findViewById<TextView>(R.id.tvCommitmentTitle)
      val tvSubtitle = itemView.findViewById<TextView>(R.id.tvCommitmentSubtitle)
      val switchCommitment = itemView.findViewById<Switch>(R.id.switchCommitment)
      val btnChangeTime = itemView.findViewById<Button>(R.id.btnChangeTime)
      val tvSubmissionBadge = itemView.findViewById<TextView>(R.id.tvSubmissionBadge)
      val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteCommitment)

      tvEmoji.text = item.emoji
      tvTitle.text = item.title
      tvSubtitle.text = item.subtitle
      btnChangeTime.text = "⏰ ${item.getFormattedTime()}"

      switchCommitment.isEnabled = isMasterActive
      btnChangeTime.isEnabled = isMasterActive && item.isEnabled
      itemView.alpha = if (isMasterActive && item.isEnabled) 1.0f else 0.6f

      switchCommitment.isChecked = item.isEnabled

      if (item.isSubmissionCheck) {
        tvSubmissionBadge.visibility = View.VISIBLE
      } else {
        tvSubmissionBadge.visibility = View.GONE
      }

      if (item.isCustom) {
        btnDelete.visibility = View.VISIBLE
        btnDelete.setOnClickListener {
          AlertDialog.Builder(this)
            .setTitle("Delete Reminder?")
            .setMessage("Are you sure you want to remove '${item.title}'?")
            .setPositiveButton("Delete") { _, _ ->
              SadhnaReminderManager.deleteCommitment(this, item.id)
              refreshCommitmentsList()
              Toast.makeText(this, "Reminder removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
        }
      } else {
        btnDelete.visibility = View.GONE
      }

      switchCommitment.setOnCheckedChangeListener { _, isChecked ->
        item.isEnabled = isChecked
        SadhnaReminderManager.updateCommitment(this, item)
        btnChangeTime.isEnabled = isMasterActive && isChecked
        itemView.alpha = if (isMasterActive && isChecked) 1.0f else 0.6f
        updateUpcomingSummary()
      }

      btnChangeTime.setOnClickListener {
        TimePickerDialog(
          this,
          { _, hourOfDay, minute ->
            item.hour = hourOfDay
            item.minute = minute
            SadhnaReminderManager.updateCommitment(this, item)
            btnChangeTime.text = "⏰ ${item.getFormattedTime()}"
            updateUpcomingSummary()
            val cleanTitle = SadhnaReminderManager.cleanActivityTitle(item.title)
            Toast.makeText(this, "Reminder for '$cleanTitle' will alert 15 min before ${item.getFormattedTime()}", Toast.LENGTH_SHORT).show()
          },
          item.hour,
          item.minute,
          false
        ).show()
      }

      llCommitmentList.addView(itemView)
    }

    updateUpcomingSummary()
  }

  private fun updateUpcomingSummary() {
    val next = SadhnaReminderManager.getNextUpcomingCommitment(this)
    if (next != null) {
      val (commitment, triggerMillis) = next
      val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
      val isToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
      }.timeInMillis >= triggerMillis

      val dayLabel = if (isToday) "Today" else "Tomorrow"
      val triggerStr = timeFormat.format(Date(triggerMillis))
      val cleanName = SadhnaReminderManager.cleanActivityTitle(commitment.title)
      tvNextReminderText.text = "Next: ${commitment.emoji} $cleanName (Alert at $triggerStr • Activity at ${commitment.getFormattedTime()})"
      layoutNextReminderCard.visibility = View.VISIBLE
    } else {
      if (!SadhnaReminderManager.isMasterEnabled(this)) {
        tvNextReminderText.text = "All Sadhna reminders are currently paused."
      } else {
        tvNextReminderText.text = "No active reminders enabled. Turn on commitments above."
      }
      layoutNextReminderCard.visibility = View.VISIBLE
    }
  }

  private fun showAddCustomReminderDialog() {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_custom_reminder, null)
    val etTitle = dialogView.findViewById<EditText>(R.id.etCustomTitle)
    val etSubtitle = dialogView.findViewById<EditText>(R.id.etCustomSubtitle)
    val btnPickTime = dialogView.findViewById<Button>(R.id.btnPickCustomTime)

    var selectedHour = 16
    var selectedMinute = 30
    var selectedEmoji = "📿"

    val emojiViews = listOf(
      dialogView.findViewById<TextView>(R.id.tvEmoji1) to "📿",
      dialogView.findViewById<TextView>(R.id.tvEmoji2) to "📖",
      dialogView.findViewById<TextView>(R.id.tvEmoji3) to "🌅",
      dialogView.findViewById<TextView>(R.id.tvEmoji4) to "🪔",
      dialogView.findViewById<TextView>(R.id.tvEmoji5) to "🎧",
      dialogView.findViewById<TextView>(R.id.tvEmoji6) to "🙏"
    )

    fun updateEmojiSelection(chosenEmoji: String) {
      selectedEmoji = chosenEmoji
      for ((view, emoji) in emojiViews) {
        if (emoji == chosenEmoji) {
          view.setBackgroundColor(Color.parseColor("#FFE082"))
        } else {
          view.setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
      }
    }

    for ((view, emoji) in emojiViews) {
      view.setOnClickListener {
        updateEmojiSelection(emoji)
      }
    }

    fun formatTime(h: Int, m: Int): String {
      val displayHour = if (h == 0) 12 else if (h > 12) h - 12 else h
      val amPm = if (h < 12) "AM" else "PM"
      return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, m, amPm)
    }

    btnPickTime.text = "⏰ Set Time: ${formatTime(selectedHour, selectedMinute)}"
    btnPickTime.setOnClickListener {
      TimePickerDialog(
        this,
        { _, hourOfDay, minute ->
          selectedHour = hourOfDay
          selectedMinute = minute
          btnPickTime.text = "⏰ Set Time: ${formatTime(selectedHour, selectedMinute)}"
        },
        selectedHour,
        selectedMinute,
        false
      ).show()
    }

    AlertDialog.Builder(this)
      .setView(dialogView)
      .setPositiveButton("Save Reminder") { _, _ ->
        val title = etTitle.text.toString().trim()
        val subtitle = etSubtitle.text.toString().trim()

        if (title.isEmpty()) {
          Toast.makeText(this, "Please enter a reminder name", Toast.LENGTH_SHORT).show()
          return@setPositiveButton
        }

        val newCommitment = SadhnaReminderManager.addCustomCommitment(
          context = this,
          title = title,
          subtitle = subtitle,
          hour = selectedHour,
          minute = selectedMinute,
          emoji = selectedEmoji
        )

        refreshCommitmentsList()
        Toast.makeText(this, "Added reminder for '${newCommitment.title}' at ${newCommitment.getFormattedTime()}", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }
}
