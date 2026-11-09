package com.example

import org.json.JSONObject
import java.util.Locale

data class SadhnaCommitment(
  val id: String,
  var title: String,
  var subtitle: String,
  var hour: Int, // 0-23
  var minute: Int, // 0-59
  var isEnabled: Boolean = true,
  val isCustom: Boolean = false,
  val isSubmissionCheck: Boolean = false,
  var emoji: String = "📿"
) {
  fun getFormattedTime(): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
  }

  fun toJson(): JSONObject {
    return JSONObject().apply {
      put("id", id)
      put("title", title)
      put("subtitle", subtitle)
      put("hour", hour)
      put("minute", minute)
      put("isEnabled", isEnabled)
      put("isCustom", isCustom)
      put("isSubmissionCheck", isSubmissionCheck)
      put("emoji", emoji)
    }
  }

  companion object {
    fun fromJson(json: JSONObject): SadhnaCommitment {
      return SadhnaCommitment(
        id = json.optString("id", "custom_${System.currentTimeMillis()}"),
        title = json.optString("title", "Daily Sadhna Commitment"),
        subtitle = json.optString("subtitle", "Spiritual practice reminder"),
        hour = json.optInt("hour", 6),
        minute = json.optInt("minute", 0),
        isEnabled = json.optBoolean("isEnabled", true),
        isCustom = json.optBoolean("isCustom", false),
        isSubmissionCheck = json.optBoolean("isSubmissionCheck", false),
        emoji = json.optString("emoji", "📿")
      )
    }

    fun getDefaultCommitments(): List<SadhnaCommitment> {
      return listOf(
        SadhnaCommitment(
          id = "commitment_mangal_aarti",
          title = "Mangala Aarti",
          subtitle = "Awaken early for auspicious Mangala Aarti & morning worship",
          hour = 4,
          minute = 30,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "🌅"
        ),
        SadhnaCommitment(
          id = "commitment_tulasi_aarti",
          title = "Tulasi Aarti",
          subtitle = "Offer worship and circumambulate sacred Tulasi Maharani",
          hour = 5,
          minute = 0,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "🌿"
        ),
        SadhnaCommitment(
          id = "commitment_japa",
          title = "Japa Meditation",
          subtitle = "Chant the Hare Krishna Maha-Mantra with attention and devotion",
          hour = 5,
          minute = 30,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "📿"
        ),
        SadhnaCommitment(
          id = "commitment_guru_puja",
          title = "Guru Puja",
          subtitle = "Offer flowers, prayers and worship to Srila Prabhupada",
          hour = 7,
          minute = 30,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "🌺"
        ),
        SadhnaCommitment(
          id = "commitment_reading",
          title = "Srimad Bhagavatam Reading",
          subtitle = "Read and study Srila Prabhupada's transcendental literature",
          hour = 8,
          minute = 0,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "📖"
        ),
        SadhnaCommitment(
          id = "commitment_hearing",
          title = "Afternoon Hearing & Kirtan",
          subtitle = "Listen to spiritual lectures, Hari-katha and ecstatic kirtan",
          hour = 14,
          minute = 0,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "🎧"
        ),
        SadhnaCommitment(
          id = "commitment_sandhya_aarti",
          title = "Sandhya Aarti",
          subtitle = "Offer ghee lamps and sweet prayers to Sri Sri Radha Krishna",
          hour = 19,
          minute = 0,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = false,
          emoji = "🪔"
        ),
        SadhnaCommitment(
          id = "commitment_submission",
          title = "Daily Sadhna Submission",
          subtitle = "Review and submit your daily devotional practices report",
          hour = 21,
          minute = 0,
          isEnabled = true,
          isCustom = false,
          isSubmissionCheck = true,
          emoji = "🙏"
        )
      )
    }
  }
}
