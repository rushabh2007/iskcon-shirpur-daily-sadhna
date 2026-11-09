package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserProfile(
  val id: String,
  val name: String,
  val email: String,
  val role: String, // "admin", "mentor", "mentee"
  val mentorId: String? = null,
  val mentorName: String? = null,
  val active: Boolean = true,
  val organizationId: String = "shirpur",
  val organizationName: String = "ISKCON Shirpur",
  val createdAt: Any? = null
)

data class MentorItem(
  val id: String,
  val name: String,
  val email: String = "",
  val organizationId: String = "shirpur",
  val organizationName: String = "ISKCON Shirpur"
)

data class SadhnaSubmission(
  val id: String? = null,
  val menteeId: String,
  val mentorId: String? = null,
  val devoteeName: String? = null,
  val organizationId: String = "shirpur",
  val date: String,
  val wakeUpTime: String,
  val spiritualActivities: List<String> = emptyList(),
  val japaRounds: Int = 0,
  val japaCompletedHours: String = "",
  val japaCompletedMinutes: String = "",
  val japaFinishTime: String = "",
  val bookReadingSubject: String = "",
  val bookReadingTime: String = "",
  val hearingSubject: String = "",
  val hearingTime: String = "",
  val sevaCompleted: Boolean = false,
  val sevaHours: String = "",
  val sevaMinutes: String = "",
  val sevaDescription: String = "",
  val sleepingTime: String = "",
  val createdAt: Any? = null
)

object SessionManager {
  private const val TAG = "SessionManager"
  private const val PREFS_NAME = "sadhna_app_session_prefs"
  private const val KEY_ACCESS_TOKEN = "access_token"
  private const val KEY_USER_ID = "user_id"
  private const val KEY_USER_NAME = "user_name"
  private const val KEY_USER_EMAIL = "user_email"
  private const val KEY_USER_ROLE = "user_role"
  private const val KEY_MENTOR_ID = "mentor_id"
  private const val KEY_ORG_ID = "org_id"
  private const val KEY_ORG_NAME = "org_name"
  private const val KEY_SELECTED_ORG_ID = "selected_org_id"
  private const val KEY_SELECTED_ORG_NAME = "selected_org_name"

  const val DEFAULT_ORG_ID = "shirpur"
  const val DEFAULT_ORG_NAME = "ISKCON Shirpur"
  const val SUPER_ADMIN_EMAIL = "iskconshirpur@gmail.com"
  const val ADMIN_REQUEST_EMAIL = "shirpuriskcon@gmail.com"
  const val ADMIN_PASSCODE = "ISKCON@2026"

  data class OrgInfo(
    val id: String,
    val name: String,
    val active: Boolean = true,
    val city: String = "",
    val description: String = ""
  )

  // Dynamic in-memory cache seeded with default organization
  val DEFAULT_ORGANIZATIONS = listOf(
    OrgInfo(DEFAULT_ORG_ID, DEFAULT_ORG_NAME, active = true, city = "Shirpur", description = "ISKCON Shirpur Centre")
  )
  val ORGANIZATIONS = DEFAULT_ORGANIZATIONS

  private val orgNameCache = java.util.concurrent.ConcurrentHashMap<String, String>().apply {
    put(DEFAULT_ORG_ID.lowercase(), DEFAULT_ORG_NAME)
  }

  fun getOrgName(orgId: String): String {
    val cleanId = orgId.trim().lowercase()
    orgNameCache[cleanId]?.let { return it }
    val formatted = if (cleanId.startsWith("iskcon", ignoreCase = true)) {
      orgId
    } else {
      "ISKCON ${cleanId.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
    }
    return formatted
  }

  /**
   * Save the currently selected organization for pre-login context
   */
  fun saveSelectedOrg(context: Context, orgId: String, orgName: String) {
    val prefs = getPrefs(context)
    prefs.edit()
      .putString(KEY_SELECTED_ORG_ID, orgId.trim())
      .putString(KEY_SELECTED_ORG_NAME, orgName.trim())
      .apply()
    orgNameCache[orgId.trim().lowercase()] = orgName.trim()
  }

  /**
   * Retrieve the currently selected organization
   */
  fun getSelectedOrg(context: Context): OrgInfo {
    val prefs = getPrefs(context)
    val id = prefs.getString(KEY_SELECTED_ORG_ID, DEFAULT_ORG_ID) ?: DEFAULT_ORG_ID
    val name = prefs.getString(KEY_SELECTED_ORG_NAME, DEFAULT_ORG_NAME) ?: DEFAULT_ORG_NAME
    return OrgInfo(id = id, name = name, city = "Shirpur", description = "ISKCON Temple")
  }

  /**
   * Realtime listener for dynamic organizations list from Firestore 'organizations' collection.
   * Updates on-the-fly when a new organization is added.
   */
  fun listenToOrganizations(
    onUpdate: (List<OrgInfo>) -> Unit,
    onError: (Exception) -> Unit = {}
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("organizations")
      .whereEqualTo("active", true)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "listenToOrganizations snapshot error: ${error.message}")
          onError(error)
          return@addSnapshotListener
        }

        val list = snapshot?.documents?.mapNotNull { doc ->
          val active = doc.getBoolean("active") ?: true
          if (!active) return@mapNotNull null
          val id = doc.id.trim()
          val name = doc.getString("name")?.trim() ?: return@mapNotNull null
          val city = doc.getString("city")?.trim() ?: ""
          val description = doc.getString("description")?.trim() ?: ""
          orgNameCache[id.lowercase()] = name
          OrgInfo(id = id, name = name, active = true, city = city, description = description)
        }?.toMutableList() ?: mutableListOf()

        // Ensure default ISKCON Shirpur is present
        if (list.none { it.id.equals(DEFAULT_ORG_ID, ignoreCase = true) }) {
          list.add(0, OrgInfo(DEFAULT_ORG_ID, DEFAULT_ORG_NAME, active = true, city = "Shirpur", description = "ISKCON Shirpur Centre"))
        }

        // Sort by name
        val sortedList = list.sortedWith(compareBy({ !it.id.equals(DEFAULT_ORG_ID, ignoreCase = true) }, { it.name.lowercase(Locale.getDefault()) }))
        onUpdate(sortedList)
      }
  }

  /**
   * One-time direct fetch of active organizations.
   */
  suspend fun fetchOrganizations(): List<OrgInfo> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snap = firestore.collection("organizations")
        .whereEqualTo("active", true)
        .get().awaitTask()
      if (!snap.isEmpty) {
        val list = snap.documents.mapNotNull { doc ->
          val active = doc.getBoolean("active") ?: true
          if (!active) return@mapNotNull null
          val id = doc.id.trim()
          val name = doc.getString("name")?.trim() ?: return@mapNotNull null
          orgNameCache[id.lowercase()] = name
          OrgInfo(id = id, name = name, active = true)
        }.toMutableList()

        if (list.none { it.id.equals(DEFAULT_ORG_ID, ignoreCase = true) }) {
          list.add(0, OrgInfo(DEFAULT_ORG_ID, DEFAULT_ORG_NAME))
        }
        return@withContext list.sortedWith(compareBy({ !it.id.equals(DEFAULT_ORG_ID, ignoreCase = true) }, { it.name.lowercase(Locale.getDefault()) }))
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchOrganizations notice: ${e.message}")
    }
    listOf(OrgInfo(DEFAULT_ORG_ID, DEFAULT_ORG_NAME))
  }

  /**
   * Main Admin action: Create a new organization and initialize its primary Admin account.
   */
  suspend fun createOrganizationAndAdmin(
    context: Context,
    orgId: String,
    orgName: String,
    adminName: String,
    adminEmail: String,
    adminPassword: String
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val currentUser = auth.currentUser
        ?: return@withContext Result.failure(Exception("Unauthorized: No authenticated user session."))

      val firestore = FirebaseFirestore.getInstance()
      val userEmail = currentUser.email?.lowercase(Locale.getDefault())?.trim() ?: ""

      var userRole = ""
      try {
        val userDoc = firestore.collection("users").document(currentUser.uid).get().awaitTask()
        if (userDoc.exists()) {
          userRole = userDoc.getString("role")?.lowercase(Locale.getDefault())?.trim() ?: ""
        }
      } catch (e: Exception) {
        Log.w(TAG, "Notice reading user doc in createOrganizationAndAdmin: ${e.message}")
      }

      val isAuthorizedSuperAdmin = userRole == "super_admin" ||
        userEmail == SUPER_ADMIN_EMAIL ||
        userEmail == "rushabhmunot2007@gmail.com"

      if (!isAuthorizedSuperAdmin) {
        return@withContext Result.failure(Exception("Access Denied: Only Super Admin is authorized to create organisations."))
      }

      // Ensure user doc has super_admin role
      if (userRole != "super_admin" && (userEmail == SUPER_ADMIN_EMAIL || userEmail == "rushabhmunot2007@gmail.com")) {
        try {
          firestore.collection("users").document(currentUser.uid).update("role", "super_admin").awaitTask()
        } catch (_: Exception) {}
      }

      val cleanOrgId = orgId.trim().lowercase().replace("[^a-z0-9_]".toRegex(), "_")
      val cleanOrgName = orgName.trim()

      if (cleanOrgId.isEmpty() || cleanOrgName.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Organization ID and Name cannot be empty."))
      }

      // 1. Create document in 'organizations' collection
      val orgDoc = mapOf(
        "id" to cleanOrgId,
        "name" to cleanOrgName,
        "active" to true,
        "createdAt" to FieldValue.serverTimestamp()
      )
      firestore.collection("organizations").document(cleanOrgId).set(orgDoc, SetOptions.merge()).awaitTask()
      orgNameCache[cleanOrgId] = cleanOrgName

      // 2. Create the new Admin account in Firebase Auth & users collection using Secondary App
      val defaultApp = FirebaseApp.getInstance()
      val secondaryAppName = "SecondaryAdminAuthApp"
      val secondaryApp = try {
        FirebaseApp.getInstance(secondaryAppName)
      } catch (e: Exception) {
        FirebaseApp.initializeApp(context.applicationContext, defaultApp.options, secondaryAppName)
      }

      val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
      val authResult = secondaryAuth.createUserWithEmailAndPassword(adminEmail.trim(), adminPassword).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Failed to create admin auth user."))

      val uid = firebaseUser.uid
      val adminMap = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to adminName.trim(),
        "email" to adminEmail.trim().lowercase(),
        "role" to "admin",
        "active" to true,
        "organizationId" to cleanOrgId,
        "organizationName" to cleanOrgName,
        "createdAt" to FieldValue.serverTimestamp()
      )

      try {
        val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
        secFirestore.collection("users").document(uid).set(adminMap).awaitTask()
      } catch (_: Exception) {
        firestore.collection("users").document(uid).set(adminMap).awaitTask()
      }

      try {
        firestore.collection("users").document(uid).set(adminMap).awaitTask()
      } catch (_: Exception) {}

      try {
        secondaryAuth.signOut()
      } catch (_: Exception) {}

      Result.success(UserProfile(
        id = uid,
        name = adminName.trim(),
        email = adminEmail.trim().lowercase(),
        role = "admin",
        active = true,
        organizationId = cleanOrgId,
        organizationName = cleanOrgName
      ))
    } catch (e: Exception) {
      Log.e(TAG, "createOrganizationAndAdmin error: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun ensureShirpurOrganizationTagged(currentOrgId: String) = withContext(Dispatchers.IO) {
    if (currentOrgId != DEFAULT_ORG_ID) return@withContext
    try {
      val firestore = FirebaseFirestore.getInstance()
      val usersSnap = firestore.collection("users").get().awaitTask()
      val batch = firestore.batch()
      var count = 0
      for (doc in usersSnap.documents) {
        val orgId = doc.getString("organizationId")
        if (orgId.isNullOrEmpty()) {
          batch.update(doc.reference, mapOf(
            "organizationId" to DEFAULT_ORG_ID,
            "organizationName" to DEFAULT_ORG_NAME
          ))
          count++
          if (count >= 400) {
            batch.commit().awaitTask()
            count = 0
          }
        }
      }
      if (count > 0) {
        batch.commit().awaitTask()
      }

      val mentorsSnap = firestore.collection("mentor_profiles").get().awaitTask()
      val mBatch = firestore.batch()
      var mCount = 0
      for (doc in mentorsSnap.documents) {
        val orgId = doc.getString("organizationId")
        if (orgId.isNullOrEmpty()) {
          mBatch.update(doc.reference, mapOf(
            "organizationId" to DEFAULT_ORG_ID,
            "organizationName" to DEFAULT_ORG_NAME
          ))
          mCount++
        }
      }
      if (mCount > 0) {
        mBatch.commit().awaitTask()
      }

      val sadhnaSnap = firestore.collection("sadhna_records").get().awaitTask()
      val sBatch = firestore.batch()
      var sCount = 0
      for (doc in sadhnaSnap.documents) {
        val orgId = doc.getString("organizationId")
        if (orgId.isNullOrEmpty()) {
          sBatch.update(doc.reference, "organizationId", DEFAULT_ORG_ID)
          sCount++
          if (sCount >= 400) {
            sBatch.commit().awaitTask()
            sCount = 0
          }
        }
      }
      if (sCount > 0) {
        sBatch.commit().awaitTask()
      }
    } catch (e: Exception) {
      Log.w(TAG, "ensureShirpurOrganizationTagged notice: ${e.message}")
    }
  }

  private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
      cont.resume(result)
    }
    addOnFailureListener { exception ->
      cont.resumeWithException(exception)
    }
    addOnCanceledListener {
      cont.cancel()
    }
  }

  private fun getPrefs(context: Context): SharedPreferences {
    return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun saveSession(context: Context, token: String, profile: UserProfile) {
    getPrefs(context).edit()
      .putString(KEY_ACCESS_TOKEN, token)
      .putString(KEY_USER_ID, profile.id)
      .putString(KEY_USER_NAME, profile.name)
      .putString(KEY_USER_EMAIL, profile.email)
      .putString(KEY_USER_ROLE, profile.role)
      .putString(KEY_MENTOR_ID, profile.mentorId)
      .putString(KEY_ORG_ID, profile.organizationId)
      .putString(KEY_ORG_NAME, profile.organizationName)
      .apply()
  }

  fun getSavedProfile(context: Context): UserProfile? {
    val prefs = getPrefs(context)
    val id = prefs.getString(KEY_USER_ID, null) ?: return null
    val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    val name = prefs.getString(KEY_USER_NAME, "Devotee") ?: "Devotee"
    val role = prefs.getString(KEY_USER_ROLE, "mentee") ?: "mentee"
    val mentorId = prefs.getString(KEY_MENTOR_ID, null)
    val orgId = prefs.getString(KEY_ORG_ID, DEFAULT_ORG_ID) ?: DEFAULT_ORG_ID
    val orgName = prefs.getString(KEY_ORG_NAME, DEFAULT_ORG_NAME) ?: DEFAULT_ORG_NAME
    return UserProfile(
      id = id,
      name = name,
      email = email,
      role = role,
      mentorId = mentorId,
      active = true,
      organizationId = orgId,
      organizationName = orgName
    )
  }

  fun getAccessToken(context: Context): String? {
    return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
  }

  fun clearSession(context: Context) {
    try {
      FirebaseAuth.getInstance().signOut()
    } catch (e: Exception) {
      Log.e(TAG, "FirebaseAuth signOut error: ${e.message}")
    }
    getPrefs(context).edit().clear().apply()
    SadhnaReminderManager.cancelReminderNotification(context)
  }

  /**
   * Real-time listener for Counsellors/Mentors querying Firestore 'users' collection as the single source of truth.
   * Used for the Counsellee Create Account dropdown.
   * When a Counsellor is deleted, created, or edited in Firestore, the dropdown automatically reflects changes in realtime.
   * Filtered by active == true and organizationId == selectedOrganizationId.
   */
  fun listenToActiveMentors(
    organizationId: String? = null,
    onUpdate: (List<MentorItem>) -> Unit,
    onError: (Exception) -> Unit = {}
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    val mentorsMap = mutableMapOf<String, MentorItem>()
    val profilesMap = mutableMapOf<String, MentorItem>()

    fun emitCombined() {
      val combined = HashMap<String, MentorItem>()
      combined.putAll(mentorsMap)
      combined.putAll(profilesMap)

      val targetOrg = organizationId?.trim()
      val matched = combined.values.filter { item ->
        if (targetOrg.isNullOrEmpty()) {
          true
        } else {
          item.organizationId.equals(targetOrg, ignoreCase = true) ||
          item.organizationName.equals(targetOrg, ignoreCase = true) ||
          getOrgName(item.organizationId).equals(getOrgName(targetOrg), ignoreCase = true) ||
          (item.organizationId.equals(DEFAULT_ORG_ID, ignoreCase = true) && targetOrg.equals(DEFAULT_ORG_NAME, ignoreCase = true)) ||
          (targetOrg.equals(DEFAULT_ORG_ID, ignoreCase = true) && item.organizationId.equals(DEFAULT_ORG_NAME, ignoreCase = true))
        }
      }.sortedBy { it.name.lowercase(Locale.getDefault()) }

      // If matching by organization yields results, use them; otherwise fallback to all active counsellors so devotees are never blocked
      val finalList = if (matched.isNotEmpty()) matched else combined.values.sortedBy { it.name.lowercase(Locale.getDefault()) }

      onUpdate(finalList)
    }

    fun parseDoc(doc: com.google.firebase.firestore.DocumentSnapshot): MentorItem? {
      val active = doc.getBoolean("active") ?: true
      if (!active) return null
      val name = doc.getString("name")?.trim() ?: return null
      if (name.isEmpty()) return null
      val email = doc.getString("email")?.trim() ?: ""
      val orgId = doc.getString("orgId")?.trim()?.ifEmpty { null }
        ?: doc.getString("organizationId")?.trim()?.ifEmpty { null }
        ?: DEFAULT_ORG_ID
      val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null }
        ?: getOrgName(orgId)

      return MentorItem(
        id = doc.id,
        name = name,
        email = email,
        organizationId = orgId,
        organizationName = orgName
      )
    }

    // 1. Realtime listener on 'users' collection for roles: mentor, Mentor, counsellor, Counsellor
    val reg1 = firestore.collection("users")
      .whereIn("role", listOf("mentor", "Mentor", "counsellor", "Counsellor"))
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "users mentor listener error: ${error.message}")
          onError(error)
        } else if (snapshot != null) {
          mentorsMap.clear()
          for (doc in snapshot.documents) {
            val item = parseDoc(doc)
            if (item != null) {
              mentorsMap[doc.id] = item
            }
          }
          emitCombined()
        }
      }

    // 2. Fallback listener on 'mentor_profiles' collection
    val reg2 = firestore.collection("mentor_profiles")
      .addSnapshotListener { snapshot, error ->
        if (error == null && snapshot != null) {
          profilesMap.clear()
          for (doc in snapshot.documents) {
            val item = parseDoc(doc)
            if (item != null) {
              profilesMap[doc.id] = item
            }
          }
          emitCombined()
        }
      }

    return ListenerRegistration {
      reg1.remove()
      reg2.remove()
    }
  }

  /**
   * Real-time listener for an individual user's profile
   * Allows mentee/mentor to receive real-time updates if an admin updates their profile/mentor assignment.
   */
  fun listenToUserProfile(
    uid: String,
    onUpdate: (UserProfile?) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("users").document(uid)
      .addSnapshotListener { doc, error ->
        if (error != null) {
          Log.e(TAG, "listenToUserProfile error: ${error.message}", error)
          onError(error)
          return@addSnapshotListener
        }
        if (doc == null || !doc.exists()) {
          onUpdate(null)
          return@addSnapshotListener
        }

        val orgId = doc.getString("organizationId")?.trim()?.ifEmpty { null } ?: DEFAULT_ORG_ID
        val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)
        val profile = UserProfile(
          id = doc.id,
          name = doc.getString("name") ?: "Devotee",
          email = doc.getString("email") ?: "",
          role = doc.getString("role") ?: "mentee",
          mentorId = doc.getString("mentorId"),
          active = doc.getBoolean("active") ?: true,
          organizationId = orgId,
          organizationName = orgName,
          createdAt = doc.get("createdAt")
        )
        onUpdate(profile)
      }
  }

  /**
   * Real-time listener for a mentee's own Sadhna records
   */
  fun listenToSadhnaRecordsForMentee(
    menteeUid: String,
    onUpdate: (List<SadhnaSubmission>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("sadhna_records")
      .whereEqualTo("menteeId", menteeUid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "listenToSadhnaRecordsForMentee error: ${error.message}", error)
          onError(error)
          return@addSnapshotListener
        }
        val records = snapshot?.documents?.map { doc ->
          val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
          SadhnaSubmission(
            id = doc.id,
            menteeId = doc.getString("menteeId") ?: "",
            mentorId = doc.getString("mentorId"),
            devoteeName = doc.getString("devoteeName"),
            organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
            date = doc.getString("date") ?: "",
            wakeUpTime = doc.getString("wakeUpTime") ?: "",
            spiritualActivities = activities,
            japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
            japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
            japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
            japaFinishTime = doc.getString("japaFinishTime") ?: "",
            bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
            bookReadingTime = doc.getString("bookReadingTime") ?: "",
            hearingSubject = doc.getString("hearingSubject") ?: "",
            hearingTime = doc.getString("hearingTime") ?: "",
            sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
            sevaHours = doc.getString("sevaHours") ?: "",
            sevaMinutes = doc.getString("sevaMinutes") ?: "",
            sevaDescription = doc.getString("sevaDescription") ?: "",
            sleepingTime = doc.getString("sleepingTime") ?: "",
            createdAt = doc.get("createdAt")
          )
        } ?: emptyList()
        onUpdate(records)
      }
  }

  /**
   * Direct fetch of a mentee's Sadhna records (guarantees export gets all existing records)
   */
  suspend fun fetchSadhnaRecordsForMenteeDirectly(menteeUid: String): Result<List<SadhnaSubmission>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("sadhna_records")
        .whereEqualTo("menteeId", menteeUid)
        .get()
        .awaitTask()

      val records = snapshot.documents.map { doc ->
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: "",
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
          organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
          date = doc.getString("date") ?: "",
          wakeUpTime = doc.getString("wakeUpTime") ?: "",
          spiritualActivities = activities,
          japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
          japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
          japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
          japaFinishTime = doc.getString("japaFinishTime") ?: "",
          bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
          bookReadingTime = doc.getString("bookReadingTime") ?: "",
          hearingSubject = doc.getString("hearingSubject") ?: "",
          hearingTime = doc.getString("hearingTime") ?: "",
          sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
          sevaHours = doc.getString("sevaHours") ?: "",
          sevaMinutes = doc.getString("sevaMinutes") ?: "",
          sevaDescription = doc.getString("sevaDescription") ?: "",
          sleepingTime = doc.getString("sleepingTime") ?: "",
          createdAt = doc.get("createdAt")
        )
      }.sortedByDescending { it.date }

      Result.success(records)
    } catch (e: Exception) {
      Log.e(TAG, "fetchSadhnaRecordsForMenteeDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * One-time fetch of active counsellors/mentors directly from Firestore 'users' collection.
   */
  suspend fun fetchMentors(organizationId: String? = null): Result<List<MentorItem>> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val firestore = FirebaseFirestore.getInstance()

      if (auth.currentUser == null) {
        try {
          auth.signInAnonymously().awaitTask()
        } catch (authEx: Exception) {
          Log.w(TAG, "Anonymous sign in skipped/not available: ${authEx.message}")
        }
      }

      fun parseDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>?): List<MentorItem> {
        val list = docs?.mapNotNull { doc ->
          val role = doc.getString("role")?.trim()?.lowercase() ?: "mentor"
          if (role != "mentor" && role != "counsellor") return@mapNotNull null
          val name = doc.getString("name")?.trim() ?: return@mapNotNull null
          val email = doc.getString("email")?.trim() ?: ""
          val active = doc.getBoolean("active") ?: true
          if (!active) return@mapNotNull null
          val orgId = doc.getString("orgId")?.trim()?.ifEmpty { null }
            ?: doc.getString("organizationId")?.trim()?.ifEmpty { null }
            ?: DEFAULT_ORG_ID
          val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)
          MentorItem(
            id = doc.id,
            name = name,
            email = email,
            organizationId = orgId,
            organizationName = orgName
          )
        }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()

        val matched = if (organizationId != null && organizationId.isNotEmpty()) {
          list.filter {
            it.organizationId.equals(organizationId, ignoreCase = true) ||
            it.organizationName.equals(organizationId, ignoreCase = true) ||
            getOrgName(it.organizationId).equals(getOrgName(organizationId), ignoreCase = true) ||
            (it.organizationId.equals(DEFAULT_ORG_ID, ignoreCase = true) && organizationId.equals(DEFAULT_ORG_NAME, ignoreCase = true)) ||
            (organizationId.equals(DEFAULT_ORG_ID, ignoreCase = true) && it.organizationId.equals(DEFAULT_ORG_NAME, ignoreCase = true))
          }
        } else {
          list
        }
        return if (matched.isNotEmpty()) matched else list
      }

      val mentors = try {
        val snap = firestore.collection("users")
          .whereIn("role", listOf("mentor", "Mentor", "counsellor", "Counsellor"))
          .get()
          .awaitTask()
        parseDocs(snap.documents)
      } catch (e: Exception) {
        emptyList()
      }

      val profiles = try {
        val snap = firestore.collection("mentor_profiles").get().awaitTask()
        parseDocs(snap.documents)
      } catch (e: Exception) {
        emptyList()
      }

      val combined = (mentors + profiles).distinctBy { it.id }.sortedBy { it.name.lowercase(Locale.getDefault()) }
      Result.success(combined)
    } catch (e: Exception) {
      Log.w(TAG, "fetchMentors handled fallback: ${e.message}")
      Result.success(emptyList())
    }
  }

  /**
   * User Registration with Firebase Authentication (Email + Password)
   * Supports Mentee self-registration (with selected mentorId) and Admin registration (passcode-verified).
   */
  suspend fun registerUser(
    context: Context,
    name: String,
    email: String,
    password: String,
    role: String = "mentee",
    mentorId: String?,
    organizationId: String = DEFAULT_ORG_ID,
    organizationName: String = DEFAULT_ORG_NAME
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val firestore = FirebaseFirestore.getInstance()

      val targetRole = when (role.lowercase().trim()) {
        "admin", "administrator" -> "admin"
        else -> "mentee"
      }

      // 1. Create account in Firebase Auth (Secure password storage)
      val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Failed to initialize Firebase Auth account."))

      val uid = firebaseUser.uid

      // 2. Save user profile in Firestore users/{uid} (No passwords or passcodes stored!)
      val isCounsellee = targetRole == "mentee" || targetRole == "counselee"
      val initialMentorId = if (isCounsellee) null else null // Initially unassigned for counsellee pending approval

      val userProfileMap = hashMapOf<String, Any?>(
        "uid" to uid,
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "role" to if (isCounsellee) "counselee" else targetRole,
        "mentorId" to initialMentorId,
        "active" to true,
        "organizationId" to organizationId,
        "organizationName" to organizationName,
        "createdAt" to FieldValue.serverTimestamp()
      )

      firestore.collection("users").document(uid).set(userProfileMap).awaitTask()

      // If Counsellee registered with a chosen Counsellor, create pending request
      if (isCounsellee && !mentorId.isNullOrEmpty()) {
        val requestId = "${uid}_${mentorId}"
        val requestMap = hashMapOf<String, Any?>(
          "id" to requestId,
          "counseleeId" to uid,
          "counseleeName" to name.trim(),
          "counseleeEmail" to email.trim().lowercase(),
          "counsellorId" to mentorId,
          "orgId" to organizationId,
          "organizationName" to organizationName,
          "status" to "pending",
          "createdAt" to FieldValue.serverTimestamp(),
          "respondedAt" to null
        )
        try {
          firestore.collection("counsellor_requests").document(requestId).set(requestMap).awaitTask()
        } catch (reqEx: Exception) {
          Log.w(TAG, "Failed to create counsellor_request: ${reqEx.message}")
        }
      }

      val profile = UserProfile(
        id = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = if (isCounsellee) "counselee" else targetRole,
        mentorId = initialMentorId,
        active = true,
        organizationId = organizationId,
        organizationName = organizationName
      )

      val token = try {
        firebaseUser.getIdToken(false).awaitTask()?.token ?: uid
      } catch (_: Exception) {
        uid
      }

      saveSession(context, token, profile)

      Result.success(profile)
    } catch (e: Exception) {
      Log.e(TAG, "registerUser error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Unified Sign In using Firebase Authentication (Email + Password).
   * Automatically reads the user's role from users/{uid} and returns the profile.
   * Auto-heals missing user documents for authorized logins.
   */
  suspend fun signIn(
    email: String,
    password: String
  ): Result<Pair<String, UserProfile>> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val firestore = FirebaseFirestore.getInstance()
      val cleanEmail = email.trim()

      // 1. Sign in with Firebase Auth
      val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Authentication failed: No user found."))

      val uid = firebaseUser.uid

      // 2. Fetch user profile from Cloud Firestore users/{uid}
      var profile: UserProfile? = null
      try {
        val docSnapshot = firestore.collection("users").document(uid).get().awaitTask()
        if (docSnapshot.exists()) {
          var role = docSnapshot.getString("role")?.lowercase()?.trim() ?: "mentee"
          val active = docSnapshot.getBoolean("active") ?: true
          val name = docSnapshot.getString("name") ?: cleanEmail.substringBefore("@")
          val mentorId = docSnapshot.getString("mentorId")
          val orgId = docSnapshot.getString("organizationId")?.trim()?.ifEmpty { null } ?: DEFAULT_ORG_ID
          val orgName = docSnapshot.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)

          // Designated Super Admin check
          if (cleanEmail.equals("iskconshirpur@gmail.com", ignoreCase = true) || role == "super_admin") {
            role = "super_admin"
            if (docSnapshot.getString("role") != "super_admin") {
              try {
                firestore.collection("users").document(uid).update("role", "super_admin")
              } catch (_: Exception) {}
            }
          }

          if (!active) {
            auth.signOut()
            return@withContext Result.failure(Exception("Your account is currently deactivated. Please contact the administrator."))
          }

          // Legacy backward compatibility: If organizationId is missing, tag it with default
          if (!docSnapshot.contains("organizationId")) {
            try {
              firestore.collection("users").document(uid).update(
                "organizationId", DEFAULT_ORG_ID,
                "organizationName", DEFAULT_ORG_NAME
              )
            } catch (_: Exception) {}
          }

          profile = UserProfile(
            id = uid,
            name = name,
            email = cleanEmail,
            role = role,
            mentorId = mentorId,
            active = active,
            organizationId = orgId,
            organizationName = orgName
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error fetching user doc, attempting auto-heal: ${e.message}")
      }

      // Auto-heal: If profile document does not exist yet in Firestore, create it gracefully
      if (profile == null) {
        val inferredRole = when {
          cleanEmail.equals("iskconshirpur@gmail.com", ignoreCase = true) -> "super_admin"
          cleanEmail.equals("rushabhmunot2007@gmail.com", ignoreCase = true) ||
            cleanEmail.contains("admin", ignoreCase = true) -> "admin"
          cleanEmail.contains("mentor", ignoreCase = true) -> "mentor"
          else -> "mentee"
        }

        val inferredName = when (inferredRole) {
          "super_admin" -> "HG Super Admin"
          "admin" -> "HG Admin"
          "mentor" -> "HG ${cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }}"
          else -> cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }

        val userMap = hashMapOf<String, Any?>(
          "uid" to uid,
          "name" to inferredName,
          "email" to cleanEmail.lowercase(),
          "role" to inferredRole,
          "mentorId" to null,
          "active" to true,
          "organizationId" to DEFAULT_ORG_ID,
          "organizationName" to DEFAULT_ORG_NAME,
          "createdAt" to FieldValue.serverTimestamp()
        )

        try {
          firestore.collection("users").document(uid).set(userMap).awaitTask()
          if (inferredRole == "mentor") {
            firestore.collection("mentor_profiles").document(uid).set(userMap).awaitTask()
          }
        } catch (setEx: Exception) {
          Log.w(TAG, "Auto-heal set failed: ${setEx.message}")
        }

        profile = UserProfile(
          id = uid,
          name = inferredName,
          email = cleanEmail,
          role = inferredRole,
          mentorId = null,
          active = true,
          organizationId = DEFAULT_ORG_ID,
          organizationName = DEFAULT_ORG_NAME
        )
      }

      val token = try {
        firebaseUser.getIdToken(false).awaitTask()?.token ?: uid
      } catch (e: Exception) {
        uid
      }

      Result.success(Pair(token, profile))
    } catch (e: Exception) {
      Log.e(TAG, "signIn error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Save Daily Sadhna submission to Cloud Firestore sadhna_records collection.
   * Supports both Counsellors and Counsellees.
   * Prevents duplicate entries for the same date by updating existing records cleanly.
   */
  suspend fun saveSadhna(submission: SadhnaSubmission): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val currentUser = auth.currentUser
      val uid = currentUser?.uid ?: submission.menteeId.takeIf { it.isNotEmpty() && it != "anonymous_user" }
      if (uid.isNullOrEmpty()) {
        return@withContext Result.failure(Exception("Authentication required. Please log in to record your Sadhna."))
      }

      val firestore = FirebaseFirestore.getInstance()
      val cleanDate = submission.date.trim()
      val cleanOrgId = submission.organizationId.trim().ifEmpty { DEFAULT_ORG_ID }

      val recordMap = hashMapOf<String, Any?>(
        "menteeId" to uid,
        "mentorId" to submission.mentorId,
        "devoteeName" to (submission.devoteeName ?: "Devotee"),
        "organizationId" to cleanOrgId,
        "orgId" to cleanOrgId,
        "date" to cleanDate,
        "wakeUpTime" to submission.wakeUpTime,
        "spiritualActivities" to submission.spiritualActivities,
        "japaRounds" to submission.japaRounds,
        "japaCompletedHours" to submission.japaCompletedHours,
        "japaCompletedMinutes" to submission.japaCompletedMinutes,
        "japaFinishTime" to submission.japaFinishTime,
        "bookReadingSubject" to submission.bookReadingSubject,
        "bookReadingTime" to submission.bookReadingTime,
        "hearingSubject" to submission.hearingSubject,
        "hearingTime" to submission.hearingTime,
        "sevaCompleted" to submission.sevaCompleted,
        "sevaHours" to submission.sevaHours,
        "sevaMinutes" to submission.sevaMinutes,
        "sevaDescription" to submission.sevaDescription,
        "sleepingTime" to submission.sleepingTime,
        "updatedAt" to FieldValue.serverTimestamp()
      )

      // Query existing record for this user and date to avoid duplicate entries
      val existingQuery = firestore.collection("sadhna_records")
        .whereEqualTo("menteeId", uid)
        .whereEqualTo("date", cleanDate)
        .get()
        .awaitTask()

      if (!existingQuery.isEmpty) {
        val existingDoc = existingQuery.documents[0]
        recordMap["createdAt"] = existingDoc.get("createdAt") ?: FieldValue.serverTimestamp()
        firestore.collection("sadhna_records").document(existingDoc.id).set(recordMap, SetOptions.merge()).awaitTask()
        Log.i(TAG, "Updated existing Sadhna record ${existingDoc.id} for $uid on $cleanDate")
      } else {
        recordMap["createdAt"] = FieldValue.serverTimestamp()
        firestore.collection("sadhna_records").add(recordMap).awaitTask()
        Log.i(TAG, "Created new Sadhna record for $uid on $cleanDate")
      }

      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "saveSadhna error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Fetch a devotee's existing Sadhna record for a specific date (if already filled)
   */
  suspend fun fetchSadhnaRecordForDate(userId: String, date: String): SadhnaSubmission? = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("sadhna_records")
        .whereEqualTo("menteeId", userId)
        .whereEqualTo("date", date.trim())
        .limit(1)
        .get()
        .awaitTask()

      if (!snapshot.isEmpty) {
        val doc = snapshot.documents[0]
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: userId,
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
          organizationId = doc.getString("organizationId") ?: doc.getString("orgId") ?: DEFAULT_ORG_ID,
          date = doc.getString("date") ?: date,
          wakeUpTime = doc.getString("wakeUpTime") ?: "",
          spiritualActivities = activities,
          japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
          japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
          japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
          japaFinishTime = doc.getString("japaFinishTime") ?: "",
          bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
          bookReadingTime = doc.getString("bookReadingTime") ?: "",
          hearingSubject = doc.getString("hearingSubject") ?: "",
          hearingTime = doc.getString("hearingTime") ?: "",
          sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
          sevaHours = doc.getString("sevaHours") ?: "",
          sevaMinutes = doc.getString("sevaMinutes") ?: "",
          sevaDescription = doc.getString("sevaDescription") ?: "",
          sleepingTime = doc.getString("sleepingTime") ?: "",
          createdAt = doc.get("createdAt")
        )
      } else {
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "fetchSadhnaRecordForDate notice: ${e.message}")
      null
    }
  }

  /**
   * Real-time listener for mentees assigned to a mentor
   */
  fun listenToMenteesForMentor(
    mentorUid: String,
    organizationId: String? = null,
    onUpdate: (List<UserProfile>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("users")
      .whereEqualTo("role", "mentee")
      .whereEqualTo("mentorId", mentorUid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "listenToMenteesForMentor error: ${error.message}", error)
          onError(error)
          return@addSnapshotListener
        }
        val mentees = snapshot?.documents?.mapNotNull { doc ->
          val orgId = doc.getString("organizationId")?.trim()?.ifEmpty { null } ?: DEFAULT_ORG_ID
          if (!organizationId.isNullOrEmpty() && !orgId.equals(organizationId, ignoreCase = true)) {
            return@mapNotNull null
          }
          val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)
          UserProfile(
            id = doc.id,
            name = doc.getString("name") ?: "Devotee",
            email = doc.getString("email") ?: "",
            role = "mentee",
            mentorId = mentorUid,
            active = doc.getBoolean("active") ?: true,
            organizationId = orgId,
            organizationName = orgName
          )
        } ?: emptyList()
        onUpdate(mentees)
      }
  }

  /**
   * Real-time listener for Sadhna records belonging to a mentor's assigned mentees
   */
  fun listenToSadhnaRecordsForMentor(
    mentorUid: String,
    onUpdate: (List<SadhnaSubmission>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("sadhna_records")
      .whereEqualTo("mentorId", mentorUid)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "listenToSadhnaRecordsForMentor error: ${error.message}", error)
          onError(error)
          return@addSnapshotListener
        }
        val records = snapshot?.documents?.map { doc ->
          val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
          SadhnaSubmission(
            id = doc.id,
            menteeId = doc.getString("menteeId") ?: "",
            mentorId = doc.getString("mentorId"),
            devoteeName = doc.getString("devoteeName"),
            organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
            date = doc.getString("date") ?: "",
            wakeUpTime = doc.getString("wakeUpTime") ?: "",
            spiritualActivities = activities,
            japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
            japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
            japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
            japaFinishTime = doc.getString("japaFinishTime") ?: "",
            bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
            bookReadingTime = doc.getString("bookReadingTime") ?: "",
            hearingSubject = doc.getString("hearingSubject") ?: "",
            hearingTime = doc.getString("hearingTime") ?: "",
            sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
            sevaHours = doc.getString("sevaHours") ?: "",
            sevaMinutes = doc.getString("sevaMinutes") ?: "",
            sevaDescription = doc.getString("sevaDescription") ?: "",
            sleepingTime = doc.getString("sleepingTime") ?: "",
            createdAt = doc.get("createdAt")
          )
        } ?: emptyList()
        onUpdate(records)
      }
  }

  /**
   * Direct fetch of all Sadhna records belonging to a mentor's assigned mentees
   */
  suspend fun fetchSadhnaRecordsForMentorDirectly(mentorUid: String): Result<List<SadhnaSubmission>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("sadhna_records")
        .whereEqualTo("mentorId", mentorUid)
        .get()
        .awaitTask()

      val records = snapshot.documents.map { doc ->
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: "",
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
          organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
          date = doc.getString("date") ?: "",
          wakeUpTime = doc.getString("wakeUpTime") ?: "",
          spiritualActivities = activities,
          japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
          japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
          japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
          japaFinishTime = doc.getString("japaFinishTime") ?: "",
          bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
          bookReadingTime = doc.getString("bookReadingTime") ?: "",
          hearingSubject = doc.getString("hearingSubject") ?: "",
          hearingTime = doc.getString("hearingTime") ?: "",
          sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
          sevaHours = doc.getString("sevaHours") ?: "",
          sevaMinutes = doc.getString("sevaMinutes") ?: "",
          sevaDescription = doc.getString("sevaDescription") ?: "",
          sleepingTime = doc.getString("sleepingTime") ?: "",
          createdAt = doc.get("createdAt")
        )
      }.sortedByDescending { it.date }

      Result.success(records)
    } catch (e: Exception) {
      Log.e(TAG, "fetchSadhnaRecordsForMentorDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Mentor action: Remove/unassign a mentee from their mentorship list
   * Safely clears mentorId on the mentee doc without deleting their account or sadhna records.
   */
  suspend fun removeMenteeFromMentor(menteeUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("users").document(menteeUid).update("mentorId", null).awaitTask()

      // Also clear mentorId on existing sadhna records for this mentee
      try {
        val recordsSnap = firestore.collection("sadhna_records")
          .whereEqualTo("menteeId", menteeUid)
          .get()
          .awaitTask()
        if (!recordsSnap.isEmpty) {
          val batch = firestore.batch()
          for (doc in recordsSnap.documents) {
            batch.update(doc.reference, "mentorId", null)
          }
          batch.commit().awaitTask()
        }
      } catch (recErr: Exception) {
        Log.w(TAG, "Note clearing sadhna records mentorId: ${recErr.message}")
      }

      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "removeMenteeFromMentor error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin action: Reassign a mentee's mentor
   */
  suspend fun reassignMenteeMentor(menteeUid: String, newMentorId: String?): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("users").document(menteeUid).update("mentorId", newMentorId).awaitTask()

      // Also update sadhna records for this mentee so new mentor sees all submissions immediately
      try {
        val recordsSnap = firestore.collection("sadhna_records")
          .whereEqualTo("menteeId", menteeUid)
          .get()
          .awaitTask()
        if (!recordsSnap.isEmpty) {
          val batch = firestore.batch()
          for (doc in recordsSnap.documents) {
            batch.update(doc.reference, "mentorId", newMentorId)
          }
          batch.commit().awaitTask()
        }
      } catch (recErr: Exception) {
        Log.w(TAG, "Note updating sadhna records for reassigned mentee: ${recErr.message}")
      }

      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "reassignMenteeMentor error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Real-time listener for all users (Mentors and Mentees) scoped by organization
   */
  fun listenToAllUsers(
    organizationId: String = DEFAULT_ORG_ID,
    onUpdate: (List<UserProfile>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    var query: com.google.firebase.firestore.Query = firestore.collection("users")
    if (organizationId.isNotEmpty()) {
      query = query.whereEqualTo("organizationId", organizationId)
    }
    return query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.e(TAG, "listenToAllUsers error: ${error.message}", error)
        onError(error)
        return@addSnapshotListener
      }
      val users = snapshot?.documents?.map { doc ->
        val orgId = doc.getString("organizationId")?.trim()?.ifEmpty { null } ?: DEFAULT_ORG_ID
        val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)
        UserProfile(
          id = doc.id,
          name = doc.getString("name") ?: "User",
          email = doc.getString("email") ?: "",
          role = doc.getString("role") ?: "mentee",
          mentorId = doc.getString("mentorId"),
          active = doc.getBoolean("active") ?: true,
          organizationId = orgId,
          organizationName = orgName
        )
      } ?: emptyList()
      onUpdate(users)
    }
  }

  fun listenToAllUsers(
    onUpdate: (List<UserProfile>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    return listenToAllUsers(DEFAULT_ORG_ID, onUpdate, onError)
  }

  /**
   * Direct fetch of all users (for Admin Excel mapping) scoped by organization
   */
  suspend fun fetchAllUsersDirectly(organizationId: String = DEFAULT_ORG_ID): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      var query: com.google.firebase.firestore.Query = firestore.collection("users")
      if (organizationId.isNotEmpty()) {
        query = query.whereEqualTo("organizationId", organizationId)
      }
      val snapshot = query.get().awaitTask()
      val users = snapshot.documents.map { doc ->
        val orgId = doc.getString("organizationId")?.trim()?.ifEmpty { null } ?: DEFAULT_ORG_ID
        val orgName = doc.getString("organizationName")?.trim()?.ifEmpty { null } ?: getOrgName(orgId)
        UserProfile(
          id = doc.id,
          name = doc.getString("name") ?: "User",
          email = doc.getString("email") ?: "",
          role = doc.getString("role") ?: "mentee",
          mentorId = doc.getString("mentorId"),
          active = doc.getBoolean("active") ?: true,
          organizationId = orgId,
          organizationName = orgName
        )
      }
      Result.success(users)
    } catch (e: Exception) {
      Log.e(TAG, "fetchAllUsersDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Real-time listener for all Sadhna submissions scoped by organization
   */
  fun listenToAllSadhnaRecords(
    organizationId: String = DEFAULT_ORG_ID,
    onUpdate: (List<SadhnaSubmission>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    var query: com.google.firebase.firestore.Query = firestore.collection("sadhna_records")
    if (organizationId.isNotEmpty()) {
      query = query.whereEqualTo("organizationId", organizationId)
    }
    return query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.e(TAG, "listenToAllSadhnaRecords error: ${error.message}", error)
        onError(error)
        return@addSnapshotListener
      }
      val records = snapshot?.documents?.map { doc ->
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: "",
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
          organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
          date = doc.getString("date") ?: "",
          wakeUpTime = doc.getString("wakeUpTime") ?: "",
          spiritualActivities = activities,
          japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
          japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
          japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
          japaFinishTime = doc.getString("japaFinishTime") ?: "",
          bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
          bookReadingTime = doc.getString("bookReadingTime") ?: "",
          hearingSubject = doc.getString("hearingSubject") ?: "",
          hearingTime = doc.getString("hearingTime") ?: "",
          sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
          sevaHours = doc.getString("sevaHours") ?: "",
          sevaMinutes = doc.getString("sevaMinutes") ?: "",
          sevaDescription = doc.getString("sevaDescription") ?: "",
          sleepingTime = doc.getString("sleepingTime") ?: "",
          createdAt = doc.get("createdAt")
        )
      } ?: emptyList()
      onUpdate(records)
    }
  }

  fun listenToAllSadhnaRecords(
    onUpdate: (List<SadhnaSubmission>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    return listenToAllSadhnaRecords(DEFAULT_ORG_ID, onUpdate, onError)
  }

  /**
   * Direct fetch of all Sadhna records (guarantees Admin Export fetches all records) scoped by organization
   */
  suspend fun fetchAllSadhnaRecordsDirectly(organizationId: String = DEFAULT_ORG_ID): Result<List<SadhnaSubmission>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      var query: com.google.firebase.firestore.Query = firestore.collection("sadhna_records")
      if (organizationId.isNotEmpty()) {
        query = query.whereEqualTo("organizationId", organizationId)
      }
      val snapshot = query.get().awaitTask()
      val records = snapshot.documents.map { doc ->
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: "",
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
          organizationId = doc.getString("organizationId") ?: DEFAULT_ORG_ID,
          date = doc.getString("date") ?: "",
          wakeUpTime = doc.getString("wakeUpTime") ?: "",
          spiritualActivities = activities,
          japaRounds = doc.getLong("japaRounds")?.toInt() ?: 0,
          japaCompletedHours = doc.getString("japaCompletedHours") ?: "",
          japaCompletedMinutes = doc.getString("japaCompletedMinutes") ?: "",
          japaFinishTime = doc.getString("japaFinishTime") ?: "",
          bookReadingSubject = doc.getString("bookReadingSubject") ?: "",
          bookReadingTime = doc.getString("bookReadingTime") ?: "",
          hearingSubject = doc.getString("hearingSubject") ?: "",
          hearingTime = doc.getString("hearingTime") ?: "",
          sevaCompleted = doc.getBoolean("sevaCompleted") ?: false,
          sevaHours = doc.getString("sevaHours") ?: "",
          sevaMinutes = doc.getString("sevaMinutes") ?: "",
          sevaDescription = doc.getString("sevaDescription") ?: "",
          sleepingTime = doc.getString("sleepingTime") ?: "",
          createdAt = doc.get("createdAt")
        )
      }.sortedByDescending { it.date }

      Result.success(records)
    } catch (e: Exception) {
      Log.e(TAG, "fetchAllSadhnaRecordsDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Add a new mentor. Uses secondary FirebaseApp to avoid disturbing the logged-in admin session.
   */
  suspend fun addMentor(
    context: Context,
    name: String,
    email: String,
    password: String,
    organizationId: String? = null,
    organizationName: String? = null
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val creatorProfile = getSavedProfile(context)
      val targetOrgId = organizationId ?: creatorProfile?.organizationId ?: DEFAULT_ORG_ID
      val targetOrgName = organizationName ?: creatorProfile?.organizationName ?: getOrgName(targetOrgId)

      val defaultApp = FirebaseApp.getInstance()
      val secondaryAppName = "SecondaryAdminAuthApp"
      val secondaryApp = try {
        FirebaseApp.getInstance(secondaryAppName)
      } catch (e: Exception) {
        FirebaseApp.initializeApp(context.applicationContext, defaultApp.options, secondaryAppName)
      }

      val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
      val authResult = secondaryAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Failed to create mentor account."))

      val uid = firebaseUser.uid

      val mentorMap = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "role" to "mentor",
        "active" to true,
        "organizationId" to targetOrgId,
        "organizationName" to targetOrgName,
        "createdAt" to FieldValue.serverTimestamp()
      )

      // Write via defaultFirestore (logged-in Admin has full permission)
      val defaultFirestore = FirebaseFirestore.getInstance()
      try {
        defaultFirestore.collection("users").document(uid).set(mentorMap).awaitTask()
      } catch (e: Exception) {
        try {
          val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
          secFirestore.collection("users").document(uid).set(mentorMap).awaitTask()
        } catch (_: Exception) {}
      }

      try {
        defaultFirestore.collection("mentor_profiles").document(uid).set(mentorMap).awaitTask()
      } catch (e: Exception) {
        try {
          val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
          secFirestore.collection("mentor_profiles").document(uid).set(mentorMap).awaitTask()
        } catch (_: Exception) {}
      }

      // Clean up secondary auth session
      try {
        secondaryAuth.signOut()
      } catch (_: Exception) {}

      Result.success(UserProfile(
        id = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = "mentor",
        active = true,
        organizationId = targetOrgId,
        organizationName = targetOrgName
      ))
    } catch (e: Exception) {
      Log.e(TAG, "addMentor error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Add a new admin user.
   */
  suspend fun addAdmin(
    context: Context,
    name: String,
    email: String,
    password: String,
    organizationId: String? = null,
    organizationName: String? = null
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val creatorProfile = getSavedProfile(context)
      val targetOrgId = organizationId ?: creatorProfile?.organizationId ?: DEFAULT_ORG_ID
      val targetOrgName = organizationName ?: creatorProfile?.organizationName ?: getOrgName(targetOrgId)

      val defaultApp = FirebaseApp.getInstance()
      val secondaryAppName = "SecondaryAdminAuthApp"
      val secondaryApp = try {
        FirebaseApp.getInstance(secondaryAppName)
      } catch (e: Exception) {
        FirebaseApp.initializeApp(context.applicationContext, defaultApp.options, secondaryAppName)
      }

      val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
      val authResult = secondaryAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Failed to create admin account."))

      val uid = firebaseUser.uid

      val adminMap = hashMapOf<String, Any>(
        "uid" to uid,
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "role" to "admin",
        "active" to true,
        "organizationId" to targetOrgId,
        "organizationName" to targetOrgName,
        "createdAt" to FieldValue.serverTimestamp()
      )

      try {
        val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
        secFirestore.collection("users").document(uid).set(adminMap).awaitTask()
      } catch (secErr: Exception) {
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(adminMap).awaitTask()
      }

      try {
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(adminMap).awaitTask()
      } catch (_: Exception) {}

      try {
        secondaryAuth.signOut()
      } catch (_: Exception) {}

      Result.success(UserProfile(
        id = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = "admin",
        active = true,
        organizationId = targetOrgId,
        organizationName = targetOrgName
      ))
    } catch (e: Exception) {
      Log.e(TAG, "addAdmin error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Delete/remove a mentor
   */
  suspend fun deleteMentor(mentorUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()

      // 1. Remove from mentor_profiles if present
      try {
        firestore.collection("mentor_profiles").document(mentorUid).delete().awaitTask()
      } catch (e: Exception) {
        Log.w(TAG, "Failed deleting from mentor_profiles: ${e.message}")
      }

      // 2. Remove from users collection
      firestore.collection("users").document(mentorUid).delete().awaitTask()

      // 3. Unassign any mentees whose mentor was this mentor
      val menteesSnapshot = firestore.collection("users")
        .whereEqualTo("role", "mentee")
        .whereEqualTo("mentorId", mentorUid)
        .get()
        .awaitTask()

      for (doc in menteesSnapshot.documents) {
        firestore.collection("users").document(doc.id).update("mentorId", null).awaitTask()
      }

      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "deleteMentor error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Add a new mentee user
   */
  suspend fun addMentee(
    context: Context,
    name: String,
    email: String,
    password: String,
    mentorId: String?,
    organizationId: String? = null,
    organizationName: String? = null
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val creatorProfile = getSavedProfile(context)
      val targetOrgId = organizationId ?: creatorProfile?.organizationId ?: DEFAULT_ORG_ID
      val targetOrgName = organizationName ?: creatorProfile?.organizationName ?: getOrgName(targetOrgId)

      val defaultApp = FirebaseApp.getInstance()
      val secondaryAppName = "SecondaryAdminAuthApp"
      val secondaryApp = try {
        FirebaseApp.getInstance(secondaryAppName)
      } catch (e: Exception) {
        FirebaseApp.initializeApp(context.applicationContext, defaultApp.options, secondaryAppName)
      }

      val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
      val authResult = secondaryAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
      val firebaseUser = authResult.user
        ?: return@withContext Result.failure(Exception("Failed to create mentee account."))

      val uid = firebaseUser.uid
      val menteeMap = hashMapOf<String, Any?>(
        "uid" to uid,
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "role" to "mentee",
        "mentorId" to mentorId,
        "active" to true,
        "organizationId" to targetOrgId,
        "organizationName" to targetOrgName,
        "createdAt" to FieldValue.serverTimestamp()
      )

      try {
        val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
        secFirestore.collection("users").document(uid).set(menteeMap).awaitTask()
      } catch (secErr: Exception) {
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(menteeMap).awaitTask()
      }

      try {
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(menteeMap).awaitTask()
      } catch (_: Exception) {}

      try {
        secondaryAuth.signOut()
      } catch (_: Exception) {}

      Result.success(UserProfile(
        id = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = "mentee",
        mentorId = mentorId,
        active = true,
        organizationId = targetOrgId,
        organizationName = targetOrgName
      ))
    } catch (e: Exception) {
      Log.e(TAG, "addMentee error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Edit user information (Name and Email, and optional mentorId)
   */
  suspend fun updateUserProfile(
    uid: String,
    name: String,
    email: String,
    role: String,
    mentorId: String? = null
  ): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val updates = mutableMapOf<String, Any?>(
        "name" to name.trim(),
        "email" to email.trim().lowercase()
      )
      if (role == "mentee") {
        updates["mentorId"] = mentorId
      }
      firestore.collection("users").document(uid).update(updates).awaitTask()

      if (role == "mentor") {
        try {
          firestore.collection("mentor_profiles").document(uid).update(
            mapOf("name" to name.trim(), "email" to email.trim().lowercase())
          ).awaitTask()
        } catch (e: Exception) {
          Log.w(TAG, "Failed updating mentor_profiles doc: ${e.message}")
        }
      }

      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "updateUserProfile error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Delete/remove a mentee
   */
  suspend fun deleteMentee(menteeUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("users").document(menteeUid).delete().awaitTask()
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "deleteMentee error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Delete an admin user (must not be the current admin)
   */
  suspend fun deleteAdmin(adminUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("users").document(adminUid).delete().awaitTask()
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "deleteAdmin error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Delete a Sadhna record
   */
  suspend fun deleteSadhnaRecord(recordId: String): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("sadhna_records").document(recordId).delete().awaitTask()
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "deleteSadhnaRecord error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Sync mentors to public mentor_profiles collection in Firestore so unauthenticated registration can read them.
   * Also purges any deleted mentors from mentor_profiles so the registration dropdown stays clean and realtime.
   */
  fun syncMentorsToPublicProfiles(mentors: List<UserProfile>) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()
        val currentMentorIds = mentors.map { it.id }.toSet()

        // 1. Update/Add active mentors
        for (m in mentors) {
          val docRef = firestore.collection("mentor_profiles").document(m.id)
          val data = mapOf(
            "uid" to m.id,
            "name" to m.name,
            "email" to m.email,
            "role" to "mentor",
            "active" to m.active,
            "organizationId" to (m.organizationId.ifEmpty { DEFAULT_ORG_ID }),
            "organizationName" to (m.organizationName.ifEmpty { getOrgName(m.organizationId) })
          )
          batch.set(docRef, data)
        }

        // 2. Remove any profiles in mentor_profiles that are no longer in the active mentors list
        try {
          val existingProfilesSnapshot = firestore.collection("mentor_profiles").get().awaitTask()
          for (doc in existingProfilesSnapshot.documents) {
            if (!currentMentorIds.contains(doc.id)) {
              batch.delete(doc.reference)
            }
          }
        } catch (fetchEx: Exception) {
          Log.w(TAG, "Notice fetching existing mentor_profiles: ${fetchEx.message}")
        }

        batch.commit().awaitTask()
      } catch (e: Exception) {
        Log.w(TAG, "syncMentorsToPublicProfiles notice: ${e.message}")
      }
    }
  }

  /**
   * Helper function to map Firebase & network exceptions into clear, friendly error messages.
   */
  fun formatErrorMessage(e: Throwable?): String {
    if (e == null) return "An unexpected error occurred. Please try again."
    val msg = e.message ?: ""
    return when {
      msg.contains("network", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true) ->
        "Network unavailable. Please check your internet connection and try again."
      msg.contains("password", ignoreCase = true) && (msg.contains("wrong", ignoreCase = true) || msg.contains("invalid", ignoreCase = true) || msg.contains("credential", ignoreCase = true)) ->
        "Invalid email or password. Please verify your credentials and try again."
      msg.contains("no user record", ignoreCase = true) || msg.contains("user-not-found", ignoreCase = true) ->
        "No account found with this email. Please check the email or create the account in Firebase Console."
      msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) ->
        "An account with this email already exists. Please login with your password."
      msg.contains("badly formatted", ignoreCase = true) || msg.contains("invalid-email", ignoreCase = true) ->
        "Please enter a valid email address."
      msg.contains("deactivated", ignoreCase = true) ->
        "Your account is currently deactivated. Please contact the temple administrator."
      msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
        "Access denied. Please ensure your account has the correct permissions."
      msg.isNotEmpty() -> msg
      else -> "Operation failed. Please try again."
    }
  }

  // =========================================================
  // COUNSELLOR REQUEST & APPROVAL WORKFLOW
  // =========================================================

  data class CounsellorRequest(
    val id: String = "",
    val counseleeId: String = "",
    val counseleeName: String = "",
    val counseleeEmail: String = "",
    val counsellorId: String = "",
    val counsellorName: String = "",
    val orgId: String = "",
    val organizationName: String = "",
    val status: String = "pending", // "pending", "approved", "rejected"
    val createdAt: Any? = null,
    val respondedAt: Any? = null
  )

  /**
   * Listen in real-time to pending requests for a specific Counsellor
   */
  fun listenToPendingCounsellorRequests(
    counsellorId: String,
    organizationId: String = DEFAULT_ORG_ID,
    onUpdate: (List<CounsellorRequest>) -> Unit,
    onError: (Exception) -> Unit = {}
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("counsellor_requests")
      .whereEqualTo("counsellorId", counsellorId)
      .whereEqualTo("status", "pending")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "listenToPendingCounsellorRequests error: ${error.message}")
          onError(error)
          return@addSnapshotListener
        }
        val requests = snapshot?.documents?.mapNotNull { doc ->
          val reqOrg = doc.getString("orgId") ?: ""
          if (organizationId.isNotEmpty() && reqOrg.isNotEmpty() && !reqOrg.equals(organizationId, ignoreCase = true)) {
            return@mapNotNull null
          }
          CounsellorRequest(
            id = doc.id,
            counseleeId = doc.getString("counseleeId") ?: "",
            counseleeName = doc.getString("counseleeName") ?: "Devotee",
            counseleeEmail = doc.getString("counseleeEmail") ?: "",
            counsellorId = doc.getString("counsellorId") ?: "",
            counsellorName = doc.getString("counsellorName") ?: "",
            orgId = reqOrg,
            organizationName = doc.getString("organizationName") ?: "",
            status = doc.getString("status") ?: "pending",
            createdAt = doc.get("createdAt"),
            respondedAt = doc.get("respondedAt")
          )
        } ?: emptyList()
        onUpdate(requests)
      }
  }

  /**
   * Listen to the latest request for a Counsellee to show pending/approved/rejected status
   */
  fun listenToCounselleeRequest(
    counseleeId: String,
    onUpdate: (CounsellorRequest?) -> Unit,
    onError: (Exception) -> Unit = {}
  ): ListenerRegistration {
    val authUid = FirebaseAuth.getInstance().currentUser?.uid
    val targetId = if (counseleeId.isNotEmpty()) counseleeId else (authUid ?: "")
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("counsellor_requests")
      .whereEqualTo("counseleeId", targetId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.w(TAG, "listenToCounselleeRequest error: ${error.message}")
          onError(error)
          return@addSnapshotListener
        }
        val reqDoc = snapshot?.documents?.maxByOrNull { doc ->
          (doc.getTimestamp("createdAt")?.seconds) ?: 0L
        }
        if (reqDoc != null) {
          val req = CounsellorRequest(
            id = reqDoc.id,
            counseleeId = reqDoc.getString("counseleeId") ?: "",
            counseleeName = reqDoc.getString("counseleeName") ?: "Devotee",
            counseleeEmail = reqDoc.getString("counseleeEmail") ?: "",
            counsellorId = reqDoc.getString("counsellorId") ?: "",
            counsellorName = reqDoc.getString("counsellorName") ?: "",
            orgId = reqDoc.getString("orgId") ?: "",
            organizationName = reqDoc.getString("organizationName") ?: "",
            status = reqDoc.getString("status") ?: "pending",
            createdAt = reqDoc.get("createdAt"),
            respondedAt = reqDoc.get("respondedAt")
          )
          onUpdate(req)
        } else {
          onUpdate(null)
        }
      }
  }

  /**
   * Counsellee sends a join request to a selected Counsellor.
   * Prevents duplicate pending requests for the same counsellee.
   */
  suspend fun sendCounsellorJoinRequest(
    counseleeId: String,
    counseleeName: String,
    counseleeEmail: String,
    counsellorId: String,
    counsellorName: String,
    orgId: String = DEFAULT_ORG_ID,
    organizationName: String = DEFAULT_ORG_NAME
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val authUid = auth.currentUser?.uid
      val effectiveCounseleeId = if (!authUid.isNullOrEmpty()) authUid else counseleeId.trim()
      val firestore = FirebaseFirestore.getInstance()

      // 1. Prevent duplicate pending requests for this counsellee
      try {
        val existingQuery = firestore.collection("counsellor_requests")
          .whereEqualTo("counseleeId", effectiveCounseleeId)
          .whereEqualTo("status", "pending")
          .get()
          .awaitTask()

        if (!existingQuery.isEmpty) {
          return@withContext Result.failure(Exception("You already have an active pending request. Please wait for the counsellor to respond."))
        }
      } catch (queryErr: Exception) {
        Log.w(TAG, "Duplicate check notice: ${queryErr.message}")
      }

      val requestId = "${effectiveCounseleeId}_${counsellorId}_${System.currentTimeMillis()}"
      val requestMap = hashMapOf<String, Any?>(
        "id" to requestId,
        "counseleeId" to effectiveCounseleeId,
        "counseleeName" to counseleeName.trim(),
        "counseleeEmail" to counseleeEmail.trim().lowercase(),
        "counsellorId" to counsellorId,
        "counsellorName" to counsellorName.trim(),
        "orgId" to orgId,
        "organizationName" to organizationName,
        "status" to "pending",
        "createdAt" to FieldValue.serverTimestamp(),
        "respondedAt" to null
      )

      firestore.collection("counsellor_requests").document(requestId).set(requestMap).awaitTask()
      Log.i(TAG, "Counsellee $effectiveCounseleeId sent join request $requestId to counsellor $counsellorId")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "sendCounsellorJoinRequest error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Accept / Approve a pending counsellee request:
   * Sets request status = "accepted" (and maintains compatibility with "approved")
   * and updates users/{counseleeId} mentorId = counsellorId atomically via Firestore batch
   */
  suspend fun acceptCounselleeRequest(
    requestId: String,
    counseleeId: String,
    counsellorId: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val batch = firestore.batch()

      val reqRef = firestore.collection("counsellor_requests").document(requestId)
      val userRef = firestore.collection("users").document(counseleeId)

      batch.update(reqRef, mapOf(
        "status" to "accepted",
        "respondedAt" to FieldValue.serverTimestamp()
      ))
      batch.update(userRef, "mentorId", counsellorId)

      batch.commit().awaitTask()
      Log.i(TAG, "Counsellor $counsellorId accepted request $requestId for counsellee $counseleeId atomically")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "acceptCounselleeRequest error: ${e.message}", e)
      Result.failure(e)
    }
  }

  // Backward compatibility alias
  suspend fun approveCounselleeRequest(
    requestId: String,
    counseleeId: String,
    counsellorId: String
  ): Result<Unit> = acceptCounselleeRequest(requestId, counseleeId, counsellorId)

  /**
   * Decline / Reject a pending counsellee request:
   * Sets request status = "declined" and leaves counsellee unassigned
   */
  suspend fun declineCounselleeRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("counsellor_requests").document(requestId).update(
        mapOf(
          "status" to "declined",
          "respondedAt" to FieldValue.serverTimestamp()
        )
      ).awaitTask()
      Log.i(TAG, "Request $requestId declined")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "declineCounselleeRequest error: ${e.message}", e)
      Result.failure(e)
    }
  }

  // Backward compatibility alias
  suspend fun rejectCounselleeRequest(requestId: String): Result<Unit> = declineCounselleeRequest(requestId)

  /**
   * Direct one-shot fetch of pending requests for a Counsellor (used by manual Refresh button)
   */
  suspend fun fetchPendingCounsellorRequestsDirectly(
    counsellorId: String,
    organizationId: String = DEFAULT_ORG_ID
  ): Result<List<CounsellorRequest>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("counsellor_requests")
        .whereEqualTo("counsellorId", counsellorId)
        .whereEqualTo("status", "pending")
        .get()
        .awaitTask()

      val requests = snapshot.documents.mapNotNull { doc ->
        val reqOrg = doc.getString("orgId") ?: ""
        if (organizationId.isNotEmpty() && reqOrg.isNotEmpty() && !reqOrg.equals(organizationId, ignoreCase = true)) {
          return@mapNotNull null
        }
        CounsellorRequest(
          id = doc.id,
          counseleeId = doc.getString("counseleeId") ?: "",
          counseleeName = doc.getString("counseleeName") ?: "Devotee",
          counseleeEmail = doc.getString("counseleeEmail") ?: "",
          counsellorId = doc.getString("counsellorId") ?: "",
          counsellorName = doc.getString("counsellorName") ?: "",
          orgId = reqOrg,
          organizationName = doc.getString("organizationName") ?: "",
          status = doc.getString("status") ?: "pending",
          createdAt = doc.get("createdAt"),
          respondedAt = doc.get("respondedAt")
        )
      }
      Result.success(requests)
    } catch (e: Exception) {
      Log.e(TAG, "fetchPendingCounsellorRequestsDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Direct one-shot fetch of latest request for a Counsellee (used by manual Refresh button)
   */
  suspend fun fetchCounselleeRequestDirectly(counseleeId: String): Result<CounsellorRequest?> = withContext(Dispatchers.IO) {
    try {
      val authUid = FirebaseAuth.getInstance().currentUser?.uid
      val targetId = if (counseleeId.isNotEmpty()) counseleeId else (authUid ?: "")
      if (targetId.isEmpty()) {
        return@withContext Result.success(null)
      }

      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("counsellor_requests")
        .whereEqualTo("counseleeId", targetId)
        .get()
        .awaitTask()

      val reqDoc = snapshot.documents.maxByOrNull { doc ->
        (doc.getTimestamp("createdAt")?.seconds) ?: 0L
      }
      if (reqDoc != null) {
        val req = CounsellorRequest(
          id = reqDoc.id,
          counseleeId = reqDoc.getString("counseleeId") ?: "",
          counseleeName = reqDoc.getString("counseleeName") ?: "Devotee",
          counseleeEmail = reqDoc.getString("counseleeEmail") ?: "",
          counsellorId = reqDoc.getString("counsellorId") ?: "",
          counsellorName = reqDoc.getString("counsellorName") ?: "",
          orgId = reqDoc.getString("orgId") ?: "",
          organizationName = reqDoc.getString("organizationName") ?: "",
          status = reqDoc.getString("status") ?: "pending",
          createdAt = reqDoc.get("createdAt"),
          respondedAt = reqDoc.get("respondedAt")
        )
        Result.success(req)
      } else {
        Result.success(null)
      }
    } catch (e: Exception) {
      Log.e(TAG, "fetchCounselleeRequestDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  // =========================================================
  // SADHNA DATA MANAGEMENT (DELETE ONLY SADHNA ACTIVITY RECORDS)
  // NEVER DELETES USERS, PROFILES, RELATIONSHIPS, OR CREDENTIALS
  // =========================================================

  /**
   * Batch deletes specified Sadhna records by document ID.
   * STRICTLY operates on the 'sadhna_records' collection only.
   * Zero user accounts or relationships can ever be touched.
   */
  suspend fun deleteSadhnaRecordsBatch(recordIds: List<String>): Result<Int> = withContext(Dispatchers.IO) {
    try {
      if (recordIds.isEmpty()) return@withContext Result.success(0)
      val firestore = FirebaseFirestore.getInstance()
      var count = 0
      for (chunk in recordIds.chunked(450)) {
        val batch = firestore.batch()
        for (id in chunk) {
          batch.delete(firestore.collection("sadhna_records").document(id))
        }
        batch.commit().awaitTask()
        count += chunk.size
      }
      Log.i(TAG, "Successfully deleted $count sadhna records safely (zero user accounts affected).")
      Result.success(count)
    } catch (e: Exception) {
      Log.e(TAG, "deleteSadhnaRecordsBatch error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Schedule a future Sadhna records deletion date (Admin authorization)
   */
  suspend fun scheduleSadhnaDeletion(
    scheduledDateStr: String,
    epochMs: Long,
    scopeDesc: String,
    scheduledBy: String
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val map = hashMapOf<String, Any?>(
        "scheduledDate" to scheduledDateStr,
        "epochMs" to epochMs,
        "scopeDesc" to scopeDesc,
        "scheduledBy" to scheduledBy,
        "status" to "scheduled",
        "createdAt" to FieldValue.serverTimestamp()
      )
      firestore.collection("system_settings").document("sadhna_data_management").set(map).awaitTask()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "scheduleSadhnaDeletion error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Retrieves active scheduled deletion notice if present
   */
  suspend fun getScheduledDeletionNotice(): Result<Map<String, Any>?> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val doc = firestore.collection("system_settings").document("sadhna_data_management").get().awaitTask()
      if (doc.exists() && doc.getString("status") == "scheduled") {
        Result.success(doc.data)
      } else {
        Result.success(null)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Cancels any active scheduled deletion
   */
  suspend fun cancelScheduledSadhnaDeletion(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      firestore.collection("system_settings").document("sadhna_data_management")
        .update("status", "cancelled").awaitTask()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Super Admin only: Create a new organization dynamically in Firestore
   */
  suspend fun createOrganizationBySuperAdmin(
    name: String,
    city: String,
    description: String
  ): Result<OrgInfo> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val currentUser = auth.currentUser
        ?: return@withContext Result.failure(Exception("Unauthorized: No authenticated user session."))

      val firestore = FirebaseFirestore.getInstance()
      val userEmail = currentUser.email?.lowercase(Locale.getDefault())?.trim() ?: ""

      // Check Firestore doc and local session
      var userRole = ""
      try {
        val userDoc = firestore.collection("users").document(currentUser.uid).get().awaitTask()
        if (userDoc.exists()) {
          userRole = userDoc.getString("role")?.lowercase(Locale.getDefault())?.trim() ?: ""
        }
      } catch (e: Exception) {
        Log.w(TAG, "Notice reading user doc in createOrganizationBySuperAdmin: ${e.message}")
      }

      val isAuthorizedSuperAdmin = userRole == "super_admin" ||
        userEmail == SUPER_ADMIN_EMAIL ||
        userEmail == "rushabhmunot2007@gmail.com"

      if (!isAuthorizedSuperAdmin) {
        return@withContext Result.failure(Exception("Access Denied: Only Super Admin is authorized to create organisations."))
      }

      // Ensure user doc has super_admin role
      if (userRole != "super_admin" && (userEmail == SUPER_ADMIN_EMAIL || userEmail == "rushabhmunot2007@gmail.com")) {
        try {
          firestore.collection("users").document(currentUser.uid).update("role", "super_admin").awaitTask()
        } catch (_: Exception) {
          try {
            val userMap = hashMapOf<String, Any?>(
              "uid" to currentUser.uid,
              "name" to "HG Super Admin",
              "email" to userEmail,
              "role" to "super_admin",
              "active" to true,
              "organizationId" to DEFAULT_ORG_ID,
              "organizationName" to DEFAULT_ORG_NAME,
              "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(currentUser.uid).set(userMap, SetOptions.merge()).awaitTask()
          } catch (_: Exception) {}
        }
      }

      val cleanName = name.trim()
      val cleanCity = city.trim()
      val cleanDesc = description.trim()

      if (cleanName.isEmpty()) {
        return@withContext Result.failure(Exception("Organization name cannot be empty."))
      }
      if (cleanCity.isEmpty()) {
        return@withContext Result.failure(Exception("City / location cannot be empty."))
      }

      // Generate a unique, URL-safe slug orgId
      val baseSlug = cleanCity.lowercase(Locale.getDefault())
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')
        .ifEmpty {
          cleanName.lowercase(Locale.getDefault())
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
        }
      val orgId = if (baseSlug.isNotEmpty()) baseSlug else "org-${System.currentTimeMillis()}"

      val docRef = firestore.collection("organizations").document(orgId)
      val finalOrgId = try {
        val existing = docRef.get().awaitTask()
        if (existing.exists()) {
          "$orgId-${System.currentTimeMillis() % 10000}"
        } else {
          orgId
        }
      } catch (_: Exception) {
        orgId
      }

      val orgMap = hashMapOf<String, Any?>(
        "id" to finalOrgId,
        "name" to cleanName,
        "city" to cleanCity,
        "description" to cleanDesc,
        "active" to true,
        "createdAt" to FieldValue.serverTimestamp()
      )

      firestore.collection("organizations").document(finalOrgId).set(orgMap, SetOptions.merge()).awaitTask()
      orgNameCache[finalOrgId.lowercase()] = cleanName

      Result.success(OrgInfo(
        id = finalOrgId,
        name = cleanName,
        active = true,
        city = cleanCity,
        description = cleanDesc
      ))
    } catch (e: Exception) {
      Log.e(TAG, "createOrganizationBySuperAdmin error: ${e.message}", e)
      Result.failure(e)
    }
  }
}
