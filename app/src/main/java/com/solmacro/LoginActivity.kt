package com.solmacro

import android.animation.Animator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val FILL = ViewGroup.LayoutParams.MATCH_PARENT
private const val HUG = ViewGroup.LayoutParams.WRAP_CONTENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// slow drifting colour glow behind the sign-in screen
class AuroraView(ctx: Context, private val warm: Boolean = false) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var anim: ValueAnimator? = null
    private var g1: RadialGradient? = null
    private var g2: RadialGradient? = null
    private var g3: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = maxOf(w, h) * 0.55f
        if (warm) {
            g1 = RadialGradient(0f, 0f, r, 0x55FF3B4E, 0x00FF3B4E, Shader.TileMode.CLAMP)
            g2 = RadialGradient(0f, 0f, r * 0.9f, 0x44B3122A, 0x00B3122A, Shader.TileMode.CLAMP)
            g3 = RadialGradient(0f, 0f, r * 0.8f, 0x33FF7A3B, 0x00FF7A3B, Shader.TileMode.CLAMP)
        } else {
            g1 = RadialGradient(0f, 0f, r, 0x555865F2, 0x005865F2, Shader.TileMode.CLAMP)
            g2 = RadialGradient(0f, 0f, r * 0.9f, 0x4438A0FF, 0x0038A0FF, Shader.TileMode.CLAMP)
            g3 = RadialGradient(0f, 0f, r * 0.8f, 0x33A06BFF, 0x00A06BFF, Shader.TileMode.CLAMP)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Fx.on(context)) {
            anim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 26000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { t = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(0xFF03050E.toInt())
        fun blob(g: RadialGradient?, x: Float, y: Float) {
            if (g == null) return
            c.save()
            c.translate(x, y)
            paint.shader = g
            c.drawCircle(0f, 0f, maxOf(w, h), paint)
            c.restore()
        }
        blob(g1, w * (0.2f + 0.12f * Math.sin(t.toDouble()).toFloat()), h * (0.12f + 0.06f * Math.cos(t.toDouble()).toFloat()))
        blob(g2, w * (0.9f + 0.1f * Math.cos(t * 1.0 + 1.0).toFloat()), h * (0.45f + 0.08f * Math.sin(t * 1.0 + 2.0).toFloat()))
        blob(g3, w * (0.3f + 0.1f * Math.sin(t * 1.0 + 4.0).toFloat()), h * (0.95f + 0.04f * Math.cos(t * 1.0 + 3.0).toFloat()))
    }
}

class LoginActivity : Activity() {
    private val cBg = 0xFF03050E.toInt()
    private val cBd = 0xFF22306B.toInt()
    private val cTx = 0xFFE6ECFF.toInt()
    private val cMut = 0xFF8E9BD0.toInt()
    private val cBad = 0xFFFF6B6B.toInt()
    private val cOk = 0xFF38D39F.toInt()
    private val cAcc = 0xFF8FB4FF.toInt()

    private lateinit var status: TextView
    private lateinit var titleTv: TextView
    private lateinit var subTv: TextView
    private lateinit var card: LinearLayout
    private lateinit var tabSignup: TextView
    private lateinit var tabLogin: TextView
    private lateinit var indicator: View
    private lateinit var tabBar: FrameLayout
    private lateinit var fUser: EditText
    private lateinit var fPass: EditText
    private lateinit var fPass2: EditText
    private lateinit var go: LinearLayout
    private lateinit var goLbl: TextView
    private var mode = "signup"
    private var showPw = false
    private var busyNow = false
    private val loops = ArrayList<Animator>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun attachBaseContext(base: Context) {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        if (cfg.fontScale > 1.0f) cfg.fontScale = 1.0f
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    private fun shape(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(stroke), strokeColor)
    }

    private fun tv(t: String, size: Float, color: Int = cTx, bold: Boolean = false, center: Boolean = false) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }

    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            false
        }
    }

    private fun field(hint: String, secret: Boolean) = EditText(this).apply {
        setHint(hint)
        setHintTextColor(0xFF5E6A9C.toInt())
        setTextColor(cTx)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = shape(0xFF070C20.toInt(), 13, 1, cBd)
        setOnFocusChangeListener { v, f -> v.background = shape(0xFF070C20.toInt(), 13, if (f) 2 else 1, if (f) cAcc else cBd) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        mode = if (Auth.accounts(this).isNotEmpty()) "login" else "signup"

        val frame = FrameLayout(this)
        frame.addView(AuroraView(this), FrameLayout.LayoutParams(FILL, FILL))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(36), dp(22), dp(28))
        }

        // logo with a glow ring that keeps spreading
        val logoBox = FrameLayout(this)
        val ring = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(2), cAcc); setColor(Color.TRANSPARENT) } }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.brry_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF000000.toInt()); setStroke(dp(2), cAcc) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            elevation = dp(6).toFloat()
        }
        logoBox.addView(ring, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        logoBox.addView(logo, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        root.addView(logoBox, LinearLayout.LayoutParams(dp(150), dp(120)))
        titleTv = tv("", 28f, cTx, true, true)
        subTv = tv("", 14f, cMut, false, true)
        root.addView(titleTv, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(6) })
        root.addView(subTv, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(4) })

        // Sign up | Log in
        tabBar = FrameLayout(this).apply { background = shape(0xCC0A1028.toInt(), 15, 1, cBd) }
        indicator = View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF3A55C8.toInt(), 0xFF2A3A82.toInt())).apply { cornerRadius = dp(12).toFloat() } }
        tabBar.addView(indicator, FrameLayout.LayoutParams(0, dp(38), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dp(4) })
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabSignup = tv("Sign up", 14f, cTx, true, true).apply { setOnClickListener { switchTab("signup") } }
        tabLogin = tv("Log in", 14f, cMut, true, true).apply { setOnClickListener { switchTab("login") } }
        tabRow.addView(tabSignup, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabRow.addView(tabLogin, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabBar.addView(tabRow, FrameLayout.LayoutParams(FILL, dp(46)))
        root.addView(tabBar, LinearLayout.LayoutParams(FILL, dp(46)).apply { topMargin = dp(18) })

        // the card with the fields
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF0131E4A.toInt(), 0xF0080D24.toInt())).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0xFF2A3A82.toInt())
            }
            elevation = dp(6).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fUser = field("Username", false)
        fPass = field("Password", true)
        fPass2 = field("Repeat password", true)
        fUser.setText(Cfg.gets(this, "last_user"))
        card.addView(fUser, LinearLayout.LayoutParams(FILL, HUG))
        val passBox = FrameLayout(this)
        passBox.addView(fPass, FrameLayout.LayoutParams(FILL, HUG))
        val showTv = tv("Show", 12.5f, cAcc, true).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            setOnClickListener {
                showPw = !showPw
                text = if (showPw) "Hide" else "Show"
                val t = if (showPw) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                fPass.inputType = InputType.TYPE_CLASS_TEXT or t
                fPass2.inputType = InputType.TYPE_CLASS_TEXT or t
                fPass.setSelection(fPass.text.length)
            }
        }
        passBox.addView(showTv, FrameLayout.LayoutParams(HUG, HUG, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(4) })
        card.addView(passBox, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(10) })
        card.addView(fPass2, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(10) })
        status = tv("", 13f, cMut, false, true).apply { visibility = View.GONE }
        card.addView(status, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(12) })
        goLbl = tv("", 16f, 0xFF0A1030.toInt(), true, true)
        go = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(52)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFF2F6FF.toInt(), 0xFF9DBDFF.toInt())).apply { cornerRadius = dp(16).toFloat() }
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { submit() }
        }
        pressFx(go)
        go.addView(goLbl, LinearLayout.LayoutParams(HUG, HUG))
        card.addView(go, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(14) })
        root.addView(card, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(14) })
        val foot = tv("Your account stays on this phone. Nothing to set up.", 11.5f, cMut, false, true)
        root.addView(foot, LinearLayout.LayoutParams(FILL, HUG).apply { topMargin = dp(14) })

        val sv = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        sv.addView(root, FrameLayout.LayoutParams(FILL, FILL))
        frame.addView(sv, FrameLayout.LayoutParams(FILL, FILL))
        setContentView(frame)

        applyMode(false)
        tabBar.post {
            val half = (tabBar.width - dp(8)) / 2
            indicator.layoutParams = (indicator.layoutParams as FrameLayout.LayoutParams).apply { width = half }
            applyMode(false)
        }
        Fx.pop(logo, 0)
        Fx.ripple(ring)?.let { loops.add(it) }
        Fx.float(logo, 3, 2800)?.let { loops.add(it) }
        Fx.rise(titleTv, 120)
        Fx.rise(subTv, 190)
        Fx.rise(tabBar, 260)
        Fx.rise(card, 330, 26, 480)
        Fx.rise(foot, 560, 10, 380)
    }

    override fun onDestroy() {
        for (a in loops) a.cancel()
        loops.clear()
        super.onDestroy()
    }

    private fun switchTab(m: String) {
        if (m == mode) return
        mode = m
        applyMode(true)
    }

    private fun applyMode(animate: Boolean) {
        val signup = mode == "signup"
        val t = if (signup) "Create your account" else "Welcome back"
        val s = if (signup) "Pick a username and a password" else "Log in to keep fishing"
        val g = if (signup) "Create account" else "Log in"
        if (animate) { Fx.swapText(titleTv, t); Fx.swapText(subTv, s); Fx.swapText(goLbl, g) } else { titleTv.text = t; subTv.text = s; goLbl.text = g }
        tabSignup.setTextColor(if (signup) cTx else cMut)
        tabLogin.setTextColor(if (signup) cMut else cTx)
        if (signup) {
            if (fPass2.visibility != View.VISIBLE) { fPass2.visibility = View.VISIBLE; if (animate) Fx.rise(fPass2, 0, 10, 260) }
        } else fPass2.visibility = View.GONE
        val half = (tabBar.width - dp(8)) / 2
        val x = if (signup) 0f else half.toFloat()
        if (animate && Fx.on(this)) indicator.animate().translationX(x).setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start() else indicator.translationX = x
        status.visibility = View.GONE
    }

    private fun submit() {
        if (busyNow) return
        val u = fUser.text.toString().trim()
        val p = fPass.text.toString()
        if (u.isEmpty() || p.isEmpty()) { fail("Enter a username and a password"); return }
        if (mode == "signup" && p != fPass2.text.toString()) { fail("The two passwords are not the same"); return }
        busyNow = true
        go.alpha = 0.6f
        show("One moment…", cMut)
        val app = applicationContext
        val m = mode
        Thread {
            // newest blacklist first (a stored one is used when offline)
            try { Blacklist.refreshBlocking(app) } catch (_: Exception) {}
            val r = if (m == "signup") Auth.signUp(app, u, p) else Auth.logIn(app, u, p)
            runOnUiThread {
                busyNow = false
                go.alpha = 1f
                if (r.banned) goBanned()
                else if (!r.ok) fail(r.msg)
                else {
                    Cfg.puts(app, "last_user", u)
                    welcome(r.msg)
                }
            }
        }.start()
    }

    private fun show(t: String, color: Int) {
        status.setTextColor(color)
        if (status.visibility != View.VISIBLE) { status.text = t; status.visibility = View.VISIBLE; Fx.rise(status, 0, 8, 240) } else Fx.swapText(status, t)
    }

    private fun fail(t: String) {
        show(t, cBad)
        Fx.shake(card)
    }

    private fun welcome(msg: String) {
        val who = msg.substringAfter(":")
        show((if (msg.startsWith("new:")) "✓  Account created. Welcome, " else "✓  Welcome back, ") + who, cOk)
        val c = card
        c.postDelayed({ Fx.out(c, 320) { goMain() } }, if (Fx.on(this)) 650L else 0L)
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun goBanned() {
        startActivity(Intent(this, BannedActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
