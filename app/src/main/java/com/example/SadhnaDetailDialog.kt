package com.example

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object SadhnaDetailDialog {

  fun show(context: Context, submission: SadhnaSubmission) {
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val scrollView = android.widget.ScrollView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      isFillViewport = true
      setBackgroundColor(Color.parseColor("#FAF8F2"))
    }

    val root = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(32, 28, 32, 32)
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    // --- TITLE HEADER ---
    val headerLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER_HORIZONTAL
      setPadding(0, 0, 0, 20)
    }

    val titleTv = TextView(context).apply {
      text = "Daily Sadhna Record"
      textSize = 20f
      setTextColor(Color.parseColor("#111111"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
    }

    val devoteeTv = TextView(context).apply {
      text = "Devotee: ${submission.devoteeName ?: "Devotee"} • ${submission.date}"
      textSize = 14f
      setTextColor(Color.parseColor("#E65100"))
      setTypeface(null, android.graphics.Typeface.BOLD)
      gravity = android.view.Gravity.CENTER
      setPadding(0, 4, 0, 0)
    }

    headerLayout.addView(titleTv)
    headerLayout.addView(devoteeTv)
    root.addView(headerLayout)

    // Helper to create Section Card
    fun createCard(title: String): Pair<LinearLayout, LinearLayout> {
      val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        elevation = 3f
        setPadding(24, 20, 24, 20)
        val params = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
          setMargins(0, 0, 0, 16)
        }
        layoutParams = params
      }

      val cardTitle = TextView(context).apply {
        text = title
        textSize = 15f
        setTextColor(Color.parseColor("#111111"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, 12)
      }
      card.addView(cardTitle)

      val contentLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
      }
      card.addView(contentLayout)
      return Pair(card, contentLayout)
    }

    fun addFieldRow(parent: LinearLayout, label: String, value: String, isHighlight: Boolean = false, isNegative: Boolean = false) {
      val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 4, 0, 4)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      }

      val labelTv = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#333333"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
      }

      val valueTv = TextView(context).apply {
        text = value
        textSize = 13f
        setTextColor(
          when {
            isNegative -> Color.parseColor("#C62828")
            isHighlight -> Color.parseColor("#1B5E20")
            else -> Color.parseColor("#111111")
          }
        )
        setTypeface(null, if (isHighlight || isNegative) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
      }

      row.addView(labelTv)
      row.addView(valueTv)
      parent.addView(row)
    }

    // --- CARD 1: TIMING & SCHEDULE ---
    val (card1, content1) = createCard("⏰ Morning Wake Up")
    val wakeUp = submission.wakeUpTime.ifEmpty { "Not provided" }
    addFieldRow(content1, "Wake Up Time:", wakeUp, isHighlight = wakeUp != "Not provided", isNegative = wakeUp == "Not provided")
    root.addView(card1)

    // --- CARD 2: JAPA & CHANTING ---
    val (card2, content2) = createCard("📿 Japa & Holy Name")
    val japaRoundsStr = "${submission.japaRounds} Rounds"
    val japaTime = if (submission.japaFinishTime.isNotEmpty()) {
      submission.japaFinishTime
    } else if (submission.japaCompletedHours.isNotEmpty() || submission.japaCompletedMinutes.isNotEmpty()) {
      "${submission.japaCompletedHours.ifEmpty { "0" }} Hrs ${submission.japaCompletedMinutes.ifEmpty { "0" }} Mins"
    } else {
      "Not specified"
    }
    addFieldRow(content2, "Chanted Rounds:", japaRoundsStr, isHighlight = submission.japaRounds > 0, isNegative = submission.japaRounds == 0)
    addFieldRow(content2, "Finish Time of Japa:", japaTime)
    root.addView(card2)

    // --- CARD 3: TEMPLE & MORNING PROGRAMS ---
    val (card3, content3) = createCard("🪷 Spiritual Programs & Aarti")
    val allStandardPrograms = listOf(
      "Mangal Aarti",
      "Narasimha Aarti",
      "Tulsi Aarti",
      "Shringar Aarti",
      "Guru Puja",
      "Srimad Bhagvatam",
      "Satsang"
    )

    for (program in allStandardPrograms) {
      val isAttended = submission.spiritualActivities.any { it.contains(program, ignoreCase = true) }
      val statusText = if (isAttended) "Attended" else "Not attended"
      addFieldRow(content3, "• $program:", statusText, isHighlight = isAttended, isNegative = !isAttended)
    }

    // Extra / Custom programs
    val otherActivities = submission.spiritualActivities.filter { act ->
      allStandardPrograms.none { act.contains(it, ignoreCase = true) }
    }
    if (otherActivities.isNotEmpty()) {
      addFieldRow(content3, "• Other Activities:", otherActivities.joinToString(", "), isHighlight = true)
    }
    root.addView(card3)

    // --- CARD 4: SHRAVANAM & SWADHYAYA ---
    val (card4, content4) = createCard("📖 Hearing & Book Reading")
    val bookSub = submission.bookReadingSubject.ifEmpty { "Not completed" }
    val bookTime = if (submission.bookReadingSubject.isNotEmpty()) submission.bookReadingTime.ifEmpty { "Not provided" } else "0 Hr"
    val hearSub = submission.hearingSubject.ifEmpty { "Not completed" }
    val hearTime = if (submission.hearingSubject.isNotEmpty()) submission.hearingTime.ifEmpty { "Not provided" } else "0 Hr"

    addFieldRow(content4, "Book Reading:", bookSub, isHighlight = bookSub != "Not completed", isNegative = bookSub == "Not completed")
    addFieldRow(content4, "Reading Duration:", bookTime)
    addFieldRow(content4, "Lecture / Hearing:", hearSub, isHighlight = hearSub != "Not completed", isNegative = hearSub == "Not completed")
    addFieldRow(content4, "Hearing Duration:", hearTime)
    root.addView(card4)

    // --- CARD 5: PRACTICAL DEVOTIONAL SEVA ---
    val (card5, content5) = createCard("🌺 Devotional Seva (Service)")
    val sevaStatus = if (submission.sevaCompleted) "Completed" else "Not performed"
    val sevaDuration = if (submission.sevaCompleted) {
      "${submission.sevaHours.ifEmpty { "0" }} Hrs ${submission.sevaMinutes.ifEmpty { "0" }} Mins"
    } else {
      "0 Hrs 0 Mins"
    }
    addFieldRow(content5, "Seva Status:", sevaStatus, isHighlight = submission.sevaCompleted, isNegative = !submission.sevaCompleted)
    addFieldRow(content5, "Seva Time:", sevaDuration)
    root.addView(card5)

    // --- CARD 6: NIGHT REST & SLEEPING TIME ---
    val (card6, content6) = createCard("🌙 Night Rest & Sleeping Time")
    val sleep = submission.sleepingTime.ifEmpty { "Not provided" }
    addFieldRow(content6, "Sleeping Time:", sleep, isHighlight = sleep != "Not provided", isNegative = sleep == "Not provided")
    root.addView(card6)

    // --- CLOSE BUTTON ---
    val btnClose = Button(context).apply {
      text = "CLOSE"
      textSize = 15f
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.parseColor("#E65100"))
      setPadding(0, 24, 0, 24)
      val params = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 12
      }
      layoutParams = params
      setOnClickListener {
        dialog.dismiss()
      }
    }
    root.addView(btnClose)

    scrollView.addView(root)
    dialog.setContentView(scrollView)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
  }
}
