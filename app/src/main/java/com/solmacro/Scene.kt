package com.solmacro

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

// v2.3 screen recognition of the fishing buttons, from the pictures in assets/scene:
//   fish.png            the "Fish" button  -> tap it
//   exit.png            the "Exit" button  -> fishing is running, do NOT tap there (it would leave the fishing)
//   x_white1/2.png      the WHITE strokes of the X in the corner of the "Fishing Failed" / "Fish Caught!" box -> tap it
// The X is matched on its white strokes only (the box is see-through, so the picture behind it changes all the time).
// Matching is done at many sizes and remembers the size that worked, so the next look is fast.
// Fish / Exit: the first strong match on YOUR phone is saved and used as an extra picture from then on.
object Scene {
    const val FISH = 1
    const val EXIT = 2
    const val XBTN = 4

    class Hit(val kind: Int, val x: Int, val y: Int, val score: Double)

    private var tFish: Mat? = null
    private var tExit: Mat? = null
    private var tFishL: Mat? = null     // learned on this phone
    private var tExitL: Mat? = null
    private var tX1: Mat? = null
    private var tX2: Mat? = null
    private var loaded = false
    private val memo = DoubleArray(8)
    @Volatile var info = ""
    @Volatile var lastBest = 0.0           // best score of the last look for each button, for the log
    private var lastPic = 0L

    private fun load(ctx: Context, name: String): Mat? = try {
        val b = ctx.assets.open("scene/$name").readBytes()
        val m = Imgcodecs.imdecode(MatOfByte(*b), Imgcodecs.IMREAD_GRAYSCALE)
        if (m.empty()) null else m
    } catch (e: Throwable) {
        Dbg.add("Scene picture $name not loaded: " + e.message)
        null
    }

    private fun loadLearned(ctx: Context, name: String): Mat? = try {
        val f = File(File(ctx.filesDir, "scene"), name)
        if (!f.exists()) null else Imgcodecs.imread(f.absolutePath, Imgcodecs.IMREAD_GRAYSCALE).let { if (it.empty()) null else it }
    } catch (e: Throwable) { null }

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        tFish = load(ctx, "fish.png")
        tExit = load(ctx, "exit.png")
        tX1 = load(ctx, "x_white1.png")
        tX2 = load(ctx, "x_white2.png")
        tFishL = loadLearned(ctx, "learn_fish.png")
        tExitL = loadLearned(ctx, "learn_exit.png")
        // sizes that worked last time: the first look after starting the app is then as fast as the rest
        for (i in memo.indices) memo[i] = Cfg.get(ctx, "scene_s$i") / 1000.0
        loaded = true
    }

    private class Best(val score: Double, val x: Int, val y: Int, val w: Int, val h: Int, val scale: Double)

    // best match of tpl in g. Tries the remembered scale first (also remembered between sessions), then a coarse sweep
    // (steps of 14%) and a fine search of +-8% in 2% steps around the best size: the coarse steps alone lost up to 20 points of
    // score on text (tests/scene_robust_test.py), the fine search brings the real match back above the limit.
    private fun best(g: Mat, tpl: Mat, slot: Int, accept: Double, from: Double = 0.05, to: Double = 1.2): Best? {
        fun at(s: Double): Best? {
            val tw = (tpl.cols() * s).toInt()
            val th = (tpl.rows() * s).toInt()
            if (th < 10 || tw < 10 || th >= g.rows() || tw >= g.cols()) return null
            val t = Mat()
            val r = Mat()
            try {
                Imgproc.resize(tpl, t, Size(tw.toDouble(), th.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.matchTemplate(g, t, r, Imgproc.TM_CCOEFF_NORMED)
                val mm = Core.minMaxLoc(r)
                return Best(mm.maxVal, mm.maxLoc.x.toInt(), mm.maxLoc.y.toInt(), tw, th, s)
            } finally {
                t.release()
                r.release()
            }
        }
        fun refine(b0: Best): Best {
            var top = b0
            var f = 0.92
            while (f <= 1.081) {
                val b = at(b0.scale * f)
                if (b != null && b.score > top.score) top = b
                f += 0.02
            }
            return top
        }
        if (memo[slot] > 0.0) {
            val b = at(memo[slot])
            if (b != null && b.score >= accept * 0.9) {
                val r = refine(b)
                if (r.score >= accept) { memo[slot] = r.scale; return r }
            }
        }
        var found: Best? = null
        var s = from
        while (s <= to) {
            val b = at(s)
            val cur = found
            if (b != null && (cur == null || b.score > cur.score)) found = b
            s *= 1.14
        }
        val f1 = found
        val fin: Best? = if (f1 != null) refine(f1) else null
        if (fin != null && fin.score >= accept) memo[slot] = fin.scale
        return fin
    }

    // white strokes of the top of the screen: the colour channels are first scaled to the same white level (a night filter
    // or tinted screen then reads like a normal one) and the brightness limit follows the picture's own white level (a dim
    // screen still shows the X). Slightly blurred like the templates. tests/xbtn_test.py + scene_robust_test.py.
    private fun whiteMask(bgra: Mat, rows: Int): Mat {
        val top = bgra.submat(0, rows, 0, bgra.cols())
        val chs = ArrayList<Mat>()
        val hist = Mat()
        val merged = Mat()
        val gray = Mat()
        val m = Mat()
        try {
            Core.split(top, chs)
            val tail = top.rows() * top.cols() * 0.003
            val hi = IntArray(3)
            for (i in 0..2) {
                Imgproc.calcHist(listOf(chs[i]), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
                var acc = 0.0
                var v = 255
                for (b in 255 downTo 0) {
                    acc += hist.get(b, 0)[0]
                    if (acc >= tail) { v = b; break }
                }
                hi[i] = maxOf(v, 60)
            }
            val ref = maxOf(hi[0], maxOf(hi[1], hi[2]))
            for (i in 0..2) {
                val gain = (ref.toDouble() / hi[i]).coerceIn(1.0, 3.0)
                if (gain > 1.01) chs[i].convertTo(chs[i], -1, gain, 0.0)
            }
            Core.merge(chs.subList(0, 3), merged)
            Imgproc.cvtColor(merged, gray, Imgproc.COLOR_BGR2GRAY)
            val thr = maxOf(120.0, 214.0 * minOf(1.0, ref / 255.0))
            Imgproc.threshold(gray, m, thr, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.GaussianBlur(m, m, Size(3.0, 3.0), 0.0)
            return m
        } finally {
            for (c in chs) c.release()
            hist.release()
            merged.release()
            gray.release()
            top.release()
        }
    }

    private fun learn(ctx: Context, name: String, g: Mat, b: Best) {
        try {
            if (Cfg.get(ctx, "learnpics") != 1 || b.score < 0.85) return
            val dir = File(ctx.filesDir, "scene")
            dir.mkdirs()
            val f = File(dir, name)
            if (f.exists()) return
            val crop = g.submat(b.y, b.y + b.h, b.x, b.x + b.w)
            Imgcodecs.imwrite(f.absolutePath, crop)
            crop.release()
            Dbg.add("Saved this phone's own picture of the button ($name)")
        } catch (_: Throwable) {}
    }

    // reg = the whole screen (any sampling step). want = FISH / EXIT / XBTN (can be combined).
    // Returns the best recognised thing with the centre in real screen pixels, or null.
    @Synchronized
    fun find(ctx: Context, reg: Reg, want: Int): Hit? {
        if (!Cv.ready) return null
        ensure(ctx)
        val n = reg.w * reg.h
        if (reg.px.size < n || reg.w < 32 || reg.h < 32) return null
        val bgra = Mat(reg.h, reg.w, CvType.CV_8UC4)
        val g = Mat()
        try {
            val arr = ByteArray(n * 4)
            ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(reg.px, 0, n)
            bgra.put(0, 0, arr)
            Imgproc.cvtColor(bgra, g, Imgproc.COLOR_BGRA2GRAY)
            var out: Hit? = null
            var seen = 0.0
            fun consider(kind: Int, b: Best) {
                val cx = reg.x + (b.x + b.w / 2) * reg.step
                val cy = reg.y + (b.y + b.h / 2) * reg.step
                if (out == null || b.score > out!!.score) out = Hit(kind, cx, cy, b.score)
            }
            if (want and XBTN != 0) {
                val ph = (g.rows() * 0.6).toInt()
                val wm = whiteMask(bgra, ph)
                var bx: Best? = null
                tX1?.let { t -> best(wm, t, 2, 0.80)?.let { if (bx == null || it.score > bx!!.score) bx = it } }
                tX2?.let { t -> best(wm, t, 3, 0.80)?.let { if (bx == null || it.score > bx!!.score) bx = it } }
                val xb = bx
                if (xb != null) {
                    seen = maxOf(seen, xb.score)
                    if (xb.score >= 0.80) consider(XBTN, xb)
                }
                wm.release()
            }
            if (want and FISH != 0) {
                var bf: Best? = null
                tFish?.let { t -> best(g, t, 0, 0.82)?.let { bf = it } }
                tFishL?.let { t -> best(g, t, 4, 0.82, 0.8, 1.25)?.let { if (bf == null || it.score > bf!!.score) bf = it } }
                val b = bf
                if (b != null) {
                    seen = maxOf(seen, b.score)
                    if (b.score >= 0.82) { consider(FISH, b); learn(ctx, "learn_fish.png", g, b) }
                }
            }
            if (want and EXIT != 0) {
                var be: Best? = null
                tExit?.let { t -> best(g, t, 1, 0.82)?.let { be = it } }
                tExitL?.let { t -> best(g, t, 5, 0.82, 0.8, 1.25)?.let { if (be == null || it.score > be!!.score) be = it } }
                val b = be
                if (b != null) {
                    seen = maxOf(seen, b.score)
                    if (b.score >= 0.82) { consider(EXIT, b); learn(ctx, "learn_exit.png", g, b) }
                }
            }
            lastBest = seen
            for (i in memo.indices) { val v = (memo[i] * 1000).toInt(); if (v != Cfg.get(ctx, "scene_s$i")) Cfg.put(ctx, "scene_s$i", v) }
            info = if (out == null) "nothing (best " + (seen * 100).toInt() + "%)" else (when (out!!.kind) { FISH -> "Fish"; EXIT -> "Exit"; else -> "X" }) + " " + (out!!.score * 100).toInt() + "%"
            return out
        } catch (e: Throwable) {
            Dbg.add("Scene error: " + e.message)
            return null
        } finally {
            bgra.release()
            g.release()
        }
    }

    // Saves the screen as a picture so a button that was not recognised can be looked at (and used to improve the
    // pictures). At most one every 25 s and 15 files. Folder: Android/data/com.solmacro/files/debug
    fun savePicture(ctx: Context, reg: Reg, tag: String, force: Boolean = false) {
        try {
            if (!force && Cfg.get(ctx, "savepics") != 1) return
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastPic < 25000L) return
            lastPic = now
            val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "debug")
            dir.mkdirs()
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size >= 15) files.take(files.size - 14).forEach { it.delete() }
            val bmp = Bitmap.createBitmap(reg.px, reg.w, reg.h, Bitmap.Config.ARGB_8888)
            val f = File(dir, tag + "_" + System.currentTimeMillis() / 1000 + ".png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
            Dbg.add("Saved a picture of the screen for checking: debug/" + f.name)
        } catch (e: Throwable) {
            Dbg.add("Could not save the debug picture: " + e.message)
        }
    }
}
