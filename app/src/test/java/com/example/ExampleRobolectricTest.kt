package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ISKCON", appName)
  }

  @Test
  fun `session manager saves and retrieves user profile correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val profile = UserProfile(
      id = "test-uid-123",
      name = "Bhakta Rahul",
      email = "rahul@example.com",
      role = "mentee",
      mentorId = "mentor-uid-456",
      active = true
    )
    SessionManager.saveSession(context, "dummy-token", profile)
    val saved = SessionManager.getSavedProfile(context)
    assertNotNull(saved)
    assertEquals("test-uid-123", saved?.id)
    assertEquals("Bhakta Rahul", saved?.name)
    assertEquals("rahul@example.com", saved?.email)
    assertEquals("mentee", saved?.role)
    assertEquals("mentor-uid-456", saved?.mentorId)
    SessionManager.clearSession(context)
  }

  @Test
  fun `sadhna submission stores japa finish time correctly`() {
    val submission = SadhnaSubmission(
      menteeId = "mentee-1",
      mentorId = "mentor-1",
      devoteeName = "Bhakta Test",
      date = "03/09/2026",
      wakeUpTime = "04:30",
      japaRounds = 16,
      japaFinishTime = "06:45"
    )
    assertEquals("06:45", submission.japaFinishTime)
    assertEquals(16, submission.japaRounds)
    assertEquals("04:30", submission.wakeUpTime)
  }
}
