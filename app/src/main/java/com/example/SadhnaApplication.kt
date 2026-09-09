package com.example

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class SadhnaApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this)

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
    } catch (_: Exception) {
      // Ignore if settings were already initialized
    }

    // Initialize 9:00 PM Daily Sadhna reminder schedule & notification channel
    SadhnaReminderManager.init(this)
  }
}

