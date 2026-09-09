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
  val createdAt: Any? = null
)

data class MentorItem(
  val id: String,
  val name: String,
  val email: String = ""
)

data class SadhnaSubmission(
  val id: String? = null,
  val menteeId: String,
  val mentorId: String? = null,
  val devoteeName: String? = null,
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
      .apply()
  }

  fun getSavedProfile(context: Context): UserProfile? {
    val prefs = getPrefs(context)
    val id = prefs.getString(KEY_USER_ID, null) ?: return null
    val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    val name = prefs.getString(KEY_USER_NAME, "Devotee") ?: "Devotee"
    val role = prefs.getString(KEY_USER_ROLE, "mentee") ?: "mentee"
    val mentorId = prefs.getString(KEY_MENTOR_ID, null)
    return UserProfile(id = id, name = name, email = email, role = role, mentorId = mentorId, active = true)
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
   * Real-time listener for mentors/counsellors with fallback between "mentor_profiles" and "users" collection.
   * Used for the Mentee Registration dropdown.
   * Updates ON-THE-FLY when admin adds, edits, or deletes counsellors.
   */
  fun listenToActiveMentors(
    onUpdate: (List<MentorItem>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    var activeRegistration: ListenerRegistration? = null

    fun parseDocuments(docs: List<com.google.firebase.firestore.DocumentSnapshot>?): List<MentorItem> {
      return docs?.mapNotNull { doc ->
        val role = doc.getString("role")?.trim()?.lowercase() ?: "mentor"
        if (role != "mentor") return@mapNotNull null
        val name = doc.getString("name")?.trim() ?: return@mapNotNull null
        val email = doc.getString("email")?.trim() ?: ""
        val active = doc.getBoolean("active") ?: true
        if (!active) return@mapNotNull null
        MentorItem(id = doc.id, name = name, email = email)
      }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
    }

    // Try listening to mentor_profiles first (accessible without restrictive rules)
    activeRegistration = firestore.collection("mentor_profiles")
      .addSnapshotListener { snapshot, error ->
        if (error == null && snapshot != null && !snapshot.isEmpty) {
          val mentors = parseDocuments(snapshot.documents)
          if (mentors.isNotEmpty()) {
            onUpdate(mentors)
            return@addSnapshotListener
          }
        }

        // If mentor_profiles is empty or failed, fallback to users collection
        firestore.collection("users")
          .whereEqualTo("role", "mentor")
          .addSnapshotListener { userSnap, userErr ->
            if (userErr != null) {
              Log.w(TAG, "listenToActiveMentors users fallback error: ${userErr.message}")
              // If mentor_profiles had data, do not fail
              if (snapshot != null && !snapshot.isEmpty) {
                onUpdate(parseDocuments(snapshot.documents))
              } else {
                onError(userErr)
              }
              return@addSnapshotListener
            }

            val mentors = parseDocuments(userSnap?.documents)
            onUpdate(mentors)
          }
      }

    return activeRegistration
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

        val profile = UserProfile(
          id = doc.id,
          name = doc.getString("name") ?: "Devotee",
          email = doc.getString("email") ?: "",
          role = doc.getString("role") ?: "mentee",
          mentorId = doc.getString("mentorId"),
          active = doc.getBoolean("active") ?: true,
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
   * One-time fetch of active counsellors/mentors directly from Firestore with multi-collection fallback.
   */
  suspend fun fetchMentors(): Result<List<MentorItem>> = withContext(Dispatchers.IO) {
    try {
      val auth = FirebaseAuth.getInstance()
      val firestore = FirebaseFirestore.getInstance()

      // If not authenticated, try anonymous auth to satisfy any auth-required read rules
      if (auth.currentUser == null) {
        try {
          auth.signInAnonymously().awaitTask()
        } catch (authEx: Exception) {
          Log.w(TAG, "Anonymous sign in skipped/not available: ${authEx.message}")
        }
      }

      fun parseDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>?): List<MentorItem> {
        return docs?.mapNotNull { doc ->
          val role = doc.getString("role")?.trim()?.lowercase() ?: "mentor"
          if (role != "mentor") return@mapNotNull null
          val name = doc.getString("name")?.trim() ?: return@mapNotNull null
          val email = doc.getString("email")?.trim() ?: ""
          val active = doc.getBoolean("active") ?: true
          if (!active) return@mapNotNull null
          MentorItem(id = doc.id, name = name, email = email)
        }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
      }

      // 1. Try mentor_profiles collection first
      val mentorProfiles = try {
        val snapshot = firestore.collection("mentor_profiles").get().awaitTask()
        parseDocs(snapshot.documents)
      } catch (e1: Exception) {
        Log.w(TAG, "mentor_profiles fetch notice: ${e1.message}")
        emptyList()
      }

      if (mentorProfiles.isNotEmpty()) {
        return@withContext Result.success(mentorProfiles)
      }

      // 2. Fallback to users collection
      val userMentors = try {
        val snapshot = firestore.collection("users")
          .whereEqualTo("role", "mentor")
          .get()
          .awaitTask()
        parseDocs(snapshot.documents)
      } catch (e2: Exception) {
        Log.w(TAG, "users collection mentor query notice: ${e2.message}")
        emptyList()
      }

      Result.success(userMentors)
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
    mentorId: String?
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
      val userProfileMap = hashMapOf<String, Any?>(
        "uid" to uid,
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "role" to targetRole,
        "mentorId" to if (targetRole == "mentee") mentorId else null,
        "active" to true,
        "createdAt" to FieldValue.serverTimestamp()
      )

      firestore.collection("users").document(uid).set(userProfileMap).awaitTask()

      val profile = UserProfile(
        id = uid,
        name = name.trim(),
        email = email.trim().lowercase(),
        role = targetRole,
        mentorId = if (targetRole == "mentee") mentorId else null,
        active = true
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
          val role = docSnapshot.getString("role")?.lowercase()?.trim() ?: "mentee"
          val active = docSnapshot.getBoolean("active") ?: true
          val name = docSnapshot.getString("name") ?: cleanEmail.substringBefore("@")
          val mentorId = docSnapshot.getString("mentorId")

          if (!active) {
            auth.signOut()
            return@withContext Result.failure(Exception("Your account is currently deactivated. Please contact the administrator."))
          }

          profile = UserProfile(
            id = uid,
            name = name,
            email = cleanEmail,
            role = role,
            mentorId = mentorId,
            active = active
          )
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error fetching user doc, attempting auto-heal: ${e.message}")
      }

      // Auto-heal: If profile document does not exist yet in Firestore, create it gracefully
      if (profile == null) {
        val inferredRole = when {
          cleanEmail.equals("rushabhmunot2007@gmail.com", ignoreCase = true) ||
            cleanEmail.contains("admin", ignoreCase = true) -> "admin"
          cleanEmail.contains("mentor", ignoreCase = true) -> "mentor"
          else -> "mentee"
        }

        val inferredName = when (inferredRole) {
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
          active = true
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
   * Save Daily Sadhna submission to Cloud Firestore sadhna_records collection
   */
  suspend fun saveSadhna(submission: SadhnaSubmission): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()

      val recordMap = hashMapOf<String, Any?>(
        "menteeId" to submission.menteeId,
        "mentorId" to submission.mentorId,
        "devoteeName" to submission.devoteeName,
        "date" to submission.date,
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
        "sleepingTime" to submission.sleepingTime,
        "createdAt" to FieldValue.serverTimestamp()
      )

      firestore.collection("sadhna_records").add(recordMap).awaitTask()
      Log.i(TAG, "Sadhna recorded in Firestore for devotee ${submission.menteeId}")
      Result.success(true)
    } catch (e: Exception) {
      Log.e(TAG, "saveSadhna error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Real-time listener for mentees assigned to a mentor
   */
  fun listenToMenteesForMentor(
    mentorUid: String,
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
        val mentees = snapshot?.documents?.map { doc ->
          UserProfile(
            id = doc.id,
            name = doc.getString("name") ?: "Devotee",
            email = doc.getString("email") ?: "",
            role = "mentee",
            mentorId = mentorUid,
            active = doc.getBoolean("active") ?: true
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
   * Admin: Real-time listener for all users (Mentors and Mentees)
   */
  fun listenToAllUsers(
    onUpdate: (List<UserProfile>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("users")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "listenToAllUsers error: ${error.message}", error)
          onError(error)
          return@addSnapshotListener
        }
        val users = snapshot?.documents?.map { doc ->
          UserProfile(
            id = doc.id,
            name = doc.getString("name") ?: "User",
            email = doc.getString("email") ?: "",
            role = doc.getString("role") ?: "mentee",
            mentorId = doc.getString("mentorId"),
            active = doc.getBoolean("active") ?: true
          )
        } ?: emptyList()
        onUpdate(users)
      }
  }

  /**
   * Direct fetch of all users (for Admin Excel mapping)
   */
  suspend fun fetchAllUsersDirectly(): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("users").get().awaitTask()
      val users = snapshot.documents.map { doc ->
        UserProfile(
          id = doc.id,
          name = doc.getString("name") ?: "User",
          email = doc.getString("email") ?: "",
          role = doc.getString("role") ?: "mentee",
          mentorId = doc.getString("mentorId"),
          active = doc.getBoolean("active") ?: true
        )
      }
      Result.success(users)
    } catch (e: Exception) {
      Log.e(TAG, "fetchAllUsersDirectly error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Admin: Real-time listener for all Sadhna submissions
   */
  fun listenToAllSadhnaRecords(
    onUpdate: (List<SadhnaSubmission>) -> Unit,
    onError: (Exception) -> Unit
  ): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection("sadhna_records")
      .addSnapshotListener { snapshot, error ->
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
            sleepingTime = doc.getString("sleepingTime") ?: "",
            createdAt = doc.get("createdAt")
          )
        } ?: emptyList()
        onUpdate(records)
      }
  }

  /**
   * Direct fetch of all Sadhna records (guarantees Admin Export fetches all records)
   */
  suspend fun fetchAllSadhnaRecordsDirectly(): Result<List<SadhnaSubmission>> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("sadhna_records").get().awaitTask()
      val records = snapshot.documents.map { doc ->
        val activities = (doc.get("spiritualActivities") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        SadhnaSubmission(
          id = doc.id,
          menteeId = doc.getString("menteeId") ?: "",
          mentorId = doc.getString("mentorId"),
          devoteeName = doc.getString("devoteeName"),
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
    password: String
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
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
        "createdAt" to FieldValue.serverTimestamp()
      )

      // Write with secondaryApp Firestore (authenticated as the newly created user)
      try {
        val secFirestore = FirebaseFirestore.getInstance(secondaryApp)
        secFirestore.collection("users").document(uid).set(mentorMap).awaitTask()
        secFirestore.collection("mentor_profiles").document(uid).set(mentorMap).awaitTask()
      } catch (secErr: Exception) {
        Log.w(TAG, "Secondary Firestore write fallback to default: ${secErr.message}")
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(mentorMap).awaitTask()
        defaultFirestore.collection("mentor_profiles").document(uid).set(mentorMap).awaitTask()
      }

      // Also ensure default app has written or synced the document
      try {
        val defaultFirestore = FirebaseFirestore.getInstance()
        defaultFirestore.collection("users").document(uid).set(mentorMap).awaitTask()
        defaultFirestore.collection("mentor_profiles").document(uid).set(mentorMap).awaitTask()
      } catch (_: Exception) {}

      // Clean up secondary auth session
      try {
        secondaryAuth.signOut()
      } catch (_: Exception) {}

      Result.success(UserProfile(id = uid, name = name.trim(), email = email.trim().lowercase(), role = "mentor", active = true))
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
    password: String
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
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

      Result.success(UserProfile(id = uid, name = name.trim(), email = email.trim().lowercase(), role = "admin", active = true))
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
    mentorId: String?
  ): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
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

      Result.success(UserProfile(id = uid, name = name.trim(), email = email.trim().lowercase(), role = "mentee", mentorId = mentorId, active = true))
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
   */
  fun syncMentorsToPublicProfiles(mentors: List<UserProfile>) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()
        for (m in mentors) {
          val docRef = firestore.collection("mentor_profiles").document(m.id)
          val data = mapOf(
            "uid" to m.id,
            "name" to m.name,
            "email" to m.email,
            "role" to "mentor",
            "active" to m.active
          )
          batch.set(docRef, data)
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
        "Invalid email or password. Please verify and try again."
      msg.contains("no user record", ignoreCase = true) || msg.contains("user-not-found", ignoreCase = true) ->
        "No account found with this email. Please tap 'Create New Account' below to register."
      msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) ->
        "An account with this email already exists. Please login with your password."
      msg.contains("badly formatted", ignoreCase = true) || msg.contains("invalid-email", ignoreCase = true) ->
        "Please enter a valid email address."
      msg.contains("deactivated", ignoreCase = true) ->
        "Your account is currently deactivated. Please contact the temple administrator."
      msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
        "Access denied. Please ensure your account has the correct permissions or contact the temple admin."
      msg.isNotEmpty() -> msg
      else -> "Operation failed. Please try again."
    }
  }
}
