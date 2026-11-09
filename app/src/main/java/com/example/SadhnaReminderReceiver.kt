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
    val commitmentId = intent.getStringExtra(SadhnaReminderManager.EXTRA_COMMITMENT_ID) ?: "commitment_submission"
    val title = intent.getStringExtra(SadhnaReminderManager.EXTRA_COMMITMENT_TITLE) ?: "Daily Sadhna Commitment"
    val message = intent.getStringExtra(SadhnaReminderManager.EXTRA_COMMITMENT_MESSAGE) ?: "Time for your spiritual practice"
    val isSubmission = intent.getBooleanExtra(SadhnaReminderManager.EXTRA_IS_SUBMISSION, false)
    val emoji = intent.getStringExtra(SadhnaReminderManager.EXTRA_COMMITMENT_EMOJI) ?: "📿"

    Log.i(TAG, "Sadhna reminder alarm triggered for commitment: $title ($commitmentId, submissionCheck=$isSubmission)")
    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        // 1. Reschedule for next day for this specific commitment
        val allCommitments = SadhnaReminderManager.getCommitments(context)
        val currentCommitment = allCommitments.find { it.id == commitmentId }
        if (currentCommitment != null && currentCommitment.isEnabled && SadhnaReminderManager.isMasterEnabled(context)) {
          SadhnaReminderManager.scheduleCommitmentReminder(context, currentCommitment)
        }

        // 2. Check if this is a submission check reminder (e.g. 9:00 PM)
        if (isSubmission) {
          val isSubmitted = SadhnaReminderManager.hasUserSubmittedTodaySadhna(context)
          if (!isSubmitted) {
            Log.i(TAG, "Today's Sadhna is not submitted yet. Showing reminder notification.")
            SadhnaReminderManager.showCommitmentNotification(
              context = context,
              commitmentId = commitmentId,
              title = title,
              message = message,
              emoji = emoji
            )
          } else {
            Log.i(TAG, "Today's Sadhna has already been submitted. Suppressing reminder notification.")
          }
        } else {
          // Regular Sadhna commitment reminder (e.g. Mangala Aarti, Japa, Reading, Hearing, Evening Aarti)
          val savedProfile = SessionManager.getSavedProfile(context)
          val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
          if (savedProfile != null || authUser != null) {
            Log.i(TAG, "Showing commitment notification for active user: $title")
            SadhnaReminderManager.showCommitmentNotification(
              context = context,
              commitmentId = commitmentId,
              title = title,
              message = message,
              emoji = emoji
            )
          } else {
            Log.d(TAG, "No logged in user session found. Skipping notification.")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error executing Sadhna reminder check: ${e.message}", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
