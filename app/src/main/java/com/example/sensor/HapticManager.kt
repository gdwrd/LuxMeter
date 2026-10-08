package com.example.sensor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

class HapticManager(private val context: Context) {

  var isHapticsEnabled: Boolean = true

  private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    vibratorManager?.defaultVibrator
  } else {
    @Suppress("DEPRECATION")
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
  }

  /**
   * Tactile click when rotating an exposure dial detent
   */
  fun performDialClick(view: View? = null) {
    if (!isHapticsEnabled) return

    if (view != null) {
      val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_TICK
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
      val success = view.performHapticFeedback(feedbackConstant)
      if (success) return
    }

    // Vibrator fallback for crisp tactile pulse
    try {
      if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(12)
        }
      }
    } catch (_: Exception) {
      // Ignored
    }
  }

  /**
   * Satisfying heavy mechanical shutter release haptic thump
   */
  fun performShutterRelease(view: View? = null) {
    if (!isHapticsEnabled) return

    if (view != null) {
      val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
      } else {
        HapticFeedbackConstants.LONG_PRESS
      }
      view.performHapticFeedback(feedbackConstant)
    }

    try {
      if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(35)
        }
      }
    } catch (_: Exception) {
      // Ignored
    }
  }
}
