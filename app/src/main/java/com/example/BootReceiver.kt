package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "BootReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG, "Device boot or time change broadcast received: ${intent.action}. Rescheduling all Sadhna reminders.")
    SadhnaReminderManager.scheduleAllReminders(context)
  }
}
