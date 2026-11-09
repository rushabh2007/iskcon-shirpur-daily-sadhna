package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class SadhnaApplication : Application() {

  override fun onCreate() {
    super.onCreate()

    // Safe Firebase initialization
    try {
      if (FirebaseApp.getApps(this).isEmpty()) {
        FirebaseApp.initializeApp(this)
      }
    } catch (e: Exception) {
      Log.w("SadhnaApplication", "Firebase initialization handled: ${e.message}")
    }

    // Configure Cloud Firestore with aggressive local caching & instantaneous real-time sync
    try {
      val firestore = FirebaseFirestore.getInstance()
      val settings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(
          PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        )
        .build()
      firestore.firestoreSettings = settings
    } catch (e: Exception) {
      Log.w("SadhnaApplication", "Firestore settings handled: ${e.message}")
    }

    // Initialize 9:00 PM Daily Sadhna reminder schedule & notification channel safely
    try {
      SadhnaReminderManager.init(this)
    } catch (e: Exception) {
      Log.w("SadhnaApplication", "SadhnaReminderManager initialization handled: ${e.message}")
    }
  }
}

