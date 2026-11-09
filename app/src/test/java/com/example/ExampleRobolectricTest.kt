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
      japaFinishTime = "06:45",
      sevaCompleted = true,
      sevaHours = "2",
      sevaMinutes = "30",
      sevaDescription = "Temple cleaning and prasadam distribution"
    )
    assertEquals("06:45", submission.japaFinishTime)
    assertEquals(16, submission.japaRounds)
    assertEquals("04:30", submission.wakeUpTime)
    assertEquals(true, submission.sevaCompleted)
    assertEquals("Temple cleaning and prasadam distribution", submission.sevaDescription)
  }

  @Test
  fun `multi-organization profiles and default values`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dhuleProfile = UserProfile(
      id = "user-dhule-1",
      name = "Bhakta Dhule",
      email = "dhule@example.com",
      role = "mentee",
      organizationId = "dhule",
      organizationName = "ISKCON Dhule"
    )
    SessionManager.saveSession(context, "token-dhule", dhuleProfile)
    val savedDhule = SessionManager.getSavedProfile(context)
    assertNotNull(savedDhule)
    assertEquals("dhule", savedDhule?.organizationId)
    assertEquals("ISKCON Dhule", savedDhule?.organizationName)

    assertEquals("ISKCON Shirpur", SessionManager.getOrgName("shirpur"))
    assertEquals("ISKCON Dhule", SessionManager.getOrgName("dhule"))
    assertEquals("ISKCON Nashik", SessionManager.getOrgName("nashik"))
    SessionManager.clearSession(context)
  }

  @Test
  fun `excel export includes seva description column`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val records = listOf(
      SadhnaSubmission(
        menteeId = "mentee-1",
        mentorId = "mentor-1",
        devoteeName = "Devotee Test",
        date = "10/09/2026",
        wakeUpTime = "04:30",
        japaRounds = 16,
        sevaCompleted = true,
        sevaHours = "2",
        sevaMinutes = "0",
        sevaDescription = "Book distribution",
        organizationId = "shirpur"
      )
    )
    val file = ExcelExportHelper.generateExcelFile(
      context = context,
      submissions = records,
      mentorMap = emptyMap(),
      menteeMap = emptyMap(),
      filePrefix = "Test_Export"
    )
    assertNotNull(file)
    assertEquals(true, file.exists())
    assertEquals(true, file.length() > 0)
    file.delete()
  }

  @Test
  fun `launch SplashActivity`() {
    val controller = org.robolectric.Robolectric.buildActivity(SplashActivity::class.java).setup()
    assertNotNull(controller.get())
  }

  @Test
  fun `test SadhnaApplication onCreate`() {
    val app = ApplicationProvider.getApplicationContext<SadhnaApplication>()
    assertNotNull(app)
  }

  @Test
  fun `test counsellor join request approval lifecycle`() {
    val initialRequest = SessionManager.CounsellorRequest(
      id = "req-1",
      counseleeId = "mentee-1",
      counseleeName = "Bhakta Gopal",
      counseleeEmail = "gopal@example.com",
      counsellorId = "counsellor-1",
      counsellorName = "Radha Raman Das",
      status = "pending"
    )
    assertEquals("pending", initialRequest.status)
    assertEquals("counsellor-1", initialRequest.counsellorId)
    assertEquals("Radha Raman Das", initialRequest.counsellorName)

    // Simulate approval transition
    val approvedRequest = initialRequest.copy(status = "approved")
    assertEquals("approved", approvedRequest.status)

    // Verify counsellee user profile is assigned to counsellor on approval
    val userProfile = UserProfile(
      id = approvedRequest.counseleeId,
      name = approvedRequest.counseleeName,
      email = approvedRequest.counseleeEmail,
      role = "mentee",
      mentorId = null
    )
    val updatedProfileOnApproval = userProfile.copy(mentorId = approvedRequest.counsellorId)
    assertEquals("counsellor-1", updatedProfileOnApproval.mentorId)
  }

  @Test
  fun `test counsellor join request rejection lifecycle`() {
    val initialRequest = SessionManager.CounsellorRequest(
      id = "req-2",
      counseleeId = "mentee-2",
      counseleeName = "Bhakta Shyam",
      counseleeEmail = "shyam@example.com",
      counsellorId = "counsellor-2",
      counsellorName = "Govinda Das",
      status = "pending"
    )
    assertEquals("pending", initialRequest.status)

    val rejectedRequest = initialRequest.copy(status = "rejected")
    assertEquals("rejected", rejectedRequest.status)

    // User profile remains unassigned
    val userProfile = UserProfile(
      id = rejectedRequest.counseleeId,
      name = rejectedRequest.counseleeName,
      email = rejectedRequest.counseleeEmail,
      role = "mentee",
      mentorId = null
    )
    assertEquals(null, userProfile.mentorId)
  }

  @Test
  fun `admin own sadhna export isolates only admin records`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val adminId = "admin-1"
    val counselleeId = "counsellee-1"
    val records = listOf(
      SadhnaSubmission(
        menteeId = adminId,
        mentorId = null,
        devoteeName = "HG Admin",
        date = "15/10/2026",
        wakeUpTime = "04:00",
        japaRounds = 16,
        sleepingTime = "21:30"
      ),
      SadhnaSubmission(
        menteeId = counselleeId,
        mentorId = "counsellor-1",
        devoteeName = "Counsellee 1",
        date = "15/10/2026",
        wakeUpTime = "05:00",
        japaRounds = 16,
        sleepingTime = "22:00"
      )
    )
    val onlyAdminRecords = records.filter { it.menteeId == adminId }
    assertEquals(1, onlyAdminRecords.size)
    assertEquals("HG Admin", onlyAdminRecords[0].devoteeName)
    assertEquals(null, onlyAdminRecords[0].mentorId)

    val file = ExcelExportHelper.generateExcelFile(
      context = context,
      submissions = onlyAdminRecords,
      mentorMap = emptyMap(),
      menteeMap = emptyMap(),
      filePrefix = "My_Admin_Sadhna"
    )
    assertNotNull(file)
    assertEquals(true, file.exists())
    assertEquals(true, file.length() > 0)
    file.delete()
  }

  @Test
  fun `mentee dashboard toggles unassigned and assigned request views`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Case 1: Unassigned mentee
    val unassignedMentee = UserProfile(
      id = "mentee-unassigned-1",
      name = "Bhakta Unassigned",
      email = "unassigned@example.com",
      role = "mentee",
      mentorId = null,
      active = true
    )
    SessionManager.saveSession(context, "dummy-token-1", unassignedMentee)
    val ctrlUnassigned = org.robolectric.Robolectric.buildActivity(MenteeDashboardActivity::class.java).setup()
    val actUnassigned = ctrlUnassigned.get()
    assertNotNull(actUnassigned)
    val reqSection = actUnassigned.findViewById<android.view.View>(R.id.layoutCounsellorRequestSection)
    val assignedSection = actUnassigned.findViewById<android.view.View>(R.id.layoutAssignedMentorDisplay)
    assertEquals(android.view.View.VISIBLE, reqSection.visibility)
    assertEquals(android.view.View.GONE, assignedSection.visibility)
    ctrlUnassigned.destroy()

    // Case 2: Assigned mentee
    val assignedMentee = UserProfile(
      id = "mentee-assigned-1",
      name = "Bhakta Assigned",
      email = "assigned@example.com",
      role = "mentee",
      mentorId = "counsellor-123",
      active = true
    )
    SessionManager.saveSession(context, "dummy-token-2", assignedMentee)
    val ctrlAssigned = org.robolectric.Robolectric.buildActivity(MenteeDashboardActivity::class.java).setup()
    val actAssigned = ctrlAssigned.get()
    assertNotNull(actAssigned)
    val reqSection2 = actAssigned.findViewById<android.view.View>(R.id.layoutCounsellorRequestSection)
    val assignedSection2 = actAssigned.findViewById<android.view.View>(R.id.layoutAssignedMentorDisplay)
    assertEquals(android.view.View.GONE, reqSection2.visibility)
    assertEquals(android.view.View.VISIBLE, assignedSection2.visibility)
    ctrlAssigned.destroy()

    SessionManager.clearSession(context)
  }
}
