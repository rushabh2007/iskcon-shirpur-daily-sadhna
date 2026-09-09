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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SadhnaReminderManager {
  private const val TAG = "SadhnaReminderManager"

  const val CHANNEL_ID = "daily_sadhna_reminder_channel"
  private const val CHANNEL_NAME = "Daily Sadhna Reminder"
  private const val CHANNEL_DESCRIPTION = "Notifications reminding devotees to submit their Daily Sadhna at 9:00 PM"

  const val NOTIFICATION_ID = 9001
  const val REMINDER_ALARM_REQUEST_CODE = 9001
  const val NOTIFICATION_REQUEST_CODE = 9002
  const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9003

  const val ACTION_DAILY_SADHNA_REMINDER = "com.example.ACTION_DAILY_SADHNA_REMINDER"

  private const val PREFS_NAME = "sadhna_reminder_prefs"
  private const val KEY_SUBMITTED_PREFIX = "last_submitted_date_"
  private const val KEY_NOTIFIED_PREFIX = "last_notified_date_"

  fun init(context: Context) {
    createNotificationChannel(context)
    scheduleDailyReminder(context)
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

  fun getNextReminderTimeMillis(): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 21) // 9:00 PM
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    if (now.timeInMillis >= target.timeInMillis) {
      target.add(Calendar.DAY_OF_YEAR, 1)
    }

    return target.timeInMillis
  }

  fun scheduleDailyReminder(context: Context) {
    createNotificationChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, SadhnaReminderReceiver::class.java).apply {
      action = ACTION_DAILY_SADHNA_REMINDER
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      REMINDER_ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = getNextReminderTimeMillis()

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
      Log.i(TAG, "Scheduled daily 9:00 PM Sadhna reminder for: ${Date(triggerAtMillis)}")
    } catch (e: SecurityException) {
      Log.w(TAG, "Exact alarm permission not granted: ${e.message}, falling back to setAndAllowWhileIdle")
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
          alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
      } catch (ex: Exception) {
        Log.e(TAG, "Failed fallback alarm scheduling: ${ex.message}", ex)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scheduling daily reminder: ${e.message}", e)
    }
  }

  suspend fun hasUserSubmittedTodaySadhna(context: Context): Boolean = withContext(Dispatchers.IO) {
    val profile = SessionManager.getSavedProfile(context)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = profile?.id ?: currentUser?.uid

    // If no user is currently logged in, return true to avoid showing notification
    if (userId.isNullOrEmpty()) {
      Log.d(TAG, "No logged-in user found at 9:00 PM check. Skipping notification.")
      return@withContext true
    }

    val todayFormats = getTodayDateFormats()
    val primaryToday = todayFormats.first()

    // 1. Check local cache first
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val localSubmittedDate = prefs.getString(KEY_SUBMITTED_PREFIX + userId, null)
    if (localSubmittedDate != null && localSubmittedDate in todayFormats) {
      Log.d(TAG, "User $userId already submitted today's Sadhna (cached locally for $localSubmittedDate).")
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

    // 3. Fallback check: check user's recent submissions if primary query didn't match
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
            Log.d(TAG, "Found today's Sadhna in recent user records for user $userId ($recordDate).")
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

  fun showReminderNotification(context: Context) {
    val profile = SessionManager.getSavedProfile(context)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = profile?.id ?: currentUser?.uid

    if (userId.isNullOrEmpty()) {
      Log.d(TAG, "No logged in user found when attempting to show notification. Skipping.")
      return
    }

    val todayFormats = getTodayDateFormats()
    val primaryToday = todayFormats.first()

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val lastNotifiedDate = prefs.getString(KEY_NOTIFIED_PREFIX + userId, null)

    // Avoid duplicate notifications for the same day
    if (lastNotifiedDate != null && lastNotifiedDate in todayFormats) {
      Log.d(TAG, "Notification already shown today ($lastNotifiedDate) for user $userId. Skipping duplicate.")
      return
    }

    // Check Android 13+ permission
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
    val pendingIntent = PendingIntent.getActivity(
      context,
      NOTIFICATION_REQUEST_CODE,
      tapIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val title = "Daily Sadhna Reminder 🙏"
    val message = "You haven't submitted today's Sadhna yet. Please update your Sadhna for today."

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_sadhna)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setDefaults(NotificationCompat.DEFAULT_ALL)

    try {
      val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
      if (logoBitmap != null) {
        builder.setLargeIcon(logoBitmap)
      }
    } catch (_: Exception) {
      // Large icon optional
    }

    try {
      val notificationManager = NotificationManagerCompat.from(context)
      notificationManager.notify(NOTIFICATION_ID, builder.build())
      prefs.edit().putString(KEY_NOTIFIED_PREFIX + userId, primaryToday).apply()
      Log.i(TAG, "Daily Sadhna reminder notification displayed successfully.")
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException while showing notification: ${e.message}", e)
    } catch (e: Exception) {
      Log.e(TAG, "Error displaying notification: ${e.message}", e)
    }
  }

  fun cancelReminderNotification(context: Context) {
    try {
      val notificationManager = NotificationManagerCompat.from(context)
      notificationManager.cancel(NOTIFICATION_ID)
    } catch (e: Exception) {
      Log.e(TAG, "Error cancelling notification: ${e.message}")
    }
  }

  fun onSadhnaSubmitted(context: Context, date: String) {
    val profile = SessionManager.getSavedProfile(context)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userId = profile?.id ?: currentUser?.uid ?: return

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SUBMITTED_PREFIX + userId, date).apply()

    val todayFormats = getTodayDateFormats()
    if (date in todayFormats) {
      cancelReminderNotification(context)
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
}
