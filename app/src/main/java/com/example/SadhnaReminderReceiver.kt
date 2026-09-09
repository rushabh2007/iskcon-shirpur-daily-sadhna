package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SadhnaReminderReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "SadhnaReminderReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG, "9:00 PM Sadhna reminder alarm triggered.")
    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        // 1. Reschedule for next day at 9:00 PM
        SadhnaReminderManager.scheduleDailyReminder(context)

        // 2. Check whether the currently logged-in user has submitted today's Sadhna
        val isSubmitted = SadhnaReminderManager.hasUserSubmittedTodaySadhna(context)

        // 3. If today's Sadhna is NOT filled/submitted, show notification
        if (!isSubmitted) {
          Log.i(TAG, "Today's Sadhna is not submitted. Showing reminder notification.")
          SadhnaReminderManager.showReminderNotification(context)
        } else {
          Log.i(TAG, "Today's Sadhna has already been submitted or no active user. No notification needed.")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error executing Sadhna reminder check: ${e.message}", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
