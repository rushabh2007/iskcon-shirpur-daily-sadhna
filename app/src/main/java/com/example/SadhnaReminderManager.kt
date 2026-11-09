package com.example

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SadhnaReminderManager {
  private const val TAG = "SadhnaReminderManager"

  const val CHANNEL_ID = "daily_sadhna_commitment_channel"
  private const val CHANNEL_NAME = "Daily Sadhna Commitments"
  private const val CHANNEL_DESCRIPTION = "Notifications reminding devotees of their daily spiritual commitments & Sadhna"

  const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9003

  const val ACTION_DAILY_SADHNA_REMINDER = "com.example.ACTION_DAILY_SADHNA_REMINDER"
  const val EXTRA_COMMITMENT_ID = "extra_commitment_id"
  const val EXTRA_COMMITMENT_TITLE = "extra_commitment_title"
  const val EXTRA_COMMITMENT_MESSAGE = "extra_commitment_message"
  const val EXTRA_IS_SUBMISSION = "extra_is_submission"
  const val EXTRA_COMMITMENT_EMOJI = "extra_commitment_emoji"

  private const val PREFS_NAME = "sadhna_reminder_prefs"
  private const val KEY_COMMITMENTS_JSON = "key_commitments_json"
  private const val KEY_MASTER_ENABLED = "key_master_enabled"
  private const val KEY_SUBMITTED_PREFIX = "last_submitted_date_"
  private const val KEY_NOTIFIED_PREFIX = "last_notified_date_"

  fun init(context: Context) {
    try {
      createNotificationChannel(context)
      CoroutineScope(Dispatchers.IO).launch {
        try {
          scheduleAllReminders(context)
        } catch (e: Exception) {
          Log.w(TAG, "Background reminder scheduling warning: ${e.message}")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "SadhnaReminderManager init warning: ${e.message}")
    }
  }

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
      if (existingChannel == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = CHANNEL_DESCRIPTION
          enableLights(true)
          enableVibration(true)
          setShowBadge(true)
          lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $CHANNEL_ID")
      }
    }
  }

  fun isMasterEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_MASTER_ENABLED, true)
  }

  fun setMasterEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    if (enabled) {
      scheduleAllReminders(context)
    } else {
      cancelAllReminders(context)
    }
  }

  fun getCommitments(context: Context): List<SadhnaCommitment> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val rawJson = prefs.getString(KEY_COMMITMENTS_JSON, null)
    if (rawJson.isNullOrEmpty()) {
      val defaults = SadhnaCommitment.getDefaultCommitments()
      saveCommitments(context, defaults)
      return defaults
    }

    return try {
      val array = JSONArray(rawJson)
      val list = mutableListOf<SadhnaCommitment>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(SadhnaCommitment.fromJson(obj))
      }
      if (list.isEmpty()) {
        val defaults = SadhnaCommitment.getDefaultCommitments()
        saveCommitments(context, defaults)
        defaults
      } else {
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing commitments JSON, resetting to defaults", e)
      val defaults = SadhnaCommitment.getDefaultCommitments()
      saveCommitments(context, defaults)
      defaults
    }
  }

  fun saveCommitments(context: Context, list: List<SadhnaCommitment>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val array = JSONArray()
    for (item in list) {
      array.put(item.toJson())
    }
    prefs.edit().putString(KEY_COMMITMENTS_JSON, array.toString()).apply()
  }

  fun updateCommitment(context: Context, updated: SadhnaCommitment) {
    val list = getCommitments(context).toMutableList()
    val index = list.indexOfFirst { it.id == updated.id }
    if (index != -1) {
      list[index] = updated
    } else {
      list.add(updated)
    }
    saveCommitments(context, list)
    if (isMasterEnabled(context)) {
      if (updated.isEnabled) {
        scheduleCommitmentReminder(context, updated)
      } else {
        cancelCommitmentReminder(context, updated.id)
      }
    }
  }

  fun addCustomCommitment(
    context: Context,
    title: String,
    subtitle: String,
    hour: Int,
    minute: Int,
    emoji: String = "📿"
  ): SadhnaCommitment {
    val newCommitment = SadhnaCommitment(
      id = "custom_${System.currentTimeMillis()}",
      title = title.trim(),
      subtitle = subtitle.trim().ifEmpty { "Spiritual practice reminder" },
      hour = hour,
      minute = minute,
      isEnabled = true,
      isCustom = true,
      isSubmissionCheck = false,
      emoji = emoji
    )
    val list = getCommitments(context).toMutableList()
    list.add(newCommitment)
    saveCommitments(context, list)
    if (isMasterEnabled(context)) {
      scheduleCommitmentReminder(context, newCommitment)
    }
    return newCommitment
  }

  fun deleteCommitment(context: Context, commitmentId: String) {
    cancelCommitmentReminder(context, commitmentId)
    val list = getCommitments(context).toMutableList()
    list.removeAll { it.id == commitmentId }
    saveCommitments(context, list)
  }

  fun resetToDefaults(context: Context): List<SadhnaCommitment> {
    cancelAllReminders(context)
    val defaults = SadhnaCommitment.getDefaultCommitments()
    saveCommitments(context, defaults)
    if (isMasterEnabled(context)) {
      scheduleAllReminders(context)
    }
    return defaults
  }

  fun scheduleAllReminders(context: Context) {
    if (!isMasterEnabled(context)) {
      Log.d(TAG, "Master switch is OFF. Skipping reminder scheduling.")
      return
    }

    createNotificationChannel(context)
    val commitments = getCommitments(context)
    for (commitment in commitments) {
      if (commitment.isEnabled) {
        scheduleCommitmentReminder(context, commitment)
      } else {
        cancelCommitmentReminder(context, commitment.id)
      }
    }

    checkAndSendMonthEndReminders(context)
    checkAndSendScheduledDeletionNotice(context)
  }

  const val ADVANCE_REMINDER_MINUTES = 15L

  fun cleanActivityTitle(raw: String): String {
    return raw.removeSuffix(" & Prayers")
      .removeSuffix(" & Reading")
      .trim()
  }

  fun scheduleCommitmentReminder(context: Context, commitment: SadhnaCommitment) {
    createNotificationChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

    val cleanActivityName = cleanActivityTitle(commitment.title)
    val intent = Intent(context, SadhnaReminderReceiver::class.java).apply {
      action = ACTION_DAILY_SADHNA_REMINDER
      putExtra(EXTRA_COMMITMENT_ID, commitment.id)
      putExtra(EXTRA_COMMITMENT_TITLE, cleanActivityName)
      putExtra(EXTRA_COMMITMENT_MESSAGE, "$cleanActivityName will begin in 15 minutes.")
      putExtra(EXTRA_IS_SUBMISSION, commitment.isSubmissionCheck)
      putExtra(EXTRA_COMMITMENT_EMOJI, commitment.emoji)
    }

    val requestCode = getRequestCode(commitment.id)
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = getNextReminderTriggerTimeMillis(commitment.hour, commitment.minute)

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      }
      Log.i(TAG, "Scheduled reminder for '${commitment.title}' at ${Date(triggerAtMillis)} (15 mins before ${commitment.getFormattedTime()})")
    } catch (e: SecurityException) {
      Log.w(TAG, "Exact alarm permission restricted: ${e.message}, using fallback")
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
          alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
      } catch (ex: Exception) {
        Log.e(TAG, "Fallback alarm failed for ${commitment.title}", ex)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scheduling reminder for ${commitment.title}", e)
    }
  }

  fun cancelCommitmentReminder(context: Context, commitmentId: String) {
    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      val intent = Intent(context, SadhnaReminderReceiver::class.java).apply {
        action = ACTION_DAILY_SADHNA_REMINDER
      }
      val requestCode = getRequestCode(commitmentId)
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
      )
      if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled alarm for commitment ID: $commitmentId")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error cancelling reminder: ${e.message}", e)
    }
  }

  fun cancelAllReminders(context: Context) {
    val commitments = getCommitments(context)
    for (c in commitments) {
      cancelCommitmentReminder(context, c.id)
    }
  }

  // Backward compatibility alias
  fun cancelReminderNotification(context: Context) {
    cancelAllReminders(context)
  }

  // Backward compatibility alias
  fun scheduleDailyReminder(context: Context) {
    scheduleAllReminders(context)
  }

  /**
   * Calculates the notification trigger time exactly 15 minutes BEFORE the scheduled activity time.
   * Notification Time = Scheduled Activity Time - 15 minutes.
   * Seamlessly handles midnight crossover, day shifts, and local device timezones.
   */
  fun getNextReminderTriggerTimeMillis(activityHour: Int, activityMinute: Int): Long {
    val now = Calendar.getInstance()
    val scheduled = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, activityHour)
      set(Calendar.MINUTE, activityMinute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    var reminderTime = scheduled.timeInMillis - TimeUnit.MINUTES.toMillis(ADVANCE_REMINDER_MINUTES)

    // Advance by day until the 15-minute advance reminder is strictly in the future
    while (now.timeInMillis >= reminderTime) {
      scheduled.add(Calendar.DAY_OF_YEAR, 1)
      reminderTime = scheduled.timeInMillis - TimeUnit.MINUTES.toMillis(ADVANCE_REMINDER_MINUTES)
    }

    return reminderTime
  }

  // Alias for backward compatibility
  fun getNextTriggerTimeMillis(hour: Int, minute: Int): Long {
    return getNextReminderTriggerTimeMillis(hour, minute)
  }

  fun getNextUpcomingCommitment(context: Context): Pair<SadhnaCommitment, Long>? {
    if (!isMasterEnabled(context)) return null
    val commitments = getCommitments(context).filter { it.isEnabled }
    if (commitments.isEmpty()) return null

    var closest: SadhnaCommitment? = null
    var minTrigger = Long.MAX_VALUE

    for (c in commitments) {
      val trigger = getNextReminderTriggerTimeMillis(c.hour, c.minute)
      if (trigger < minTrigger) {
        minTrigger = trigger
        closest = c
      }
    }

    return if (closest != null) Pair(closest, minTrigger) else null
  }

  private fun getRequestCode(commitmentId: String): Int {
    return (commitmentId.hashCode() and 0x7FFFFFFF)
  }

  fun getTodayDateFormats(): List<String> {
    val now = Calendar.getInstance()
    val day = now.get(Calendar.DAY_OF_MONTH)
    val month = now.get(Calendar.MONTH) + 1
    val year = now.get(Calendar.YEAR)

    return listOf(
      String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year),
      String.format(Locale.getDefault(), "%d/%02d/%04d", day, month, year),
      String.format(Locale.getDefault(), "%02d/%d/%04d", day, month, year),
      String.format(Locale.getDefault(), "%d/%d/%04d", day, month, year)
    )
  }

  suspend fun hasUserSubmittedTodaySadhna(context: Context): Boolean = withContext(Dispatchers.IO) {
    val profile = SessionManager.getSavedProfile(context)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = profile?.id ?: currentUser?.uid

    if (userId.isNullOrEmpty()) {
      Log.d(TAG, "No logged-in user found. Skipping submission check.")
      return@withContext true
    }

    val todayFormats = getTodayDateFormats()
    val primaryToday = todayFormats.first()

    // 1. Check local cache first
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val localSubmittedDate = prefs.getString(KEY_SUBMITTED_PREFIX + userId, null)
    if (localSubmittedDate != null && localSubmittedDate in todayFormats) {
      Log.d(TAG, "User $userId already submitted today's Sadhna (cached locally).")
      return@withContext true
    }

    // 2. Query Firestore sadhna_records for today's date
    try {
      val firestore = FirebaseFirestore.getInstance()
      val task = firestore.collection("sadhna_records")
        .whereEqualTo("menteeId", userId)
        .whereEqualTo("date", primaryToday)
        .limit(1)
        .get()

      val snapshot = Tasks.await(task, 10, TimeUnit.SECONDS)
      if (snapshot != null && !snapshot.isEmpty) {
        Log.d(TAG, "Found today's Sadhna submission in Firestore for user $userId.")
        prefs.edit().putString(KEY_SUBMITTED_PREFIX + userId, primaryToday).apply()
        return@withContext true
      }
    } catch (e: Exception) {
      Log.w(TAG, "Querying Firestore for today's sadhna date failed: ${e.message}")
    }

    // 3. Fallback check
    try {
      val firestore = FirebaseFirestore.getInstance()
      val task = firestore.collection("sadhna_records")
        .whereEqualTo("menteeId", userId)
        .get()

      val snapshot = Tasks.await(task, 8, TimeUnit.SECONDS)
      if (snapshot != null && !snapshot.isEmpty) {
        for (doc in snapshot.documents) {
          val recordDate = doc.getString("date")?.trim() ?: ""
          if (recordDate in todayFormats) {
            prefs.edit().putString(KEY_SUBMITTED_PREFIX + userId, recordDate).apply()
            return@withContext true
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Fallback fetch for sadhna records failed: ${e.message}")
    }

    return@withContext false
  }

  fun showCommitmentNotification(
    context: Context,
    commitmentId: String,
    title: String,
    message: String,
    emoji: String = "📿"
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
        return
      }
    }

    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      Log.w(TAG, "App notifications are disabled by user in system settings.")
      return
    }

    createNotificationChannel(context)

    val tapIntent = Intent(context, DailySadhnaActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val notifRequestCode = getRequestCode(commitmentId) + 100
    val pendingIntent = PendingIntent.getActivity(
      context,
      notifRequestCode,
      tapIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val cleanActivity = cleanActivityTitle(title)
    val notifTitle = "Upcoming Spiritual Activity"
    val notifMessage = "$cleanActivity will begin in 15 minutes."

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_sadhna)
      .setContentTitle(notifTitle)
      .setContentText(notifMessage)
      .setStyle(NotificationCompat.BigTextStyle().bigText(notifMessage))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .addAction(
        R.drawable.ic_notification_sadhna,
        "Open Sadhna",
        pendingIntent
      )

    try {
      val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
      if (logoBitmap != null) {
        builder.setLargeIcon(logoBitmap)
      }
    } catch (_: Exception) {
    }

    try {
      val notificationManager = NotificationManagerCompat.from(context)
      val notificationId = getRequestCode(commitmentId)
      notificationManager.notify(notificationId, builder.build())
      Log.i(TAG, "Sadhna commitment notification shown: $notifTitle - $notifMessage (ID: $notificationId)")
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException while showing notification: ${e.message}", e)
    } catch (e: Exception) {
      Log.e(TAG, "Error displaying notification: ${e.message}", e)
    }
  }

  fun sendTestNotification(context: Context, commitment: SadhnaCommitment? = null) {
    val target = commitment ?: getCommitments(context).firstOrNull() ?: SadhnaCommitment.getDefaultCommitments().first()
    val cleanName = cleanActivityTitle(target.title)
    showCommitmentNotification(
      context = context,
      commitmentId = "test_${target.id}",
      title = cleanName,
      message = "$cleanName will begin in 15 minutes.",
      emoji = target.emoji
    )
  }

  fun onSadhnaSubmitted(context: Context, date: String) {
    val profile = SessionManager.getSavedProfile(context)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = profile?.id ?: currentUser?.uid ?: return

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SUBMITTED_PREFIX + userId, date).apply()

    val todayFormats = getTodayDateFormats()
    if (date in todayFormats) {
      try {
        val notificationManager = NotificationManagerCompat.from(context)
        val subId = getRequestCode("commitment_submission")
        notificationManager.cancel(subId)
      } catch (e: Exception) {
        Log.e(TAG, "Error cancelling submission notification: ${e.message}")
      }
    }
  }

  fun requestNotificationPermissionIfNeeded(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
          activity,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          NOTIFICATION_PERMISSION_REQUEST_CODE
        )
      }
    }
  }

  fun checkAndSendMonthEndReminders(context: Context) {
    try {
      val now = Calendar.getInstance()
      val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
      if (dayOfMonth != 1) return

      val monthKey = String.format(Locale.getDefault(), "%04d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val lastNotifiedMonth = prefs.getString("last_month_end_notified", "")
      if (lastNotifiedMonth == monthKey) return

      val profile = SessionManager.getSavedProfile(context)
      val isAdmin = profile?.role?.lowercase() == "admin"

      val title = "Previous Month Sadhna Report"
      val message = if (isAdmin) {
        "The previous month's Sadhna records are ready for review and export."
      } else {
        "Your previous month's Sadhna report is ready. Please download your Excel report."
      }

      showCommitmentNotification(
        context = context,
        commitmentId = "month_end_report",
        title = title,
        message = message,
        emoji = "📊"
      )

      prefs.edit().putString("last_month_end_notified", monthKey).apply()
    } catch (e: Exception) {
      Log.e(TAG, "Error checking month-end reminders: ${e.message}")
    }
  }

  fun checkAndSendScheduledDeletionNotice(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val result = SessionManager.getScheduledDeletionNotice()
        val notice = result.getOrNull() ?: return@launch
        val status = notice["status"] as? String ?: return@launch
        if (status != "scheduled") return@launch

        val scheduledMs = (notice["epochMs"] as? Number)?.toLong() ?: return@launch
        val nowMs = System.currentTimeMillis()
        val diffMs = scheduledMs - nowMs

        // Check if scheduled within next 48 hours (0 to 49 hours)
        if (diffMs in 0..(49 * 3600 * 1000L)) {
          val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
          val notifiedKey = "notified_deletion_$scheduledMs"
          if (prefs.getBoolean(notifiedKey, false)) return@launch

          withContext(Dispatchers.Main) {
            showCommitmentNotification(
              context = context,
              commitmentId = "deletion_notice_48h",
              title = "Sadhna Deletion Notice",
              message = "Your Sadhna records are scheduled for deletion in 2 days. Please download your Excel report.",
              emoji = "⚠️"
            )
          }
          prefs.edit().putBoolean(notifiedKey, true).apply()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Warning checking deletion notice: ${e.message}")
      }
    }
  }
}
