package com.example

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SplashActivity : ComponentActivity() {

  private lateinit var flLogoContainer: FrameLayout
  private lateinit var tvSplashTitle: TextView
  private lateinit var ivSplashFlourish: ImageView
  private lateinit var tvSplashSubtitle: TextView
  private lateinit var layoutSplashFooter: LinearLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      splashScreen.setOnExitAnimationListener { splashScreenView ->
        splashScreenView.remove()
      }
    }
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_splash)

    flLogoContainer = findViewById(R.id.flLogoContainer)
    tvSplashTitle = findViewById(R.id.tvSplashTitle)
    ivSplashFlourish = findViewById(R.id.ivSplashFlourish)
    tvSplashSubtitle = findViewById(R.id.tvSplashSubtitle)
    layoutSplashFooter = findViewById(R.id.layoutSplashFooter)

    // Initial state before animation
    flLogoContainer.alpha = 0f
    flLogoContainer.scaleX = 0.85f
    flLogoContainer.scaleY = 0.85f

    tvSplashTitle.alpha = 0f
    tvSplashTitle.translationY = 20f

    ivSplashFlourish.alpha = 0f

    tvSplashSubtitle.alpha = 0f
    tvSplashSubtitle.translationY = 15f

    layoutSplashFooter.alpha = 0f

    startSubtleEntranceAnimation()

    // 2.7 seconds splash delay with lifecycle-aware coroutine
    lifecycleScope.launch {
      delay(2700L)
      if (!isFinishing && !isDestroyed) {
        routeToNextScreen()
      }
    }
  }

  private fun startSubtleEntranceAnimation() {
    // 1. Logo container fade-in and scale up
    val logoFade = ObjectAnimator.ofFloat(flLogoContainer, View.ALPHA, 0f, 1f).apply {
      duration = 900
    }
    val logoScaleX = ObjectAnimator.ofFloat(flLogoContainer, View.SCALE_X, 0.85f, 1f).apply {
      duration = 900
    }
    val logoScaleY = ObjectAnimator.ofFloat(flLogoContainer, View.SCALE_Y, 0.85f, 1f).apply {
      duration = 900
    }

    // 2. Title fade-in and slide up
    val titleFade = ObjectAnimator.ofFloat(tvSplashTitle, View.ALPHA, 0f, 1f).apply {
      startDelay = 300
      duration = 700
    }
    val titleTransY = ObjectAnimator.ofFloat(tvSplashTitle, View.TRANSLATION_Y, 20f, 0f).apply {
      startDelay = 300
      duration = 700
    }

    // 3. Flourish and Subtitle
    val flourishFade = ObjectAnimator.ofFloat(ivSplashFlourish, View.ALPHA, 0f, 1f).apply {
      startDelay = 500
      duration = 600
    }
    val subtitleFade = ObjectAnimator.ofFloat(tvSplashSubtitle, View.ALPHA, 0f, 1f).apply {
      startDelay = 650
      duration = 600
    }
    val subtitleTransY = ObjectAnimator.ofFloat(tvSplashSubtitle, View.TRANSLATION_Y, 15f, 0f).apply {
      startDelay = 650
      duration = 600
    }

    // 4. Footer
    val footerFade = ObjectAnimator.ofFloat(layoutSplashFooter, View.ALPHA, 0f, 1f).apply {
      startDelay = 800
      duration = 700
    }

    AnimatorSet().apply {
      interpolator = DecelerateInterpolator(1.5f)
      playTogether(
        logoFade, logoScaleX, logoScaleY,
        titleFade, titleTransY,
        flourishFade,
        subtitleFade, subtitleTransY,
        footerFade
      )
      start()
    }
  }

  private fun routeToNextScreen() {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val savedProfile = SessionManager.getSavedProfile(this)

    if (currentUser != null && savedProfile != null && savedProfile.active) {
      when (savedProfile.role.lowercase(Locale.getDefault()).trim()) {
        "admin" -> navigateTo(AdminDashboardActivity::class.java)
        "mentor" -> navigateTo(MentorDashboardActivity::class.java)
        "mentee" -> navigateTo(MenteeDashboardActivity::class.java)
        else -> navigateTo(LoginActivity::class.java)
      }
    } else {
      navigateTo(LoginActivity::class.java)
    }
  }

  private fun <T> navigateTo(targetActivity: Class<T>) {
    val intent = Intent(this, targetActivity).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    finish()
  }
}
