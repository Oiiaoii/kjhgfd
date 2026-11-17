package com.solmacro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

// v2.3 small animation toolkit used by the sign-in screens and the overlays.
// Everything goes straight to its end state when "Animations" is off in the settings or when Android's own animation
// scale is 0 (system "remove animations" / battery saver), so nothing is ever left half way.
object Fx {
    fun on(c: Context): Boolean {
        if (Cfg.get(c, "anim") != 1) return false
        return try { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f } catch (_: Exception) { true }
    }

    private fun dp(v: View, x: Int) = x * v.resources.displayMetrics.density

    // slides up a little while fading in
    fun rise(v: View, delay: Long = 0, dy: Int = 26, dur: Long = 460) {
        if (!on(v.context)) { v.alpha = 1f; v.translationY = 0f; return }
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = dp(v, dy)
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(dur).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    // grows from small with a little overshoot
    fun pop(v: View, delay: Long = 0, from: Float = 0.55f, dur: Long = 520) {
        if (!on(v.context)) { v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f; return }
        v.animate().cancel()
        v.alpha = 0f
        v.scaleX = from
        v.scaleY = from
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(dur).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    // short side to side shake (wrong input, failed sign-in)
    fun shake(v: View) {
        if (!on(v.context)) return
        val d = dp(v, 9)
        ObjectAnimator.ofFloat(v, "translationX", 0f, -d, d, -d * 0.7f, d * 0.7f, -d * 0.35f, d * 0.35f, 0f).apply { duration = 440; start() }
    }

    // quick scale tick, for a value that changed
    fun bump(v: View) {
        if (!on(v.context)) return
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }

    // endless soft breathing; returns the animator so the caller can cancel it
    fun breathe(v: View, to: Float = 1.05f, dur: Long = 1700): Animator? {
        if (!on(v.context)) return null
        return ValueAnimator.ofFloat(1f, to).apply {
            duration = dur
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { v.scaleX = it.animatedValue as Float; v.scaleY = it.animatedValue as Float }
            start()
        }
    }

    // endless slow up and down float
    fun float(v: View, dy: Int = 4, dur: Long = 2600): Animator? {
        if (!on(v.context)) return null
        return ObjectAnimator.ofFloat(v, "translationY", -dp(v, dy), dp(v, dy)).apply {
            duration = dur
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ring that grows and fades again and again (glow behind a logo)
    fun ripple(v: View, from: Float = 1f, to: Float = 1.45f, dur: Long = 2200): Animator? {
        if (!on(v.context)) { v.alpha = 0f; return null }
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val s = from + (to - from) * t
                v.scaleX = s
                v.scaleY = s
                v.alpha = 0.55f * (1f - t)
            }
            start()
        }
    }

    // text change: old text fades out, new text fades in
    fun swapText(t: android.widget.TextView, text: String) {
        if (t.text.toString() == text) return
        if (!on(t.context)) { t.text = text; return }
        t.animate().cancel()
        t.animate().alpha(0f).translationY(-dp(t, 6)).setDuration(110).withEndAction {
            t.text = text
            t.translationY = dp(t, 6)
            t.animate().alpha(1f).translationY(0f).setDuration(190).setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    fun out(v: View, dur: Long = 260, end: () -> Unit) {
        if (!on(v.context)) { end(); return }
        v.animate().cancel()
        v.animate().alpha(0f).translationY(-dp(v, 18)).scaleX(0.97f).scaleY(0.97f).setDuration(dur).setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { v.animate().setListener(null); end() }
            }).start()
    }
}
