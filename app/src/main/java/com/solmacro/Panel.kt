package com.solmacro

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MC = ViewGroup.LayoutParams.MATCH_PARENT

private fun alphaOf(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

class Glyph(ctx: Context, private val kind: Int) : View(ctx) {
    var color: Int = Color.WHITE
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeWidth = s * 0.1f
        p.strokeCap = Paint.Cap.ROUND
        when (kind) {
            PLAY -> {
                val t = Path()
                t.moveTo(cx - s * 0.22f, cy - s * 0.32f)
                t.lineTo(cx + s * 0.34f, cy)
                t.lineTo(cx - s * 0.22f, cy + s * 0.32f)
                t.close()
                c.drawPath(t, p)
            }
            BARS -> {
                c.drawRoundRect(RectF(cx - s * 0.38f, cy + s * 0.05f, cx - s * 0.18f, cy + s * 0.38f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx - s * 0.1f, cy - s * 0.12f, cx + s * 0.1f, cy + s * 0.38f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx + s * 0.18f, cy - s * 0.38f, cx + s * 0.38f, cy + s * 0.38f), 3f, 3f, p)
            }
            GEAR -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, s * 0.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = cx + (Math.cos(a) * s * 0.28).toFloat()
                    val y1 = cy + (Math.sin(a) * s * 0.28).toFloat()
                    val x2 = cx + (Math.cos(a) * s * 0.42).toFloat()
                    val y2 = cy + (Math.sin(a) * s * 0.42).toFloat()
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            BOOK -> {
                c.drawRoundRect(RectF(cx - s * 0.4f, cy - s * 0.3f, cx - s * 0.03f, cy + s * 0.32f), 3f, 3f, p)
                c.drawRoundRect(RectF(cx + s * 0.03f, cy - s * 0.3f, cx + s * 0.4f, cy + s * 0.32f), 3f, 3f, p)
            }
            CLOCK -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, s * 0.38f, p)
                c.drawLine(cx, cy, cx, cy - s * 0.22f, p)
                c.drawLine(cx, cy, cx + s * 0.16f, cy + s * 0.08f, p)
            }
            STAR -> {
                val t = Path()
                t.moveTo(cx, cy - s * 0.45f)
                t.quadTo(cx, cy, cx + s * 0.45f, cy)
                t.quadTo(cx, cy, cx, cy + s * 0.45f)
                t.quadTo(cx, cy, cx - s * 0.45f, cy)
                t.quadTo(cx, cy, cx, cy - s * 0.45f)
                t.close()
                c.drawPath(t, p)
            }
            BOLT -> {
                val t = Path()
                t.moveTo(cx + s * 0.1f, cy - s * 0.45f)
                t.lineTo(cx - s * 0.25f, cy + s * 0.05f)
                t.lineTo(cx, cy + s * 0.05f)
                t.lineTo(cx - s * 0.1f, cy + s * 0.45f)
                t.lineTo(cx + s * 0.25f, cy - s * 0.1f)
                t.lineTo(cx, cy - s * 0.1f)
                t.close()
                c.drawPath(t, p)
            }
            CHEV -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - s * 0.12f, cy - s * 0.22f, cx + s * 0.12f, cy, p)
                c.drawLine(cx + s * 0.12f, cy, cx - s * 0.12f, cy + s * 0.22f, p)
            }
            else -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - s * 0.25f, cy - s * 0.25f, cx + s * 0.25f, cy + s * 0.25f, p)
                c.drawLine(cx + s * 0.25f, cy - s * 0.25f, cx - s * 0.25f, cy + s * 0.25f, p)
            }
        }
    }

    companion object {
        const val PLAY = 0
        const val BARS = 1
        const val GEAR = 2
        const val BOOK = 3
        const val CLOCK = 4
        const val STAR = 5
        const val BOLT = 6
        const val CHEV = 7
        const val CLOSE = 8
    }
}


// Small switch: light blue track with a dark knob when ON, like the LuminFish design.
class Tog(ctx: Context, var on: Boolean, private val change: (Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argb = ArgbEvaluator()
    private var pos = if (on) 1f else 0f
    private var anim: ValueAnimator? = null

    init {
        setOnClickListener {
            on = !on
            change(on)
            anim?.cancel()
            val a = ValueAnimator.ofFloat(pos, if (on) 1f else 0f)
            a.duration = 240
            a.interpolator = OvershootInterpolator(1.1f)
            a.addUpdateListener {
                pos = it.animatedValue as Float
                invalidate()
            }
            a.start()
            anim = a
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(OvershootInterpolator(3f)).start()
        }
        return super.onTouchEvent(e)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = pos.coerceIn(0f, 1f)
        val g = h * 0.08f
        if (t > 0.02f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.22f
            p.color = alphaOf(Pal.GLOW, (70 * t).toInt())
            c.drawRoundRect(RectF(g, g, w - g, h - g), h / 2, h / 2, p)
        }
        p.style = Paint.Style.FILL
        p.color = argb.evaluate(t, 0xFF141C42.toInt(), 0xFFB4C8FF.toInt()) as Int
        c.drawRoundRect(RectF(g, g, w - g, h - g), h / 2, h / 2, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.07f
        p.color = argb.evaluate(t, 0x884A5FB0.toInt(), 0xFFE6EDFF.toInt()) as Int
        c.drawRoundRect(RectF(g, g, w - g, h - g), h / 2, h / 2, p)
        val kx = h / 2 + (w - h) * t
        p.style = Paint.Style.FILL
        p.color = argb.evaluate(t, 0xFF8A99D8.toInt(), 0xFF0B1030.toInt()) as Int
        c.drawCircle(kx, h / 2, h * 0.34f, p)
        if (t > 0.02f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = h * 0.05f
            p.color = alphaOf(Pal.ICE, (200 * t).toInt())
            c.drawCircle(kx, h / 2, h * 0.34f, p)
        }
    }
}

// The big "Toggle to start" switch. The knob slides, the label changes, a light sweeps across it while idle.
class StartToggle(ctx: Context) : View(ctx) {
    var on = false
        set(v) {
            if (field == v) return
            field = v
            slideTo(if (v) 1f else 0f)
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argb = ArgbEvaluator()
    private val rect = RectF()
    private val tri = Path()
    private val mat = Matrix()
    private var sweep: LinearGradient? = null
    private var pos = 0f
    private var press = 0f
    private var breathe = 0f
    private var anim: ValueAnimator? = null
    private var loop: ValueAnimator? = null

    init { isClickable = true }

    private fun slideTo(t: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(pos, t)
        a.duration = 330
        a.interpolator = OvershootInterpolator(1.2f)
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    // moves the knob right away when tapped; refresh() puts it back if Start was refused
    fun optimistic() { slideTo(if (on) 0f else 1f) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sweep = LinearGradient(0f, 0f, w * 0.4f, 0f, intArrayOf(0x00FFFFFF, 0x40BCD0FF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alpha = 0f
        translationY = 24f
        animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(420).setInterpolator(DecelerateInterpolator()).start()
        loop?.cancel()
        val l = ValueAnimator.ofFloat(0f, 1f)
        l.duration = 1800
        l.repeatCount = ValueAnimator.INFINITE
        l.interpolator = LinearInterpolator()
        l.addUpdateListener {
            breathe = ((Math.sin(SystemClock.uptimeMillis() / 380.0) + 1.0) / 2.0).toFloat()
            invalidate()
        }
        l.start()
        loop = l
    }

    override fun onDetachedFromWindow() {
        loop?.cancel()
        anim?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { press = 1f; animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).setInterpolator(DecelerateInterpolator()).start() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                press = 0f
                animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(3f)).start()
            }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = pos.coerceIn(0f, 1f)
        val pad = h * 0.12f
        val kr = h / 2 - pad
        val g = h * 0.04f
        // glow around the track
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.12f
        p.color = alphaOf(if (t > 0.5f) Pal.OK else Pal.GLOW, (25 + 60 * breathe).toInt())
        rect.set(g, g, w - g, h - g)
        c.drawRoundRect(rect, h / 2, h / 2, p)
        // track
        p.style = Paint.Style.FILL
        p.color = argb.evaluate(t, 0xFF0B1236.toInt(), 0xFF10382E.toInt()) as Int
        c.drawRoundRect(rect, h / 2, h / 2, p)
        // light sweep
        val sw = sweep
        if (sw != null && t < 0.5f) {
            val x = -w * 0.4f + ((SystemClock.uptimeMillis() % 2600L) / 2600f) * w * 1.4f
            mat.setTranslate(x, 0f)
            sw.setLocalMatrix(mat)
            sweepP.shader = sw
            c.save()
            val clip = Path()
            clip.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW)
            c.clipPath(clip)
            c.drawRect(0f, 0f, w, h, sweepP)
            c.restore()
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.05f
        p.color = argb.evaluate(t, 0xCC8FB4FF.toInt(), 0xDD38D39F.toInt()) as Int
        c.drawRoundRect(rect, h / 2, h / 2, p)
        // knob
        val kx = pad + kr + (w - 2 * pad - 2 * kr) * t
        val ks = 1f - 0.08f * press
        p.style = Paint.Style.FILL
        p.color = Pal.ICE
        c.drawCircle(kx, h / 2, kr * ks, p)
        p.color = 0xFF0A1030.toInt()
        if (t < 0.999f) {
            p.alpha = (255 * (1f - t)).toInt()
            tri.reset()
            tri.moveTo(kx - kr * 0.28f, h / 2 - kr * 0.42f)
            tri.lineTo(kx + kr * 0.46f, h / 2)
            tri.lineTo(kx - kr * 0.28f, h / 2 + kr * 0.42f)
            tri.close()
            c.drawPath(tri, p)
        }
        if (t > 0.001f) {
            p.color = 0xFF0A1030.toInt()
            p.alpha = (255 * t).toInt()
            c.drawRoundRect(RectF(kx - kr * 0.32f, h / 2 - kr * 0.32f, kx + kr * 0.32f, h / 2 + kr * 0.32f), kr * 0.08f, kr * 0.08f, p)
        }
        val label = if (t >= 0.5f) "Toggle to stop" else "Toggle to start"
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = h * 0.34f
        p.textAlign = Paint.Align.CENTER
        val a = Math.abs(t - 0.5f) * 2f
        p.color = Pal.ICE
        p.alpha = (255 * a).toInt()
        val rightC = (kx + kr + w) / 2f
        val leftC = (kx - kr) / 2f
        val tx = if (t < 0.5f) rightC else leftC
        c.drawText(label, tx, h / 2 - (p.descent() + p.ascent()) / 2f, p)
        p.alpha = 255
        p.textAlign = Paint.Align.LEFT
    }
}

// Little bar chart: catches in each of the last minutes. Bars grow smoothly when new numbers arrive.
class Spark(ctx: Context) : View(ctx) {
    private var data = IntArray(20)
    private var shown = FloatArray(20)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null

    fun set(d: IntArray) {
        if (d.size != data.size || d.contentEquals(data)) return
        val from = shown.copyOf()
        data = d.copyOf()
        anim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 500
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val f = it.animatedValue as Float
            for (i in shown.indices) shown[i] = from[i] + (data[i] - from[i]) * f
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val n = shown.size
        val slot = w / n
        var mx = 1f
        for (v in shown) if (v > mx) mx = v
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val bh = maxOf(3f, (h - 4f) * shown[i] / mx)
            p.color = if (i == n - 1) Pal.GLOW else 0x4D8FB4FF
            c.drawRoundRect(RectF(i * slot + slot * 0.15f, h - bh, (i + 1) * slot - slot * 0.15f, h), 4f, 4f, p)
        }
    }
}

// The big emblem in the middle of the Macro page: it is the start button too. Glow, orbiting planet,
// twinkling stars, a ring that spins while the macro runs, ripple when tapped.
class PlayBtn(ctx: Context) : View(ctx) {
    // 0 = idle, 1 = running
    var mode = 0
        set(v) {
            if (field == v) return
            field = v
            startRipple()
        }
    var paused = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spk = Path()
    private val argb = ArgbEvaluator()
    private val emblem: Bitmap? = try {
        BitmapFactory.decodeResource(ctx.resources, R.drawable.lumin_emblem, BitmapFactory.Options().apply { inScaled = false })
    } catch (e: Throwable) { null }

    private var angle = 0f
    private var spd = 30f
    private var lastT = 0L
    private var tint = Pal.GLOW
    private var ripple = 0f
    private var press = 0f
    private var loop: ValueAnimator? = null
    private var rippleAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null

    private fun startRipple() {
        rippleAnim?.cancel()
        val a = ValueAnimator.ofFloat(0.001f, 1f)
        a.duration = 700
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            ripple = it.animatedValue as Float
            if (ripple >= 1f) ripple = 0f
            invalidate()
        }
        a.start()
        rippleAnim = a
    }

    private fun pressTo(target: Float) {
        pressAnim?.cancel()
        val a = ValueAnimator.ofFloat(press, target)
        if (target > 0f) {
            a.duration = 110
            a.interpolator = DecelerateInterpolator()
        } else {
            a.duration = 420
            a.interpolator = OvershootInterpolator(3f)
        }
        a.addUpdateListener {
            press = it.animatedValue as Float
            invalidate()
        }
        a.start()
        pressAnim = a
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressTo(1f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressTo(0f)
        }
        return super.onTouchEvent(e)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(520)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        lastT = SystemClock.uptimeMillis()
        loop?.cancel()
        val l = ValueAnimator.ofFloat(0f, 1f)
        l.duration = 1000
        l.repeatCount = ValueAnimator.INFINITE
        l.interpolator = LinearInterpolator()
        l.addUpdateListener {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastT).coerceIn(0L, 100L)) / 1000f
            lastT = now
            val targetSpd = if (mode == 1) (if (paused) 0f else 150f) else 30f
            spd += (targetSpd - spd) * minOf(1f, dt * 4f)
            angle = (angle + spd * dt) % 360f
            val targetCol = if (mode == 1) (if (paused) Pal.WARN else Pal.OK) else Pal.GLOW
            tint = argb.evaluate(minOf(1f, dt * 5f), tint, targetCol) as Int
            invalidate()
        }
        l.start()
        loop = l
    }

    override fun onDetachedFromWindow() {
        loop?.cancel()
        rippleAnim?.cancel()
        pressAnim?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun spark(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        spk.reset()
        spk.moveTo(x, y - s)
        spk.quadTo(x, y, x + s, y)
        spk.quadTo(x, y, x, y + s)
        spk.quadTo(x, y, x - s, y)
        spk.quadTo(x, y, x, y - s)
        spk.close()
        p.style = Paint.Style.FILL
        p.color = alphaOf(Color.WHITE, (255 * a).toInt())
        c.drawPath(spk, p)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.5f
        val now = SystemClock.uptimeMillis()
        val breath = ((Math.sin(now / 650.0) + 1.0) / 2.0).toFloat()
        val r = base * (1f - 0.06f * press)
        // breathing glow
        p.style = Paint.Style.FILL
        p.color = alphaOf(tint, (16 + 34 * breath).toInt())
        c.drawCircle(cx, cy, r * (0.95f + 0.1f * breath), p)
        // emblem
        val em = emblem
        if (em != null) {
            val ew = minOf(width * 0.96f, r * 2f * em.width / em.height * 0.98f)
            val eh = ew * em.height / em.width
            val sc = 1f + 0.025f * breath - 0.05f * press
            c.save()
            c.rotate(1.6f * Math.sin(now / 1100.0).toFloat(), cx, cy)
            c.scale(sc, sc, cx, cy)
            c.drawBitmap(em, null, RectF(cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2), bmpP)
            c.restore()
        }
        // orbiting planet on a tilted ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = 0x558FB4FF
        c.save()
        c.rotate(-18f, cx, cy)
        val ra = r * 1.55f
        val rb = r * 0.5f
        c.drawOval(RectF(cx - ra, cy - rb, cx + ra, cy + rb), p)
        val t = Math.toRadians(angle.toDouble())
        val px = cx + (Math.cos(t) * ra).toFloat()
        val py = cy + (Math.sin(t) * rb).toFloat()
        p.style = Paint.Style.FILL
        p.color = alphaOf(tint, 90)
        c.drawCircle(px, py, r * 0.12f, p)
        p.color = Color.WHITE
        c.drawCircle(px, py, r * 0.06f, p)
        c.restore()
        // twinkling sparkles
        val a1 = ((Math.sin(now / 420.0) + 1.0) / 2.0).toFloat()
        val a2 = ((Math.sin(now / 530.0 + 2.0) + 1.0) / 2.0).toFloat()
        val a3 = ((Math.sin(now / 610.0 + 4.0) + 1.0) / 2.0).toFloat()
        spark(c, cx - r * 1.45f, cy - r * 0.55f, r * (0.08f + 0.09f * a1), 0.25f + 0.75f * a1)
        spark(c, cx + r * 1.45f, cy + r * 0.5f, r * (0.07f + 0.09f * a2), 0.25f + 0.75f * a2)
        spark(c, cx + r * 0.5f, cy - r * 0.85f, r * (0.05f + 0.07f * a3), 0.25f + 0.75f * a3)
        // spinning arc while running
        if (mode == 1) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.07f
            p.strokeCap = Paint.Cap.ROUND
            p.color = alphaOf(tint, 235)
            val rr = r * 0.82f
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), angle * 2f, 80f, false, p)
            p.strokeCap = Paint.Cap.BUTT
        }
        // tap ripple
        if (ripple > 0f) {
            p.style = Paint.Style.STROKE
            val rad = r * 0.5f + (r * 1.4f) * ripple
            p.strokeWidth = r * 0.06f * (1f - ripple) + 1f
            p.color = alphaOf(tint, (170 * (1f - ripple)).toInt())
            c.drawCircle(cx, cy, rad, p)
        }
    }
}

class PanelUi(private val svc: MacroService, private val widthPx: Int, private val heightPx: Int) {
    private val ctx: Context = svc
    private val d = ctx.resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    val root = FrameLayout(ctx)
    private val narrow = widthPx < (500 * d)
    private val pages = ArrayList<View>()
    private val navRows = ArrayList<LinearLayout>()
    private val navGlyphs = ArrayList<Glyph>()
    private val navTexts = ArrayList<TextView>()
    private var hint: ObjectAnimator? = null
    private var dotAnim: ObjectAnimator? = null
    private var noticeUntil = 0L
    private var firstPage = true
    private var curPage = 0
    private val checkRows = ArrayList<TextView>()
    private val checkState = IntArray(3) { -1 }
    private val introViews = ArrayList<View>()
    private lateinit var checkBox: LinearLayout
    private lateinit var toggle: StartToggle
    private lateinit var spark: Spark

    private lateinit var pill: TextView
    private lateinit var dot: View
    private lateinit var play: PlayBtn
    private lateinit var tapT: TextView
    private lateinit var tapS: TextView
    private lateinit var pauseB: TextView
    private lateinit var vCatch: TextView
    private lateinit var vTime: TextView
    private lateinit var vState: TextView
    private lateinit var statsText: TextView
    private lateinit var capVal: TextView
    private lateinit var tapVal: TextView

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 500)
        }
    }

    private val muted = Pal.MUTED
    private val line = Pal.LINE

    private fun dp(v: Int) = (v * d).toInt()

    // press squish for anything tappable (returns false so the normal click still happens)
    private fun <T : View> fx(v: T): T {
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                    .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(240)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
        return v
    }

    // changes a text and gives it a little pop when it is different
    private fun bump(t: TextView, text: String) {
        if (t.text.toString() == text) return
        t.text = text
        t.scaleX = 1.22f
        t.scaleY = 1.22f
        t.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    private fun fadeColor(t: TextView, to: Int) {
        val from = t.currentTextColor
        if (from == to) return
        ObjectAnimator.ofArgb(t, "textColor", from, to).apply {
            duration = 260
            start()
        }
    }

    private fun shake(v: View) {
        val s = dp(10).toFloat()
        ObjectAnimator.ofFloat(v, "translationX", 0f, -s, s, -s * 0.8f, s * 0.8f, -s * 0.4f, 0f).apply {
            duration = 400
            start()
        }
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun lin(o: Int) = LinearLayout(ctx).apply { orientation = o }
    private fun flp(w: Int, h: Int) = FrameLayout.LayoutParams(w, h)

    private fun shape(fill: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun tv(t: String, sp: Float, color: Int = Pal.TEXT, bold: Boolean = false) = TextView(ctx).apply {
        text = t
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    init {
        val rr = 22f * d
        root.clipToOutline = true
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, rr) }
        }
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.PANEL_TOP, Pal.PANEL_BOT))
        // the photo or GIF the user picked in the app (Look > Panel background)
        val bgd = PanelBg.load(ctx, 1280)
        if (bgd != null) {
            val iv = ImageView(ctx)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(bgd)
            root.addView(iv, flp(MC, MC))
            val dim = View(ctx)
            dim.setBackgroundColor(Color.argb((Cfg.get(ctx, "bg_dim").coerceIn(0, 90) * 255) / 100, 2, 4, 14))
            root.addView(dim, flp(MC, MC))
        }
        val sf = StarField(ctx, if (bgd != null) 22 else 54)
        sf.strength = if (bgd != null) 0.7f else 1f
        root.addView(sf, flp(MC, MC))

        val col = lin(LinearLayout.VERTICAL)
        col.setPadding(dp(18), dp(15), dp(18), dp(14))
        val head = header()
        col.addView(head, lp(MC, WC))
        val body = lin(LinearLayout.HORIZONTAL)
        val side = sidebar()
        body.addView(side, lp(if (narrow) dp(46) else dp(92), MC))
        val center = FrameLayout(ctx)
        pages.add(scroll(pageMacro()))
        pages.add(scroll(pageStats()))
        pages.add(scroll(pageSettings()))
        pages.add(scroll(pageCredits()))
        for (p in pages) center.addView(p, flp(MC, MC))
        body.addView(center, lp(0, MC, 1f).apply { leftMargin = dp(7) })
        var right: View? = null
        if (!narrow) {
            right = rightColumn()
            body.addView(right, lp(dp(150), MC).apply { leftMargin = dp(7) })
        }
        col.addView(body, lp(MC, 0, 1f).apply { topMargin = dp(3) })
        val sb = statsBar()
        col.addView(sb, lp(MC, dp(40)).apply { topMargin = dp(6) })
        root.addView(col, flp(MC, MC))
        val gf = GlowFrame(ctx)
        gf.radius = 22f
        root.addView(gf, flp(MC, MC))
        introViews.add(head)
        introViews.add(side)
        introViews.add(center)
        if (right != null) introViews.add(right)
        introViews.add(sb)
        showPage(0)
        refresh()
    }

    private fun scroll(v: View): ScrollView = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(12))
        setPadding(0, 0, 0, dp(6))
        clipToPadding = false
        addView(v, ViewGroup.LayoutParams(MC, WC))
    }

    // the parts slide in one after another when the panel opens
    private fun intro() {
        for ((i, v) in introViews.withIndex()) {
            v.alpha = 0f
            v.translationY = dp(14).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 70L).setDuration(380)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    fun start() {
        intro()
        ui.post(tick)
        dotAnim?.cancel()
        dotAnim = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.35f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    fun stop() {
        ui.removeCallbacks(tick)
        hint?.cancel()
        hint = null
        dotAnim?.cancel()
        dotAnim = null
    }

    private fun loadLogo(v: ImageView) {
        val f = java.io.File(ctx.filesDir, "float_icon.png")
        if (Cfg.get(ctx, "icon_custom") == 1 && f.exists()) {
            val b = BitmapFactory.decodeFile(f.absolutePath)
            if (b != null) { v.setImageBitmap(b); return }
        }
        v.setImageResource(R.drawable.brry_icon)
    }

    private fun circleBtn(kind: Int, click: () -> Unit): View {
        val f = FrameLayout(ctx)
        f.background = shape(0xFF0C1330.toInt(), 15, 0x668FB4FF)
        val g = Glyph(ctx, kind)
        g.color = Pal.ICE
        f.addView(g, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        f.setOnClickListener { click() }
        return fx(f)
    }

    private fun header(): View {
        val h = lin(LinearLayout.HORIZONTAL)
        h.gravity = Gravity.CENTER_VERTICAL
        h.setPadding(dp(2), dp(0), dp(2), dp(4))
        val logo = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(0xFF050818.toInt(), 11, 0x888FB4FF.toInt())
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        loadLogo(logo)
        h.addView(logo, lp(dp(38), dp(38)))
        val titles = lin(LinearLayout.VERTICAL)
        val nameRow = lin(LinearLayout.HORIZONTAL)
        nameRow.gravity = Gravity.CENTER_VERTICAL
        nameRow.addView(tv("LuminFish", 17f, Pal.ICE, true))
        val st = Glyph(ctx, Glyph.STAR)
        st.color = Pal.GLOW
        nameRow.addView(st, lp(dp(12), dp(12)).apply { leftMargin = dp(5) })
        titles.addView(nameRow)
        titles.addView(tv("Sol’s RNG Macro", 10f, muted))
        h.addView(titles, lp(0, WC, 1f).apply { leftMargin = dp(9) })
        val pb = lin(LinearLayout.HORIZONTAL)
        pb.gravity = Gravity.CENTER_VERTICAL
        pb.setPadding(dp(10), dp(5), dp(11), dp(5))
        pb.background = shape(0xFF0A1226.toInt(), 16, 0x668FB4FF)
        dot = View(ctx)
        pb.addView(dot, lp(dp(8), dp(8)))
        pill = tv("Ready", 12f, Pal.TEXT, true)
        pb.addView(pill, lp(WC, WC).apply { leftMargin = dp(6) })
        h.addView(pb)
        h.addView(circleBtn(Glyph.GEAR) { showPage(2) }, lp(dp(30), dp(30)).apply { leftMargin = dp(7) })
        h.addView(circleBtn(Glyph.CLOSE) { svc.closePanel() }, lp(dp(30), dp(30)).apply { leftMargin = dp(7) })
        return h
    }

    private fun selBg() = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFF4F7FF.toInt(), 0xFFB4C8FF.toInt())).apply {
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Pal.GLOW)
    }

    private fun sidebar(): View {
        val col = lin(LinearLayout.VERTICAL)
        val names = listOf("Macro", "Stats", "Settings", "Credits")
        val kinds = listOf(Glyph.PLAY, Glyph.BARS, Glyph.GEAR, Glyph.BOOK)
        for (i in names.indices) {
            val row = lin(LinearLayout.HORIZONTAL)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(if (narrow) 0 else 9), dp(6), dp(if (narrow) 0 else 6), dp(6))
            if (narrow) row.gravity = Gravity.CENTER
            val g = Glyph(ctx, kinds[i])
            row.addView(g, lp(dp(18), dp(18)))
            val t = tv(names[i], 12.5f)
            if (!narrow) row.addView(t, lp(WC, WC).apply { leftMargin = dp(7) })
            row.setOnClickListener { showPage(i) }
            fx(row)
            col.addView(row, lp(MC, dp(32)).apply { topMargin = dp(3) })
            navRows.add(row)
            navGlyphs.add(g)
            navTexts.add(t)
        }
        col.addView(View(ctx), lp(MC, 0, 1f))
        if (!narrow) {
            val ver = lin(LinearLayout.VERTICAL)
            ver.background = shape(Pal.CARD, 12, line)
            ver.setPadding(dp(9), dp(6), dp(9), dp(6))
            val vr = lin(LinearLayout.HORIZONTAL)
            vr.gravity = Gravity.CENTER_VERTICAL
            val sg = Glyph(ctx, Glyph.STAR)
            sg.color = Pal.GLOW
            vr.addView(sg, lp(dp(11), dp(11)))
            vr.addView(tv("LuminFish v2.3", 10.5f, Pal.TEXT, true), lp(WC, WC).apply { leftMargin = dp(5) })
            ver.addView(vr)
            ver.addView(tv("Made for Sol’s RNG", 9f, muted))
            col.addView(ver, lp(MC, WC).apply { topMargin = dp(4) })
        }
        return col
    }

    private fun showPage(i: Int) {
        val prev = curPage
        curPage = i
        for (k in pages.indices) {
            val pg = pages[k]
            if (k == i) {
                val wasHidden = pg.visibility != View.VISIBLE
                pg.animate().cancel()
                pg.visibility = View.VISIBLE
                if (wasHidden && !firstPage) {
                    pg.alpha = 0f
                    pg.translationX = dp(22).toFloat()
                    pg.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
                } else {
                    pg.alpha = 1f
                    pg.translationX = 0f
                }
            } else {
                pg.animate().cancel()
                pg.visibility = View.GONE
                pg.alpha = 1f
                pg.translationX = 0f
            }
        }
        for (k in navRows.indices) {
            val sel = k == i
            navRows[k].background = if (sel) selBg() else null
            val c = if (sel) 0xFF0A1030.toInt() else Pal.TEXT
            navGlyphs[k].color = c
            navGlyphs[k].invalidate()
            navTexts[k].setTextColor(c)
            if (sel && k != prev && !firstPage) {
                navRows[k].scaleX = 0.9f
                navRows[k].scaleY = 0.9f
                navRows[k].animate().scaleX(1f).scaleY(1f).setDuration(330).setInterpolator(OvershootInterpolator(2.4f)).start()
            }
        }
        firstPage = false
        if (i == 0 || i == 1) refresh()
    }

    private fun titleBar(title: String, kind: Int, both: Boolean): LinearLayout {
        val t = lin(LinearLayout.HORIZONTAL)
        t.gravity = Gravity.CENTER
        t.background = shape(0x2A5F7CFF, 11)
        t.setPadding(dp(8), dp(5), dp(8), dp(5))
        val a = Glyph(ctx, kind)
        a.color = Pal.GLOW
        t.addView(a, lp(dp(14), dp(14)))
        t.addView(tv(title, 12.5f, Pal.GLOW, true), lp(WC, WC).apply { leftMargin = dp(7); rightMargin = if (both) dp(7) else 0 })
        if (both) {
            val b = Glyph(ctx, kind)
            b.color = Pal.GLOW
            t.addView(b, lp(dp(14), dp(14)))
        }
        return t
    }

    private fun card(title: String?, kind: Int = Glyph.STAR, both: Boolean = false): LinearLayout {
        val c = lin(LinearLayout.VERTICAL)
        c.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xCC111B42.toInt(), Pal.CARD)).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), line)
        }
        c.setPadding(dp(8), dp(7), dp(8), dp(8))
        if (title != null) c.addView(titleBar(title, kind, both), lp(MC, WC))
        return c
    }

    private fun smallBtn(t: String, click: () -> Unit): TextView {
        val b = tv(t, 11.5f, Pal.TEXT, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(12), dp(6), dp(12), dp(6))
        b.background = shape(0xFF0F1838.toInt(), 13, 0x668FB4FF)
        b.setOnClickListener { click() }
        return fx(b)
    }

    private fun pageMacro(): View {
        val c = card("Auto Fish", Glyph.STAR, true)
        c.gravity = Gravity.CENTER_HORIZONTAL
        play = PlayBtn(ctx)
        play.setOnClickListener { tryStart() }
        // the emblem takes whatever height is left so Tap to Start, the buttons and the chips are never cut off
        val availDp = heightPx / d - 120f
        val emblemDp = (availDp - 182f).coerceIn(48f, 92f).toInt()
        c.addView(play, lp(MC, dp(emblemDp)).apply { topMargin = dp(2) })
        tapT = tv("Tap to Start", 15f, Pal.ICE, true)
        c.addView(tapT, lp(WC, WC))
        tapS = tv("Let LuminFish do the work…", 10.5f, muted)
        tapS.gravity = Gravity.CENTER
        c.addView(tapS, lp(WC, WC))
        toggle = StartToggle(ctx)
        toggle.setOnClickListener { tryStart() }
        c.addView(toggle, lp(dp(200), dp(32)).apply { topMargin = dp(6) })
        val rowB = lin(LinearLayout.HORIZONTAL)
        rowB.gravity = Gravity.CENTER
        pauseB = smallBtn("Pause") { svc.togglePause() }
        pauseB.visibility = View.GONE
        rowB.addView(pauseB, lp(WC, WC).apply { rightMargin = dp(6) })
        rowB.addView(smallBtn("Show auto clickers") { svc.closePanel(); svc.showClickers() })
        c.addView(rowB, lp(WC, WC).apply { topMargin = dp(7) })

        // live "ready to start" checks. A red chip says what is stopping Start, tap it to fix it.
        checkBox = lin(LinearLayout.HORIZONTAL)
        val short = listOf("Positions", "Capture", "Roblox")
        for (k in 0..2) {
            val t = tv(short[k], 10.5f, muted, true)
            t.gravity = Gravity.CENTER_VERTICAL
            t.setPadding(dp(8), dp(4), dp(8), dp(4))
            t.setOnClickListener {
                when (k) {
                    0 -> { svc.closePanel(); svc.setPoints() }
                    1 -> svc.openApp(true)
                    else -> svc.openRobloxApp()
                }
            }
            fx(t)
            checkBox.addView(t, lp(WC, WC).apply { if (k > 0) leftMargin = dp(4) })
            checkRows.add(t)
        }
        c.addView(checkBox, lp(WC, WC).apply { topMargin = dp(7) })
        return c
    }

    // Both the emblem and the toggle come here.
    private fun tryStart() {
        val running = svc.isRunning()
        val why = if (running) null else svc.startProblem()
        if (why == null) toggle.optimistic()
        svc.toggle()
        if (why != null) blocked(why)
        ui.postDelayed({ refresh() }, 160)
    }

    // Start was refused: shake, say why in the panel, jump to Settings if the positions are missing.
    private fun blocked(why: String) {
        shake(play)
        shake(toggle)
        tapS.text = why
        tapS.setTextColor(Pal.WARN)
        tapS.alpha = 1f
        noticeUntil = SystemClock.uptimeMillis() + 5000L
        checkBox.scaleX = 1.06f
        checkBox.scaleY = 1.06f
        checkBox.animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(OvershootInterpolator(3f)).start()
        if (Cfg.get(ctx, "pts_ok") != 1) { svc.closePanel(); svc.showClickers() }
        else if (!Cap.active) { svc.closePanel(); svc.openApp(true) }
    }

    private fun pageStats(): View {
        val c = card("Stats", Glyph.BARS)
        statsText = tv("", 10.5f, Pal.TEXT)
        statsText.typeface = Typeface.MONOSPACE
        c.addView(statsText, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(tv("Catches per minute, last 20 minutes", 10f, muted), lp(MC, WC).apply { topMargin = dp(10) })
        spark = Spark(ctx)
        c.addView(spark, lp(MC, dp(56)).apply { topMargin = dp(5) })
        return c
    }

    // a small button that steps through a list of values and saves the choice
    private fun cycle(key: String, opts: IntArray, fmt: (Int) -> String): TextView {
        val t = tv(fmt(Cfg.get(ctx, key)), 11f, Pal.TEXT)
        t.gravity = Gravity.CENTER
        t.background = shape(0xFF0F1838.toInt(), 12, 0x668FB4FF)
        t.setPadding(dp(6), dp(4), dp(6), dp(4))
        t.setOnClickListener {
            val cur = Cfg.get(ctx, key)
            var nxt = opts[0]
            for (k in opts.indices) if (opts[k] == cur && k + 1 < opts.size) nxt = opts[k + 1]
            Cfg.put(ctx, key, nxt)
            t.text = fmt(nxt)
        }
        fx(t)
        return t
    }

    private fun actionRow(label: String, click: () -> Unit): View {
        val r = lin(LinearLayout.HORIZONTAL)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = shape(0x99101A3E.toInt(), 11, line)
        r.setPadding(dp(10), dp(8), dp(10), dp(8))
        r.addView(tv(label, 11.5f), lp(0, WC, 1f))
        val g = Glyph(ctx, Glyph.CHEV)
        g.color = Pal.GLOW
        r.addView(g, lp(dp(16), dp(16)))
        r.setOnClickListener { click() }
        return fx(r)
    }

    private fun pageSettings(): View {
        val c = card("Settings", Glyph.GEAR)
        if (narrow) c.addView(macroSettings(false), lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Show auto clickers (drag to place buttons)") { svc.closePanel(); svc.showClickers() }, lp(MC, WC).apply { topMargin = dp(6) })
        c.addView(actionRow("Set biome area (for biome watch)") { svc.closePanel(); svc.setBiomeRegion() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Test biome read (shows what it sees)") { svc.closePanel(); svc.testBiome() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Send test biome message") { Hook.biomeTest(ctx); svc.toastPublic("Sent a sample RAINY message") }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Set watch area (words like Mari, Jester, auras)") { svc.closePanel(); svc.setWatchRegion() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Set extra points P1-P6 (for timed tasks)") { svc.closePanel(); svc.setExtraPoints() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Test tap (shows where LuminFish taps)") { svc.closePanel(); svc.testTap() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Test detection") { svc.test() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Test buttons (Fish / Exit / X)") { svc.testButtons() }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Turn on screen capture (needed)") { svc.openApp(true) }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Reset all-time statistics") { Stats.reset(ctx) }, lp(MC, WC).apply { topMargin = dp(5) })
        c.addView(actionRow("Open app settings") { svc.openApp(false) }, lp(MC, WC).apply { topMargin = dp(5) })
        return c
    }

    private fun pageCredits(): View {
        val c = card("Credits", Glyph.BOOK)
        val top = lin(LinearLayout.HORIZONTAL)
        top.gravity = Gravity.CENTER_VERTICAL
        // creator's Roblox avatar, loaded from the internet (shows the logo while offline)
        val av = ImageView(ctx)
        av.scaleType = ImageView.ScaleType.CENTER_CROP
        av.background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(0xFF050818.toInt()); setStroke(dp(2), Pal.GLOW) }
        av.clipToOutline = true
        av.outlineProvider = ViewOutlineProvider.BACKGROUND
        val cached = Online.cached(ctx)
        if (cached != null) av.setImageBitmap(cached) else av.setImageResource(R.drawable.brry_icon)
        top.addView(av, lp(dp(46), dp(46)))
        ObjectAnimator.ofFloat(av, "scaleX", 1f, 1.06f).apply { duration = 1400; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start() }
        ObjectAnimator.ofFloat(av, "scaleY", 1f, 1.06f).apply { duration = 1400; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start() }
        Online.refresh(ctx) { bmp -> if (bmp != null) ui.post { av.setImageBitmap(bmp) } }
        val who = lin(LinearLayout.VERTICAL)
        who.addView(tv("LuminFish v2.3", 13f, Pal.ICE, true))
        who.addView(tv("Made for Sol’s RNG.", 11f, muted))
        top.addView(who, lp(WC, WC).apply { leftMargin = dp(10) })
        c.addView(top, lp(MC, WC).apply { topMargin = dp(8) })
        c.addView(tv("Not affiliated with Roblox. Macros can break game rules and get accounts banned.", 11f, muted), lp(MC, WC).apply { topMargin = dp(8) })
        val follow = tv("Follow: chromeplated12301 for more update", 12.5f, Pal.GLOW, true)
        follow.paintFlags = follow.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        follow.setPadding(0, dp(8), 0, dp(8))
        follow.setOnClickListener {
            svc.openUrl("https://www.roblox.com/share?code=9c18d3e4c933e640bdb855d8c451bef2&type=Profile&source=ProfileShare&stamp=1794479961423")
        }
        fx(follow)
        c.addView(follow, lp(WC, WC).apply { topMargin = dp(8) })
        return c
    }

    private fun row(c: LinearLayout, label: String, right: View, rightW: Int = 40) {
        val r = lin(LinearLayout.HORIZONTAL)
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(tv(label, 11.5f), lp(0, WC, 1f).apply { rightMargin = dp(4) })
        r.addView(right, lp(dp(rightW), dp(22)))
        c.addView(r, lp(MC, WC).apply { topMargin = dp(7) })
    }

    private fun tog(key: String) = Tog(ctx, Cfg.get(ctx, key) == 1) { Cfg.put(ctx, key, if (it) 1 else 0) }

    // every setting of the panel. The first seven match the design, all the old ones are still here.
    private fun fillSettings(a: LinearLayout) {
        row(a, "AI Autopilot", tog("ai"))
        row(a, "Auto Sell", tog("sell"))
        row(a, "OpenCV detect", tog("cv"))
        row(a, "Anti-AFK", tog("afk"))
        row(a, "Biome watch", tog("bio"))
        row(a, "Word watch", tog("watch"))
        row(a, "Auto rejoin", tog("rejoin"))
        row(a, "Precision taps", tog("tapeng"))
        row(a, "Timed tapping", tog("tapmode"))
        row(a, "Tap precision", cycle("tapprec", intArrayOf(0, 1, 2)) { if (it == 0) "Normal" else if (it == 1) "High" else "Max" }, 62)
        row(a, "Adaptive speed", tog("adaptfps"))
        row(a, "Animations", tog("anim"))
        row(a, "Find buttons on screen", tog("scene"))
        row(a, "Any zone colour", tog("anyzone"))
        row(a, "Show tap dots", tog("tapdots"))
        row(a, "One tap at zone edge", tog("edgetap"))
        row(a, "Save pictures when unsure", tog("savepics"))
        row(a, "Learn my buttons", tog("learnpics"))
        row(a, "Show auto clickers", Tog(ctx, svc.clickersShown()) { if (it) { svc.closePanel(); svc.showClickers() } else svc.hideClickers() })
        row(a, "Smart timing", tog("smart"))
        row(a, "Click recovery", tog("recover"))
        row(a, "Anti-freeze", tog("freeze"))
        row(a, "Verify catches", tog("verify"))
        row(a, "Sell trigger", cycle("sellmode", intArrayOf(0, 1, 2)) { if (it == 0) "Catches" else if (it == 1) "Time" else "Either" }, 74)
        row(a, "Sell every N catches", cycle("every", intArrayOf(5, 10, 20, 30, 50, 100)) { it.toString() }, 52)
        row(a, "Sell every N min", cycle("sellmin", intArrayOf(5, 10, 15, 30, 60)) { it.toString() }, 52)
        row(a, "Tap Confirm after Sell all", tog("sellconf"))
        row(a, "Screenshots", tog("shoton"))
        row(a, "Debug overlay", tog("debug"))
        tapVal = tv("", 11f, Pal.TEXT)
        tapVal.gravity = Gravity.CENTER
        tapVal.background = shape(0xFF0F1838.toInt(), 12, 0x668FB4FF)
        tapVal.setOnClickListener {
            val opts = intArrayOf(50, 70, 90, 120)
            val cur = Cfg.get(ctx, "tapms")
            var nxt = opts[0]
            for (k in opts.indices) if (opts[k] == cur && k + 1 < opts.size) nxt = opts[k + 1]
            Cfg.put(ctx, "tapms", nxt)
            refresh()
        }
        row(a, "Tap delay", tapVal, 56)
        capVal = tv("", 11f, Pal.TEXT)
        capVal.gravity = Gravity.CENTER
        capVal.background = shape(0xFF0F1838.toInt(), 12, 0x668FB4FF)
        capVal.setOnClickListener { if (!Cap.active) svc.openApp(true) }
        row(a, "Screen capture (needed)", capVal, 62)
    }

    private fun macroSettings(fixed: Boolean): View {
        val inner = lin(LinearLayout.VERTICAL)
        fillSettings(inner)
        if (!fixed) return inner
        val a = card("Macro Settings", Glyph.BOLT)
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        sv.isVerticalFadingEdgeEnabled = true
        sv.setFadingEdgeLength(dp(16))
        sv.addView(inner, ViewGroup.LayoutParams(MC, WC))
        a.addView(sv, lp(MC, 0, 1f))
        return a
    }

    private fun rightColumn(): View = macroSettings(true)

    private fun seg(kind: Int, label: String, value: TextView): View {
        val s = lin(LinearLayout.HORIZONTAL)
        s.gravity = Gravity.CENTER_VERTICAL
        val g = Glyph(ctx, kind)
        g.color = Pal.GLOW
        s.addView(g, lp(dp(18), dp(18)))
        val t = lin(LinearLayout.VERTICAL)
        t.addView(tv(label, 9.5f, muted))
        t.addView(value)
        s.addView(t, lp(WC, WC).apply { leftMargin = dp(7) })
        return s
    }

    private fun statsBar(): View {
        val bar = lin(LinearLayout.HORIZONTAL)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = shape(Pal.CARD, 14, line)
        bar.setPadding(dp(12), dp(3), dp(10), dp(3))
        vCatch = tv("0", 14f, Pal.ICE, true)
        vTime = tv("00:00:00", 14f, Pal.ICE, true)
        vState = tv("Idle", 12f, Pal.ICE, true)
        vState.maxLines = 1
        bar.addView(seg(Glyph.BARS, "Total Catches", vCatch), lp(0, WC, 1f))
        bar.addView(seg(Glyph.CLOCK, "Runtime", vTime), lp(0, WC, 1f))
        bar.addView(seg(Glyph.STAR, "Status", vState), lp(0, WC, 1f))
        return bar
    }

    private fun refresh() {
        val running = svc.isRunning()
        val paused = svc.isPaused()
        val col = if (paused) Pal.WARN else Pal.OK
        dot.background = shape(col, 6)
        bump(pill, if (paused) "Paused" else if (running) "Running" else "Ready")
        val tt = if (running) "Tap to Stop" else "Tap to Start"
        if (tapT.text.toString() != tt) {
            tapT.text = tt
            tapT.translationY = dp(10).toFloat()
            tapT.animate().translationY(0f).setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
        }
        if (SystemClock.uptimeMillis() >= noticeUntil) {
            if (noticeUntil != 0L) {
                noticeUntil = 0L
                tapS.setTextColor(muted)
            }
            val ts = if (running) Dbg.state else "Let LuminFish do the work…"
            if (tapS.text.toString() != ts) {
                tapS.text = ts
                tapS.alpha = 0f
                tapS.animate().alpha(1f).setDuration(220).start()
            }
        }
        play.paused = paused
        play.mode = if (running) 1 else 0
        play.invalidate()
        toggle.on = running
        // the checks only show while idle
        if (running) {
            if (checkBox.visibility != View.GONE) checkBox.visibility = View.GONE
        } else {
            if (checkBox.visibility != View.VISIBLE) checkBox.visibility = View.VISIBLE
            val items = svc.checklist()
            for (k in items.indices) {
                val ok = items[k].second
                val t = checkRows[k]
                val neutral = items[k].first.startsWith("~")
                val c2 = if (neutral) Pal.WARN else if (ok) Pal.OK else Pal.BAD
                fadeColor(t, c2)
                val st = if (neutral) 0 else if (ok) 1 else 2
                if (checkState[k] != st) {
                    checkState[k] = st
                    t.background = shape(alphaOf(c2, 0x22), 10, alphaOf(c2, 0x77))
                    val res = if (neutral) R.drawable.ic_dot else if (ok) R.drawable.ic_check else R.drawable.ic_cancel
                    val dr = ctx.getDrawable(res)?.mutate()
                    if (dr != null) {
                        dr.setTint(c2)
                        dr.setBounds(0, 0, dp(12), dp(12))
                        t.setCompoundDrawablesRelative(dr, null, null, null)
                        t.compoundDrawablePadding = dp(5)
                    }
                }
            }
        }
        pauseB.visibility = if (running) View.VISIBLE else View.GONE
        pauseB.text = if (paused) "Resume" else "Pause"
        if (!running && hint == null) {
            hint = ObjectAnimator.ofFloat(tapT, "alpha", 1f, 0.5f).apply {
                duration = 900
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        } else if (running && hint != null) {
            hint?.cancel()
            hint = null
            tapT.alpha = 1f
        }
        bump(vCatch, Dbg.catches.toString())
        val sec = if (Dbg.startMs > 0 && running) (android.os.SystemClock.uptimeMillis() - Dbg.startMs) / 1000 else 0L
        vTime.text = String.format("%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
        bump(vState, if (running) Dbg.state else "Idle")
        if (curPage == 1) {
            spark.set(Dbg.perMinute(20))
            val live = svc.activeSeconds()
            statsText.text = Stats.session(live) + "\n\n" + Stats.allTime(ctx, live) + "\n\nTECHNICAL\nIn zone   ${Dbg.inZone}%\nChecks/s  ${Dbg.fps}\nLatency   ${Dbg.latency} ms\nCapture   ${if (Cap.active) "live screen, ${Dbg.capFps} fps" else "OFF"}\nScan      ${Dbg.scanInfo}\nTapping   ${Dbg.tapInfo}\nBiome     ${Dbg.biome.ifEmpty { "-" }}\nDetect    ${Dbg.detector}\nTaps      ${Dbg.tapOk} ok, ${Dbg.tapCancel} cancelled\nAI aim    ${Dbg.aiBias}"
        }
        if (::capVal.isInitialized) capVal.text = if (Cap.active) "On" else "Off, tap"
        if (::tapVal.isInitialized) tapVal.text = "${Cfg.get(ctx, "tapms")} ms"
    }
}
