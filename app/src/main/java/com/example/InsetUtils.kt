package com.example

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Utility to apply safe system window insets (status bar & camera cutout/notch)
 * plus approximately 0.5 cm (~19dp) additional top spacing to the main content container.
 */
object InsetUtils {

  fun applySafeTopSpacing(targetView: View, additionalExtraDp: Int = 19) {
    val density = targetView.resources.displayMetrics.density
    val extraPx = (additionalExtraDp * density).toInt()

    val initialPaddingTop = targetView.paddingTop
    val initialPaddingBottom = targetView.paddingBottom
    val initialPaddingLeft = targetView.paddingLeft
    val initialPaddingRight = targetView.paddingRight

    ViewCompat.setOnApplyWindowInsetsListener(targetView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.setPadding(
        initialPaddingLeft,
        insets.top + initialPaddingTop + extraPx,
        initialPaddingRight,
        initialPaddingBottom
      )
      windowInsets
    }

    // Trigger insets dispatch in case the view is already attached
    if (targetView.isAttachedToWindow) {
      ViewCompat.requestApplyInsets(targetView)
    } else {
      targetView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
          v.removeOnAttachStateChangeListener(this)
          ViewCompat.requestApplyInsets(v)
        }
        override fun onViewDetachedFromWindow(v: View) {}
      })
    }
  }
}
