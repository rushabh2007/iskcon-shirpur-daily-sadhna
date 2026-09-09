package com.example

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MentorSubDashboardActivity : ComponentActivity() {

  companion object {
    const val EXTRA_MENTOR_ID = "extra_mentor_id"
    const val EXTRA_MENTOR_NAME = "extra_mentor_name"
    const val EXTRA_MENTOR_EMAIL = "extra_mentor_email"
    const val EXTRA_MENTOR_ACTIVE = "extra_mentor_active"
  }

  private lateinit var btnBack: Button
  private lateinit var tvMentorSubName: TextView
  private lateinit var tvMentorSubEmail: TextView
  private lateinit var tvMentorSubStatus: TextView
  private lateinit var tvMentorTotalMentees: TextView
  private lateinit var tvMentorTotalSubmissions: TextView
  private lateinit var tvMentorMenteesHeader: TextView
  private lateinit var pbMentorSubLoading: ProgressBar
  private lateinit var layoutMentorMenteesList: LinearLayout
  private lateinit var tvMentorSadhnaHeader: TextView
  private lateinit var layoutMentorSadhnaList: LinearLayout

  private var menteesListener: ListenerRegistration? = null
  private var sadhnaListener: ListenerRegistration? = null
  private var mentorId: String = ""
  private var mentorName: String = "HG Counsellor"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mentor_sub_dashboard)

    btnBack = findViewById(R.id.btnBack)
    tvMentorSubName = findViewById(R.id.tvMentorSubName)
    tvMentorSubEmail = findViewById(R.id.tvMentorSubEmail)
    tvMentorTotalMentees = findViewById(R.id.tvMentorTotalMentees)
    tvMentorTotalSubmissions = findViewById(R.id.tvMentorTotalSubmissions)
    tvMentorMenteesHeader = findViewById(R.id.tvMentorMenteesHeader)
    pbMentorSubLoading = findViewById(R.id.pbMentorSubLoading)
    layoutMentorMenteesList = findViewById(R.id.layoutMentorMenteesList)
    tvMentorSadhnaHeader = findViewById(R.id.tvMentorSadhnaHeader)
    layoutMentorSadhnaList = findViewById(R.id.layoutMentorSadhnaList)

    mentorId = intent.getStringExtra(EXTRA_MENTOR_ID) ?: ""
    mentorName = intent.getStringExtra(EXTRA_MENTOR_NAME) ?: "Counsellor"
    val mentorEmail = intent.getStringExtra(EXTRA_MENTOR_EMAIL) ?: ""

    tvMentorSubName.text = "🎓 HG $mentorName"
    tvMentorSubEmail.text = "✉️ ${mentorEmail.ifEmpty { "counsellor@iskcon.org" }}"

    btnBack.setOnClickListener {
      finish()
    }

    findViewById<View>(R.id.fabAddSadhna)?.setOnClickListener {
      val intent = Intent(this, DailySadhnaActivity::class.java)
      startActivity(intent)
    }

    findViewById<View>(R.id.btnMentorSubAddMentee)?.setOnClickListener {
      showAddMenteeDialog()
    }

    if (mentorId.isNotEmpty()) {
      startRealtimeListeners(mentorId)
    } else {
      Toast.makeText(this, "Counsellor ID not provided.", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showAddMenteeDialog() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(50, 30, 50, 10)
    }

    val etName = EditText(this).apply {
      hint = "Counsellee Full Name"
    }
    val etEmail = EditText(this).apply {
      hint = "Counsellee Email Address"
      inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }
    val etPassword = EditText(this).apply {
      hint = "Initial Password (min 6 chars)"
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
      setText("iskcon108")
    }

    val counsellorInfoTv = TextView(this).apply {
      text = "Assigned to: HG $mentorName"
      setPadding(0, 12, 0, 4)
      setTextColor(Color.parseColor("#E65100"))
      textSize = 13f
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    layout.addView(etName)
    layout.addView(etEmail)
    layout.addView(etPassword)
    layout.addView(counsellorInfoTv)

    AlertDialog.Builder(this)
      .setTitle("Add New Counsellee")
      .setView(layout)
      .setPositiveButton("Create Counsellee") { _, _ ->
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.length < 6) {
          Toast.makeText(this, "Please enter valid name, email, and at least 6 characters password.", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }

        pbMentorSubLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
          val result = SessionManager.addMentee(this@MentorSubDashboardActivity, name, email, password, mentorId)
          pbMentorSubLoading.visibility = View.GONE
          if (result.isSuccess) {
            Toast.makeText(this@MentorSubDashboardActivity, "Counsellee $name added successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(this@MentorSubDashboardActivity, "Failed to add counsellee: ${SessionManager.formatErrorMessage(result.exceptionOrNull())}", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  override fun onDestroy() {
    super.onDestroy()
    menteesListener?.remove()
    menteesListener = null
    sadhnaListener?.remove()
    sadhnaListener = null
  }

  private fun startRealtimeListeners(mUid: String) {
    pbMentorSubLoading.visibility = View.VISIBLE

    // 1. Real-time listener for mentees assigned to this mentor
    menteesListener?.remove()
    menteesListener = SessionManager.listenToMenteesForMentor(
      mentorUid = mUid,
      onUpdate = { mentees ->
        pbMentorSubLoading.visibility = View.GONE
        layoutMentorMenteesList.removeAllViews()

        val count = mentees.size
        tvMentorMenteesHeader.text = "Assigned Counsellees ($count)"
        tvMentorTotalMentees.text = "$count"

        if (mentees.isEmpty()) {
          val emptyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 2f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
          }
          val emptyTv = TextView(this).apply {
            text = "No counsellees currently assigned to this counsellor."
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
          }
          emptyCard.addView(emptyTv)
          layoutMentorMenteesList.addView(emptyCard)
        } else {
          for (mentee in mentees) {
            val card = createMenteeItemCard(mentee)
            layoutMentorMenteesList.addView(card)
          }
        }
      },
      onError = { e ->
        pbMentorSubLoading.visibility = View.GONE
        Toast.makeText(this, "Error loading counsellees: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )

    // 2. Real-time listener for sadhna submissions by this mentor's mentees
    sadhnaListener?.remove()
    sadhnaListener = SessionManager.listenToSadhnaRecordsForMentor(
      mentorUid = mUid,
      onUpdate = { records ->
        layoutMentorSadhnaList.removeAllViews()

        val sortedRecords = records.sortedByDescending { it.date }
        val count = sortedRecords.size
        tvMentorSadhnaHeader.text = "Counsellees Sadhna Submissions ($count)"
        tvMentorTotalSubmissions.text = "$count"

        if (sortedRecords.isEmpty()) {
          val emptyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 2f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
          }
          val emptyTv = TextView(this).apply {
            text = "No sadhna submissions recorded yet by this counsellor's counsellees."
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
          }
          emptyCard.addView(emptyTv)
          layoutMentorSadhnaList.addView(emptyCard)
        } else {
          for (record in sortedRecords.take(40)) {
            val card = createSadhnaRecordCard(record)
            layoutMentorSadhnaList.addView(card)
          }
        }
      },
      onError = { e ->
        Toast.makeText(this, "Error loading counsellor sadhna records: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  private fun createMenteeItemCard(mentee: UserProfile): View {
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(16, 14, 16, 14)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 10)
      }
      layoutParams = params
      isClickable = true
      isFocusable = true
      setOnClickListener {
        openMenteeSubDashboard(mentee)
      }
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val nameTv = TextView(this).apply {
      text = "👤 ${mentee.name}"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val viewDataBtn = Button(this).apply {
      text = "View Sadhna ➔"
      textSize = 11f
      minHeight = 36
      setBackgroundColor(Color.parseColor("#E65100"))
      setTextColor(Color.WHITE)
      setOnClickListener {
        openMenteeSubDashboard(mentee)
      }
    }

    topRow.addView(nameTv)
    topRow.addView(viewDataBtn)

    val emailTv = TextView(this).apply {
      text = "✉️ ${mentee.email}"
      textSize = 13f
      setTextColor(Color.parseColor("#616161"))
      setPadding(0, 2, 0, 0)
    }

    container.addView(topRow)
    container.addView(emailTv)
    return container
  }

  private fun openMenteeSubDashboard(mentee: UserProfile) {
    val intent = Intent(this, MenteeSubDashboardActivity::class.java).apply {
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_ID, mentee.id)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_NAME, mentee.name)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTEE_EMAIL, mentee.email)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTOR_NAME, mentorName)
      putExtra(MenteeSubDashboardActivity.EXTRA_MENTOR_ID, mentorId)
    }
    startActivity(intent)
  }

  private fun createSadhnaRecordCard(record: SadhnaSubmission): View {
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
      elevation = 3f
      setPadding(16, 14, 16, 14)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        setMargins(0, 0, 0, 10)
      }
      layoutParams = params
      isClickable = true
      isFocusable = true
      setOnClickListener {
        SadhnaDetailDialog.show(this@MentorSubDashboardActivity, record)
      }
    }

    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val devoteeTv = TextView(this).apply {
      text = "👤 ${record.devoteeName ?: "Devotee"}"
      textSize = 15f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val japaTv = TextView(this).apply {
      text = "📿 ${record.japaRounds} Rnds"
      textSize = 13f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
    }

    topRow.addView(devoteeTv)
    topRow.addView(japaTv)

    val dateAndTimingTv = TextView(this).apply {
      text = "📅 Date: ${record.date}  |  ⏰ Wake Up: ${record.wakeUpTime.ifEmpty { "--:--" }}"
      textSize = 13f
      setTextColor(Color.parseColor("#424242"))
      setPadding(0, 4, 0, 0)
    }

    val programsTv = TextView(this).apply {
      text = "🪷 ${record.spiritualActivities.size} Programs  •  Tap to inspect complete entry ➔"
      textSize = 12f
      setTextColor(Color.parseColor("#00695C"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      setPadding(0, 4, 0, 0)
    }

    container.addView(topRow)
    container.addView(dateAndTimingTv)
    container.addView(programsTv)
    return container
  }
}
