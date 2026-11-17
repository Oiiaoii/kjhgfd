package com.solmacro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// LuminFish colors (night sky blue, glowing ice-blue lines)
object Pal {
    val BG = 0xFF03050E.toInt()
    val PANEL_TOP = 0xFF060A1C.toInt()
    val PANEL_BOT = 0xFF02030A.toInt()
    val CARD = 0xB3080D24.toInt()
    val CARD_SOLID = 0xFF0A1028.toInt()
    val CARD2 = 0xFF111A3C.toInt()
    val LINE = 0x663F5FD0
    val LINE_SOLID = 0xFF2A3A82.toInt()
    val GLOW = 0xFF8FB4FF.toInt()
    val GLOW2 = 0xFFB6A8FF.toInt()
    val ICE = 0xFFDCE6FF.toInt()
    val TEXT = 0xFFE6ECFF.toInt()
    val MUTED = 0xFF8E9BD0.toInt()
    val OK = 0xFF38D39F.toInt()
    val WARN = 0xFFFFB84D.toInt()
    val BAD = 0xFFFF7A8A.toInt()
}

private fun withA(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

private fun fourStar(p: Path, cx: Float, cy: Float, r: Float, fat: Float = 0.10f) {
    p.reset()
    p.moveTo(cx, cy - r)
    p.quadTo(cx + r * fat, cy - r * fat, cx + r, cy)
    p.quadTo(cx + r * fat, cy + r * fat, cx, cy + r)
    p.quadTo(cx - r * fat, cy + r * fat, cx - r, cy)
    p.quadTo(cx - r * fat, cy - r * fat, cx, cy - r)
    p.close()
}

// Twinkling stars and slow drifting nebula glow. Cheap: about 25 frames a second.
class StarField(ctx: Context, private val count: Int = 48) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = java.util.Random(11)
    private val sx = FloatArray(count) { rnd.nextFloat() }
    private val sy = FloatArray(count) { rnd.nextFloat() }
    private val ss = FloatArray(count) { 0.6f + rnd.nextFloat() * 1.5f }
    private val sp = FloatArray(count) { 0.5f + rnd.nextFloat() * 1.6f }
    private val ph = FloatArray(count) { rnd.nextFloat() * 6.28f }
    private val dr = FloatArray(count) { 0.004f + rnd.nextFloat() * 0.012f }
    private val star = Path()
    private var blobs: Array<RadialGradient>? = null
    private var blobR = 0f
    private var up = false
    var strength = 1f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        blobR = maxOf(w, h) * 0.55f
        if (blobR <= 0f) return
        blobs = arrayOf(
            RadialGradient(0f, 0f, blobR, intArrayOf(0x402A44C8, 0x00000000), null, Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, blobR * 0.8f, intArrayOf(0x30604AC8, 0x00000000), null, Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, blobR * 0.7f, intArrayOf(0x2A2C7EDC, 0x00000000), null, Shader.TileMode.CLAMP)
        )
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); up = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { up = false; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = SystemClock.uptimeMillis() / 1000f
        val b = blobs
        if (b != null) {
            p.style = Paint.Style.FILL
            val pos = arrayOf(
                floatArrayOf(w * (0.25f + 0.1f * Math.sin(t * 0.21).toFloat()), h * (0.3f + 0.1f * Math.cos(t * 0.17).toFloat())),
                floatArrayOf(w * (0.8f + 0.08f * Math.cos(t * 0.19).toFloat()), h * (0.7f + 0.1f * Math.sin(t * 0.23).toFloat())),
                floatArrayOf(w * (0.55f + 0.12f * Math.sin(t * 0.13).toFloat()), h * (0.95f + 0.05f * Math.cos(t * 0.29).toFloat()))
            )
            for (i in b.indices) {
                p.shader = b[i]
                c.save()
                c.translate(pos[i][0], pos[i][1])
                p.alpha = (255 * strength).toInt()
                c.drawCircle(0f, 0f, blobR, p)
                c.restore()
            }
            p.shader = null
        }
        p.style = Paint.Style.FILL
        for (i in 0 until count) {
            val y = ((sy[i] - dr[i] * t) % 1f + 1f) % 1f
            val x = sx[i] + 0.012f * Math.sin(t * 0.6 + ph[i]).toFloat()
            val tw = 0.5f + 0.5f * Math.sin(t * sp[i] * 2.2 + ph[i]).toFloat()
            val a = (0.15f + 0.85f * tw) * strength
            val r = ss[i] * d
            p.color = withA(0xFFCFE0FF.toInt(), (255 * a).toInt())
            if (ss[i] > 1.6f) {
                fourStar(star, x * w, y * h, r * 2.6f, 0.08f)
                c.drawPath(star, p)
            } else {
                c.drawCircle(x * w, y * h, r * 0.7f, p)
            }
        }
        if (up) postInvalidateDelayed(40L)
    }
}

// The glowing ornate border: breathing glow, a light that runs around the edge, twinkling star ornaments.
class GlowFrame(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Path()
    private val seg = Path()
    private val wing = Path()
    private val star = Path()
    private val pm = PathMeasure()
    private var len = 0f
    private var up = false
    var radius = 22f
    var tint = Pal.GLOW

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val ins = 9f * d
        border.reset()
        border.addRoundRect(RectF(ins, ins, w - ins, h - ins), radius * d, radius * d, Path.Direction.CW)
        pm.setPath(border, false)
        len = pm.length
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); up = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { up = false; super.onDetachedFromWindow() }

    private fun starAt(c: Canvas, x: Float, y: Float, r: Float, tw: Float) {
        val k = 0.75f + 0.35f * tw
        fourStar(star, x, y, r * 1.9f * k, 0.12f)
        f.color = withA(tint, (70 * tw).toInt() + 25)
        c.drawPath(star, f)
        fourStar(star, x, y, r * k, 0.10f)
        f.color = withA(Pal.ICE, 200 + (55 * tw).toInt())
        c.drawPath(star, f)
    }

    private fun wings(c: Canvas, cx: Float, y: Float, dir: Float, a: Int) {
        wing.reset()
        wing.moveTo(cx + dir * 12f * d, y)
        wing.cubicTo(cx + dir * 34f * d, y - 7f * d, cx + dir * 60f * d, y + 4f * d, cx + dir * 92f * d, y + 15f * d)
        p.strokeWidth = 1.6f * d
        p.color = withA(Pal.ICE, a)
        c.drawPath(wing, p)
        wing.reset()
        wing.moveTo(cx + dir * 22f * d, y + 1f * d)
        wing.cubicTo(cx + dir * 40f * d, y + 8f * d, cx + dir * 58f * d, y + 12f * d, cx + dir * 72f * d, y + 26f * d)
        p.strokeWidth = 1.1f * d
        p.color = withA(tint, a * 3 / 4)
        c.drawPath(wing, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || len <= 0f) return
        val t = SystemClock.uptimeMillis() / 1000f
        val breath = 0.5f + 0.5f * Math.sin(t * 1.7).toFloat()
        // soft glow made of a few strokes, no blur filter (stays smooth on every phone)
        for (i in 3 downTo 0) {
            p.strokeWidth = (1.4f + i * 2.6f) * d
            p.color = withA(tint, ((0.20f - i * 0.04f) * (0.7f + 0.5f * breath) * 255).toInt())
            c.drawPath(border, p)
        }
        p.strokeWidth = 1.3f * d
        p.color = withA(Pal.ICE, 215)
        c.drawPath(border, p)
        // light running around the border
        val segLen = len * 0.14f
        val s = ((t * 0.16f) % 1f) * len
        for (pass in 0..1) {
            seg.reset()
            val ok = if (s + segLen <= len) pm.getSegment(s, s + segLen, seg, true)
            else if (pass == 0) pm.getSegment(s, len, seg, true) else pm.getSegment(0f, s + segLen - len, seg, true)
            if (ok) {
                p.strokeWidth = 5f * d
                p.color = withA(tint, 90)
                c.drawPath(seg, p)
                p.strokeWidth = 2.2f * d
                p.color = 0xFFFFFFFF.toInt()
                c.drawPath(seg, p)
            }
            if (s + segLen <= len) break
        }
        val ins = 9f * d
        val tw1 = 0.5f + 0.5f * Math.sin(t * 2.3).toFloat()
        val tw2 = 0.5f + 0.5f * Math.sin(t * 1.9 + 1.7).toFloat()
        val tw3 = 0.5f + 0.5f * Math.sin(t * 2.7 + 3.1).toFloat()
        wings(c, w / 2f, ins, -1f, 200)
        wings(c, w / 2f, ins, 1f, 200)
        starAt(c, w / 2f, ins, 15f * d, tw1)
        starAt(c, w / 2f, h - ins, 11f * d, tw2)
        starAt(c, ins, h * 0.5f, 9f * d, tw3)
        starAt(c, w - ins, h * 0.5f, 9f * d, tw1)
        starAt(c, ins + 4f * d, ins + 4f * d, 5f * d, tw2)
        starAt(c, w - ins - 4f * d, ins + 4f * d, 5f * d, tw3)
        starAt(c, ins + 4f * d, h - ins - 4f * d, 5f * d, tw1)
        starAt(c, w - ins - 4f * d, h - ins - 4f * d, 5f * d, tw2)
        if (up) postInvalidateDelayed(33L)
    }
}

// The animated halo behind the floating round icon: breathing glow, pulse rings, an orbiting spark.
class HaloView(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Path()
    private var up = false
    private var angle = 0f
    private var lastT = 0L
    private var spin = 0f
    private var col = Pal.GLOW
    var state = 0   // 0 idle, 1 running, 2 paused

    override fun onAttachedToWindow() { super.onAttachedToWindow(); up = true; lastT = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { up = false; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r0 = minOf(width, height) / 2f - 14f * d
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastT).coerceIn(0L, 100L)) / 1000f
        lastT = now
        val target = when (state) { 1 -> Pal.OK; 2 -> Pal.WARN; else -> Pal.GLOW }
        col = Color.rgb(
            Color.red(col) + ((Color.red(target) - Color.red(col)) * minOf(1f, dt * 5f)).toInt(),
            Color.green(col) + ((Color.green(target) - Color.green(col)) * minOf(1f, dt * 5f)).toInt(),
            Color.blue(col) + ((Color.blue(target) - Color.blue(col)) * minOf(1f, dt * 5f)).toInt())
        angle = (angle + (if (state == 1) 170f else 55f) * dt) % 360f
        spin = (spin + (if (state == 1) 260f else 0f) * dt) % 360f
        val t = now / 1000f
        val breath = 0.5f + 0.5f * Math.sin(t * 2.2).toFloat()
        p.style = Paint.Style.STROKE
        // glow
        for (i in 2 downTo 0) {
            p.strokeWidth = (2f + i * 3f) * d
            p.color = withA(col, ((0.30f - i * 0.08f) * (0.6f + 0.6f * breath) * 255).toInt())
            c.drawCircle(cx, cy, r0 + (1.5f + breath * 2f) * d, p)
        }
        // pulse ring every 2.4 s
        val k = (t % 2.4f) / 2.4f
        p.strokeWidth = 1.6f * d * (1f - k) + 0.5f
        p.color = withA(col, (160 * (1f - k)).toInt())
        c.drawCircle(cx, cy, r0 + 2f * d + k * 11f * d, p)
        // tilted orbit with a small planet
        c.save()
        c.rotate(-22f, cx, cy)
        val ra = r0 + 9f * d
        val rb = r0 * 0.46f
        p.strokeWidth = 1f * d
        p.color = withA(Pal.ICE, 70)
        c.drawOval(RectF(cx - ra, cy - rb, cx + ra, cy + rb), p)
        val a = Math.toRadians(angle.toDouble())
        val px = cx + (Math.cos(a) * ra).toFloat()
        val py = cy + (Math.sin(a) * rb).toFloat()
        p.style = Paint.Style.FILL
        p.color = withA(col, 110)
        c.drawCircle(px, py, 4.2f * d, p)
        p.color = Color.WHITE
        c.drawCircle(px, py, 2.1f * d, p)
        c.restore()
        // spinning arc while the macro runs
        if (state == 1) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.6f * d
            p.strokeCap = Paint.Cap.ROUND
            p.color = withA(col, 235)
            c.drawArc(RectF(cx - r0 - 3f * d, cy - r0 - 3f * d, cx + r0 + 3f * d, cy + r0 + 3f * d), spin, 70f, false, p)
            p.strokeCap = Paint.Cap.BUTT
        }
        // twinkles
        p.style = Paint.Style.FILL
        val a1 = 0.5f + 0.5f * Math.sin(t * 3.1).toFloat()
        val a2 = 0.5f + 0.5f * Math.sin(t * 2.4 + 2.0).toFloat()
        fourStar(star, cx - r0 * 1.12f, cy - r0 * 0.95f, (2.5f + 3f * a1) * d, 0.1f)
        p.color = withA(Pal.ICE, (90 + 165 * a1).toInt())
        c.drawPath(star, p)
        fourStar(star, cx + r0 * 1.1f, cy + r0 * 0.9f, (2f + 3f * a2) * d, 0.1f)
        p.color = withA(Pal.ICE, (90 + 165 * a2).toInt())
        c.drawPath(star, p)
        if (up) postInvalidateDelayed(33L)
    }
}

// Floating button = halo + round icon. Bobs gently up and down.
class FloatIcon(ctx: Context, val icon: ImageView, sizeDp: Int) : FrameLayout(ctx) {
    val halo = HaloView(ctx)
    private val d = ctx.resources.displayMetrics.density

    init {
        addView(halo, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(icon, LayoutParams((sizeDp * d).toInt(), (sizeDp * d).toInt(), android.view.Gravity.CENTER))
    }

    fun setState(s: Int) { if (halo.state != s) { halo.state = s } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        icon.animate().cancel()
        val a = android.animation.ObjectAnimator.ofFloat(icon, "translationY", -2f * d, 2f * d)
        a.duration = 1700
        a.repeatCount = android.animation.ObjectAnimator.INFINITE
        a.repeatMode = android.animation.ObjectAnimator.REVERSE
        a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        a.start()
        bob = a
    }

    private var bob: android.animation.ObjectAnimator? = null

    override fun onDetachedFromWindow() {
        bob?.cancel()
        bob = null
        super.onDetachedFromWindow()
    }
}

// Panel background chosen by the user: a photo or a GIF (animated on Android 9 and newer).
object PanelBg {
    fun file(c: Context) = File(c.filesDir, "panel_bg.bin")

    fun has(c: Context) = Cfg.get(c, "bg_custom") == 1 && file(c).exists()

    fun load(c: Context, maxPx: Int): Drawable? {
        if (!has(c)) return null
        val f = file(c)
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f)) { dec, info, _ ->
                    val big = maxOf(info.size.width, info.size.height)
                    if (big > maxPx) {
                        val k = maxPx.toFloat() / big
                        dec.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
                    }
                }
                if (d is AnimatedImageDrawable) {
                    d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    d.start()
                }
                return d
            }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o)
            val o2 = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(o.outWidth, o.outHeight) / maxPx) }
            val b = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
            return BitmapDrawable(c.resources, b)
        } catch (e: Throwable) {
            Dbg.add("Could not load the panel background: " + e.message)
            return null
        }
    }

    // copies the picked file into the app (max 40 MB) and checks it can be shown
    fun save(c: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(c.filesDir, "panel_bg.tmp")
            var total = 0L
            c.contentResolver.openInputStream(uri)?.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 40L * 1024 * 1024) throw Exception("file is bigger than 40 MB")
                        out.write(buf, 0, n)
                    }
                }
            } ?: throw Exception("cannot open the file")
            val f = file(c)
            if (f.exists()) f.delete()
            if (!tmp.renameTo(f)) throw Exception("cannot save the file")
            Cfg.put(c, "bg_custom", 1)
            if (load(c, 512) == null) { clear(c); false } else true
        } catch (e: Throwable) {
            Dbg.add("Panel background not saved: " + e.message)
            false
        }
    }

    fun clear(c: Context) {
        Cfg.put(c, "bg_custom", 0)
        try { file(c).delete() } catch (_: Exception) {}
    }
}

// The creator's Roblox avatar, loaded from the internet (Roblox web API) and cached, shown on the Credits page.
object Online {
    const val USER = "chromeplated12301"
    @Volatile private var busy = false
    @Volatile private var doneOnce = false

    private fun cacheFile(c: Context) = File(c.filesDir, "creator_avatar.png")

    fun cached(c: Context): Bitmap? {
        val f = cacheFile(c)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    private fun http(url: String, post: String?): String {
        val u = URL(url)
        val cn = u.openConnection() as HttpURLConnection
        cn.connectTimeout = 6000
        cn.readTimeout = 8000
        cn.setRequestProperty("Accept", "application/json")
        if (post != null) {
            cn.requestMethod = "POST"
            cn.doOutput = true
            cn.setRequestProperty("Content-Type", "application/json")
            cn.outputStream.use { it.write(post.toByteArray()) }
        }
        return cn.inputStream.bufferedReader().use { it.readText() }
    }

    // runs once per app start, calls back on a background thread with the new picture (or null when offline)
    fun refresh(c: Context, done: (Bitmap?) -> Unit) {
        if (busy || doneOnce) return
        busy = true
        Thread {
            var out: Bitmap? = null
            try {
                val j = org.json.JSONObject(http("https://users.roblox.com/v1/usernames/users",
                    "{\"usernames\":[\"" + USER + "\"],\"excludeBannedUsers\":true}"))
                val id = j.getJSONArray("data").getJSONObject(0).getLong("id")
                val t = org.json.JSONObject(http("https://thumbnails.roblox.com/v1/users/avatar-headshot?userIds=$id&size=150x150&format=Png&isCircular=false", null))
                val img = t.getJSONArray("data").getJSONObject(0).getString("imageUrl")
                val bytes = URL(img).openStream().use { it.readBytes() }
                out = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (out != null) {
                    cacheFile(c).outputStream().use { it.write(bytes) }
                    doneOnce = true
                }
            } catch (e: Throwable) {
                Dbg.add("Online avatar not loaded: " + e.message)
            }
            busy = false
            done(out)
        }.start()
    }
}
