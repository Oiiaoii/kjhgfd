package com.solmacro

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

// padlock that drops in, snaps shut, shakes, and sends out a red ring again and again
class LockView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drop = 0f      // 0 = open and high, 1 = closed
    private var ring = 0f
    private var shake = 0f
    private val anims = ArrayList<Animator>()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!Fx.on(context)) { drop = 1f; ring = 0f; return }
        anims.add(ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650; interpolator = BounceInterpolator(); startDelay = 150
            addUpdateListener { drop = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    val s = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 480
                        addUpdateListener { shake = Math.sin(it.animatedValue as Float * 6.0 * Math.PI).toFloat() * (1f - it.animatedValue as Float); invalidate() }
                    }
                    anims.add(s); s.start()
                }
            })
            start()
        })
        anims.add(ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { ring = it.animatedValue as Float; invalidate() }
            start()
        })
    }

    override fun onDetachedFromWindow() {
        for (a in anims) a.cancel()
        anims.clear()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val u = minOf(w, h) / 100f
        // red ring that spreads and fades
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * u
        p.color = ((200 * (1f - ring)).toInt() shl 24) or 0xFF3B4E
        c.drawCircle(cx, cy, (34f + 30f * ring) * u, p)
        c.save()
        c.translate(shake * 3f * u, 0f)
        // glow disc
        p.style = Paint.Style.FILL
        p.color = 0x33FF3B4E
        c.drawCircle(cx, cy, 36f * u, p)
        // shackle (drops down into the body)
        val lift = (1f - drop) * 16f * u
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f * u
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFE6ECFF.toInt()
        val sh = Path()
        sh.moveTo(cx - 13f * u, cy - 2f * u - lift)
        sh.lineTo(cx - 13f * u, cy - 14f * u - lift)
        sh.arcTo(RectF(cx - 13f * u, cy - 27f * u - lift, cx + 13f * u, cy - 1f * u - lift), 180f, 180f)
        sh.lineTo(cx + 13f * u, cy - 2f * u - lift)
        c.drawPath(sh, p)
        // body
        p.style = Paint.Style.FILL
        p.color = 0xFFFF3B4E.toInt()
        c.drawRoundRect(RectF(cx - 21f * u, cy - 4f * u, cx + 21f * u, cy + 26f * u), 7f * u, 7f * u, p)
        // keyhole
        p.color = 0xFF2A0610.toInt()
        c.drawCircle(cx, cy + 9f * u, 4f * u, p)
        c.drawRoundRect(RectF(cx - 1.8f * u, cy + 10f * u, cx + 1.8f * u, cy + 18f * u), 1.5f * u, 1.5f * u, p)
        c.restore()
    }
}

// shown to an account that is on the owner's blacklist
class BannedActivity : Activity() {
    private val cBg = 0xFF0A0307.toInt()
    private val cTx = 0xFFFFE6EA.toInt()
    private val cMut = 0xFFD497A0.toInt()
    private val cRed = 0xFFFF3B4E.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_DIP, size); setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }
    private fun shape(color: Int, r: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(stroke), sc)
    }

    private lateinit var again: TextView
    private lateinit var busy: ProgressBar

    override fun attachBaseContext(base: Context) {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        if (cfg.fontScale > 1.0f) cfg.fontScale = 1.0f
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        val cur = Blacklist.blocked(this)
        val name = cur?.name?.ifEmpty { null } ?: Cfg.gets(this, "ban_name").ifEmpty { Auth.name(this) }
        val reason = cur?.reason?.ifEmpty { null } ?: Cfg.gets(this, "ban_reason")
        val prov = if (cur != null) cur.p else Cfg.gets(this, "ban_p")

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(26), dp(70), dp(26), dp(30)) }
        val lock = LockView(this)
        root.addView(lock, LinearLayout.LayoutParams(dp(150), dp(150)))
        val title = tv("Access blocked", 28f, cTx, true)
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val sub = tv(
            (if (name.isNotEmpty()) name + (if (prov.isNotEmpty()) "  ·  " + Auth.label(prov) else "") + "\n" else "") + "This account is not allowed to use LuminFish.",
            14f, cMut)
        root.addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        var reasonBox: View? = null
        if (reason.isNotEmpty()) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(0x33FF3B4E, 14, 1, 0x66FF3B4E); setPadding(dp(16), dp(12), dp(16), dp(12)) }
            box.addView(tv("Reason", 11.5f, cMut, true))
            box.addView(tv(reason, 14f, cTx).apply { setPadding(0, dp(4), 0, 0) })
            root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) })
            reasonBox = box
        }
        again = tv("Check again", 15f, Color.WHITE, true).apply {
            background = shape(0xFF3A1018.toInt(), 14, 1, 0x88FF3B4E.toInt()); setPadding(0, dp(14), 0, dp(14)); isClickable = true
            setOnClickListener { checkAgain() }
        }
        val out = tv("Sign out", 14f, 0xFFFF8A96.toInt(), true).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14)); isClickable = true
            setOnClickListener { Auth.signOut(this@BannedActivity); startActivity(Intent(this@BannedActivity, LoginActivity::class.java)); finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
        }
        for (v in listOf(again, out)) v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            false
        }
        busy = ProgressBar(this).apply { isIndeterminate = true; visibility = View.GONE }
        root.addView(again, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(26) })
        root.addView(busy, LinearLayout.LayoutParams(dp(24), dp(24)).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(out, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        val frame = FrameLayout(this)
        frame.addView(AuroraView(this, true), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(root) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)

        Fx.rise(title, 650, 18, 420)
        Fx.rise(sub, 760, 14, 420)
        reasonBox?.let { Fx.rise(it, 880, 16, 420) }
        Fx.rise(again, 1000, 14, 380)
        Fx.rise(out, 1080, 10, 320)
    }

    private fun checkAgain() {
        busy.visibility = View.VISIBLE
        again.alpha = 0.5f
        Blacklist.refresh(this) {
            busy.visibility = View.GONE
            again.alpha = 1f
            val still = Blacklist.blocked(this) != null || (!Auth.signedIn(this) && Cfg.gets(this, "ban_p").isNotEmpty() && Blacklist.check(this, Cfg.gets(this, "ban_p"), Cfg.gets(this, "ban_id")) != null)
            if (still) { Fx.shake(again); again.text = if (it) "Still blocked" else "Could not reach the list, still blocked"; Fx.shake(findViewById<View>(android.R.id.content)) }
            else {
                // not on the list any more: back to the sign-in (not signed in) or to the app
                startActivity(Intent(this, if (Auth.signedIn(this)) MainActivity::class.java else LoginActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    override fun onBackPressed() { moveTaskToBack(true) }
}
