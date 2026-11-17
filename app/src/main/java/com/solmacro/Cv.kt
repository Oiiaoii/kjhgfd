package com.solmacro

import android.os.SystemClock
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder

// OpenCV reeling-bar detector (v1.8). Finds the green zone and the blue fill in HSV, so it keeps working when the
// brightness changes, and keeps a short memory of where the bar was, so a fill that covers the whole zone does not
// make the bar "disappear". If OpenCV cannot load, or finds nothing, MacroService falls back to its own scanner.
object Cv {
    @Volatile var ready = false
    @Volatile var lastUsedMemory = false
    // v2.3: the zone can change colour, so any bright coloured area of the bar that is not the blue fill counts as zone
    @Volatile var anyColor = true

    // short memory of the last bar (in scan pixels)
    private var memX = 0
    private var memY = 0
    private var memW = 0
    private var memH = 0
    private var memZl = 0
    private var memZr = 0
    private var memArea = 0.0
    private var memStep = 0
    private var memAt = 0L
    // reused between scans
    private var cacheArr: ByteArray? = null
    private var cacheMat: Mat? = null
    private var cacheHsv: Mat? = null
    private var cacheW = 0
    private var cacheH = 0

    fun init() {
        if (ready) return
        var ok = false
        try {
            val k = Class.forName("org.opencv.android.OpenCVLoader")
            ok = k.getMethod("initLocal").invoke(null) as Boolean
        } catch (e: Throwable) {
            ok = false
        }
        if (!ok) {
            try {
                System.loadLibrary("opencv_java4")
                ok = true
            } catch (e: Throwable) {
                Dbg.add("OpenCV could not load: " + e.message)
            }
        }
        ready = ok
        Dbg.add(if (ok) "OpenCV loaded" else "OpenCV is not available, using the built-in scanner")
    }

    fun forget() {
        memAt = 0L
        memW = 0
        rAt = 0L
    }

    // OpenCV style HSV (hue 0..179) of an RGB color
    private fun hsvOf(c: Int): IntArray {
        val r = (c shr 16) and 255
        val g = (c shr 8) and 255
        val b = c and 255
        val mx = maxOf(r, maxOf(g, b))
        val mn = minOf(r, minOf(g, b))
        val d = mx - mn
        var hh = 0.0
        if (d > 0) {
            hh = if (mx == r) 60.0 * (((g - b).toDouble() / d + 6.0) % 6.0)
            else if (mx == g) 60.0 * ((b - r).toDouble() / d + 2.0)
            else 60.0 * ((r - g).toDouble() / d + 4.0)
        }
        val s = if (mx == 0) 0 else d * 255 / mx
        return intArrayOf((hh / 2.0).toInt().coerceIn(0, 179), s, mx)
    }

    private fun colorMask(hsv: Mat, c: Int, tol: Int): Mat {
        val z = hsvOf(c)
        val ht = 4 + tol / 3
        val lo = Scalar(maxOf(0, z[0] - ht).toDouble(), maxOf(45, (z[1] * 0.45).toInt()).toDouble(), maxOf(25, (z[2] * 0.5).toInt()).toDouble())
        val hi = Scalar(minOf(179, z[0] + ht).toDouble(), 255.0, minOf(255, (z[2] * 1.6).toInt() + 1).toDouble())
        val m = Mat()
        Core.inRange(hsv, lo, hi, m)
        return m
    }



    // the blue fill in any shade (light blue to violet), so it still reads when the picked fill colour does not match
    private fun blueAny(hsv: Mat, vf: Int = 100): Mat {
        val m = Mat()
        Core.inRange(hsv, Scalar(98.0, 55.0, vf.toDouble()), Scalar(135.0, 255.0, 255.0), m)
        return m
    }

    // v2.3: every bright, coloured pixel that is not the blue fill (hue 95..140) and not white text (low saturation).
    // Covers green, teal, yellow, orange, red, pink and purple zones, and a zone made of two shades.
    private fun anyMask(hsv: Mat, blue: Mat?, vf: Int = 100): Mat {
        val a = Mat()
        val b = Mat()
        Core.inRange(hsv, Scalar(0.0, 55.0, vf.toDouble()), Scalar(94.0, 255.0, 255.0), a)
        Core.inRange(hsv, Scalar(141.0, 55.0, vf.toDouble()), Scalar(179.0, 255.0, 255.0), b)
        Core.bitwise_or(a, b, a)
        b.release()
        if (blue != null) {
            val nb = Mat()
            Core.bitwise_not(blue, nb)
            Core.bitwise_and(a, nb, a)
            nb.release()
        }
        return a
    }

    // number of set pixels in every column of rows ya..yb (xa..xb), as a plain array
    private fun colCounts(mask: Mat, ya: Int, yb: Int, xa: Int, xb: Int): IntArray {
        val band = mask.submat(ya, yb + 1, xa, xb + 1)
        val sums = Mat()
        Core.reduce(band, sums, 0, Core.REDUCE_SUM, CvType.CV_32S)
        val arr = IntArray(sums.cols())
        sums.get(0, 0, arr)
        sums.release()
        band.release()
        for (i in arr.indices) arr[i] = arr[i] / 255
        return arr
    }




    // thin lines (the bar outline, text strokes) are removed first, then small gaps are closed
    private fun openClose(src: Mat, dst: Mat, h: Int) {
        val tmp = Mat()
        val ko = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, maxOf(5, h / 150).toDouble()))
        val kc = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 3.0))
        try {
            Imgproc.morphologyEx(src, tmp, Imgproc.MORPH_OPEN, ko)
            Imgproc.morphologyEx(tmp, dst, Imgproc.MORPH_CLOSE, kc)
        } finally {
            tmp.release()
            ko.release()
            kc.release()
        }
    }

    // v2.3: how far the bar reaches left and right of a zone candidate: columns where dark track, blue fill or zone colour fill
    // the row. A real bar is several times wider than its zone. Returns [left, right] or null.
    private fun barExtent(hsv: Mat, blue: Mat, any: Mat, y: Int, ch: Int, zx: Int, zw: Int, w: Int): IntArray? {
        val ya = y + ch / 4
        val yb = maxOf(ya, y + ch - 1 - ch / 4)
        val rows = yb - ya + 1
        val dark = Mat()
        try {
            Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(179.0, 255.0, 69.0), dark)
            val cd = colCounts(dark, ya, yb, 0, w - 1)
            val cb = colCounts(blue, ya, yb, 0, w - 1)
            val ca = colCounts(any, ya, yb, 0, w - 1)
            fun ok(i: Int) = maxOf(cd[i], maxOf(cb[i], ca[i])) * 2 >= rows
            val mid = zx + zw / 2
            var l = mid
            var gap = 0
            var i = mid
            while (i >= 0) { if (ok(i)) { l = i; gap = 0 } else if (++gap > 10) break; i-- }
            var r = mid
            gap = 0
            i = mid
            while (i < w) { if (ok(i)) { r = i; gap = 0 } else if (++gap > 10) break; i++ }
            return if (r - l >= 3.5 * zw) intArrayOf(l, r) else null
        } finally {
            dark.release()
        }
    }

    // true when, along the bar's width, almost every column is dark track, blue fill or zone colour
    private fun looksLikeBar(hsv: Mat, blue: Mat, any: Mat, y: Int, ch: Int, xl: Int, xr: Int, w: Int): Boolean {
        val xa = maxOf(0, xl)
        val xb = minOf(w - 1, xr)
        if (xb - xa < 16) return false
        val ya = y + ch / 4
        val yb = maxOf(ya, y + ch - 1 - ch / 4)
        val rows = yb - ya + 1
        val dark = Mat()
        try {
            Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(179.0, 255.0, 69.0), dark)
            val cd = colCounts(dark, ya, yb, xa, xb)
            val cb = colCounts(blue, ya, yb, xa, xb)
            val ca = colCounts(any, ya, yb, xa, xb)
            var ok = 0
            for (i in cd.indices) if (maxOf(cd[i], maxOf(cb[i], ca[i])) * 2 >= rows) ok++
            return ok >= 0.7 * cd.size
        } finally {
            dark.release()
        }
    }

    // end of the first run of "filled" columns that starts near the left end of the bar (-1 if none)
    private fun runEnd(cnt: IntArray, rows: Int, xa: Int, w: Int): Int {
        var tip = -1
        var run = 0
        var rs = -1
        for (k in 0..cnt.size) {
            val ok = k < cnt.size && cnt[k] * 2 >= rows
            if (ok) {
                if (run == 0) rs = k
                run++
            } else {
                if (run >= 3 && rs <= 0.03 * w && tip < 0) tip = xa + k
                run = 0
            }
        }
        return tip
    }

    // Returns [zonePx, zoneLeft, zoneRight, blueCols, tipX(-1 if none), 1, boxX1, boxY1, boxX2, boxY2, redSamples]
    // in screen pixels, or null when no bar is found (the caller then tries the built-in scanner).
    @Synchronized
    fun scanFull(px: IntArray, w: Int, h: Int, step: Int, color: Int, color2: Int, tol: Int): DoubleArray? {
        if (!ready || w < 16 || h < 16 || px.size < w * h) return null
        lastUsedMemory = false
        val n = w * h
        // fast path: the whole picture goes into one BGRA Mat with a single bulk copy (no per-pixel loop),
        // and the Mats are kept between scans so nothing is allocated 30 times a second
        val need = n * 4
        var arr = cacheArr
        if (arr == null || arr.size != need) {
            arr = ByteArray(need)
            cacheArr = arr
        }
        var rgb = cacheMat
        if (rgb == null || cacheW != w || cacheH != h) {
            rgb?.release()
            rgb = Mat(h, w, CvType.CV_8UC4)
            cacheMat = rgb
            cacheW = w
            cacheH = h
        }
        val hsv = cacheHsv ?: Mat().also { cacheHsv = it }
        var zm: Mat? = null
        var zc: Mat? = null
        var bm: Mat? = null
        val labels = Mat()
        val stats = Mat()
        val cents = Mat()
        try {
            ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(px, 0, n)
            rgb.put(0, 0, arr)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_BGR2HSV)
            zm = colorMask(hsv, color, tol)
            zc = Mat()
            val kern = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 3.0))
            Imgproc.morphologyEx(zm, zc, Imgproc.MORPH_CLOSE, kern)
            kern.release()
            val barL = w / 2.0 - 0.386 * h
            val barR = w / 2.0 + 0.386 * h
            var bx = 0
            var by = 0
            var bw = 0
            var bh = 0
            var barea = -1
            // finds the best bar-shaped blob in a mask; verify = also check that the row looks like the bar (blue fill,
            // dark track and zone fill nearly all of its width), used for the any-colour pass where the mask is noisy
            var relXa = -1
            var relXb = -1
            fun pick(mask: Mat, verify: Boolean, blue: Mat?, any: Mat?, relaxed: Boolean = false) {
                val cnt = Imgproc.connectedComponentsWithStats(mask, labels, stats, cents, 8, CvType.CV_32S)
                val st = IntArray(5)
                for (i in 1 until cnt) {
                    stats.get(i, 0, st)
                    val x = st[Imgproc.CC_STAT_LEFT]
                    val y = st[Imgproc.CC_STAT_TOP]
                    val cw = st[Imgproc.CC_STAT_WIDTH]
                    val ch = st[Imgproc.CC_STAT_HEIGHT]
                    val area = st[Imgproc.CC_STAT_AREA]
                    if (ch < 3 || cw < 2 * ch) continue
                    val cx = x + cw / 2.0
                    val cy = y + ch / 2.0
                    if (!relaxed) {
                        if (ch < 0.025 * h || ch > 0.06 * h) continue
                        if (cw < 0.02 * w || cw > w / 2) continue
                        if (area < cw * ch / 2) continue
                        if (cx < barL || cx > barR || cy < 0.4 * h || cy > 0.8 * h) continue
                    } else {
                        // v2.3 other layouts (portrait, tablets, a different UI size): the bar may be anywhere
                        if (ch < 0.012 * h || ch > 0.09 * h) continue
                        if (cw < 0.02 * w || cw > 0.6 * w) continue
                        if (area < cw * ch / 2) continue
                        if (cx < 0.1 * w || cx > 0.9 * w || cy < 0.1 * h || cy > 0.92 * h) continue
                    }
                    if (area <= barea) continue
                    var el = -1
                    var er = -1
                    if (verify) {
                        if (relaxed) {
                            val ext = barExtent(hsv, blue!!, any!!, y, ch, x, cw, w) ?: continue
                            el = ext[0]
                            er = ext[1]
                        } else if (!looksLikeBar(hsv, blue!!, any!!, y, ch, barL.toInt(), barR.toInt(), w)) continue
                    }
                    barea = area
                    bx = x
                    by = y
                    bw = cw
                    bh = ch
                    relXa = el
                    relXb = er
                }
            }
            pick(zc, false, null, null)
            bm = colorMask(hsv, color2, tol)
            if (anyColor) {
                val gb0 = blueAny(hsv)
                Core.bitwise_or(bm, gb0, bm)
                gb0.release()
            }
            val red = Mat()
            Core.inRange(rgb, Scalar(25.0, 24.0, 215.0, 0.0), Scalar(105.0, 104.0, 255.0, 255.0), red)
            val redN = Core.countNonZero(red).toDouble()
            red.release()
            val xa = if (relXa >= 0) maxOf(0, relXa - 1) else maxOf(0, barL.toInt() - 1)
            if (barea < 0 && anyColor) {
                val gb = blueAny(hsv, 100)
                Core.bitwise_or(bm, gb, bm)
                gb.release()
                val am = anyMask(hsv, bm, 100)
                val amc = Mat()
                try {
                    openClose(am, amc, h)
                    pick(amc, true, bm, am)
                } finally {
                    amc.release()
                    am.release()
                }
            }
            if (barea < 0 && anyColor) {
                // third try: the same colours, but any position and size (finds the bar on other layouts)
                val am2 = anyMask(hsv, bm, 100)
                val amc2 = Mat()
                try {
                    openClose(am2, amc2, h)
                    pick(amc2, true, bm, am2, true)
                } finally {
                    amc2.release()
                    am2.release()
                }
            }
            if (barea < 0) return fromMemory(hsv, bm, w, h, step, redN)
            val ya = by + bh / 4
            val yb = maxOf(ya, by + bh - 1 - bh / 4)
            val xb = minOf(w - 1, bx + bw - 1 + bw)
            if (xb <= xa) return null
            val counts = colCounts(bm, ya, yb, xa, xb)
            val tip = runEnd(counts, yb - ya + 1, xa, w)
            // remember where the bar is
            memX = xa
            memY = by
            memW = if (relXb >= 0) minOf(relXb - xa + 3, w - xa) else minOf((2 * 0.386 * h).toInt() + 2, w - xa)
            memH = bh
            memZl = bx
            memZr = bx + bw
            memArea = barea.toDouble() * step * step / 2.0
            memStep = step
            memAt = SystemClock.uptimeMillis()
            val out = DoubleArray(11)
            out[0] = memArea
            out[1] = (bx * step).toDouble()
            out[2] = ((bx + bw) * step).toDouble()
            var blue = 0
            if (tip >= 0) {
                var k = tip - xa - 1
                while (k >= 0 && counts[k] * 2 >= (yb - ya + 1)) { blue++; k-- }
            }
            out[3] = blue.toDouble()
            out[4] = if (tip >= 0) (tip * step).toDouble() else -1.0
            out[5] = 1.0
            out[6] = (xa * step).toDouble()
            out[7] = ((by - 2) * step).toDouble()
            out[8] = ((xb + 1) * step).toDouble()
            out[9] = ((by + bh + 3) * step).toDouble()
            out[10] = redN
            return out
        } catch (e: Throwable) {
            Dbg.add("OpenCV scan error: " + e.message)
            return null
        } finally {
            zm?.release()
            zc?.release()
            bm?.release()
            labels.release()
            stats.release()
            cents.release()
        }
    }

    // ---- v2.1: OpenCV scan of the small box around the bar (the scan that runs on almost every frame) ----
    private var roiMat: Mat? = null
    private var roiHsv: Mat? = null
    private var roiW = 0
    private var roiH = 0
    // the zone seen last in a box scan (screen pixels), used while the fill covers it
    private var rZl = 0
    private var rZr = 0
    private var rCells = 0
    private var rAt = 0L

    // a = RGBA bytes of the box (w x h) whose top-left corner is (x0, y0) on the screen. Same output layout as
    // scanFull. The tip (right end of the blue fill) has decimals: the two columns around the edge are counted
    // partly, so it moves smoothly instead of jumping a whole pixel.
    @Synchronized
    fun scanRoi(a: ByteArray, x0: Int, y0: Int, w: Int, h: Int, color: Int, color2: Int, tol: Int, screenW: Int): DoubleArray? {
        if (!ready || w < 4 || h < 3 || a.size < w * h * 4) return null
        var m = roiMat
        if (m == null || roiW != w || roiH != h) {
            m?.release()
            m = Mat(h, w, CvType.CV_8UC4)
            roiMat = m
            roiW = w
            roiH = h
        }
        val hsv = roiHsv ?: Mat().also { roiHsv = it }
        var zm: Mat? = null
        var bm: Mat? = null
        try {
            m.put(0, 0, a)
            Imgproc.cvtColor(m, hsv, Imgproc.COLOR_RGB2HSV)
            zm = colorMask(hsv, color, tol)
            bm = colorMask(hsv, color2, tol)
            if (anyColor) {
                // v2.3 (fixed): plain colours of the box. A colour-balance step tried earlier turned the left half of the blue
                // fill white in this tight box (no white pixels in it), the fill was then not found and the tapper tapped blindly.
                val gb = blueAny(hsv, 70)
                Core.bitwise_or(bm, gb, bm)
                gb.release()
                val am = anyMask(hsv, bm, 70)
                Core.bitwise_or(zm, am, zm)
                am.release()
            }
            val ya = h / 4
            val yb = maxOf(ya, h - 1 - h / 4)
            val rows = yb - ya + 1
            val zc = colCounts(zm, ya, yb, 0, w - 1)
            val bc = colCounts(bm, ya, yb, 0, w - 1)
            var cells = 0
            var zl = -1
            var zr = -1
            var zrun = 0
            var zStart = -1
            for (xx in 0..w) {
                val zn = if (xx < w) zc[xx] else 0
                if (xx < w && zn * 2 >= rows) {
                    if (zrun == 0) zStart = xx
                    zrun++
                    cells += zn
                } else {
                    if (zrun >= 2) {
                        if (zl < 0) zl = zStart
                        zr = xx
                    }
                    zrun = 0
                }
            }
            val out = doubleArrayOf(0.0, 0.0, 0.0, 0.0, -1.0, 1.0, x0.toDouble(), y0.toDouble(), (x0 + w).toDouble(), (y0 + h).toDouble(), 0.0)
            val nowMs = SystemClock.uptimeMillis()
            var fromMem = false
            if (zl >= 0) {
                rZl = x0 + zl
                rZr = x0 + zr
                rCells = cells
                rAt = nowMs
            } else if (rAt != 0L && nowMs - rAt <= 400L) {
                // the blue fill covers the whole zone: use the zone seen a moment ago (the fill must still start at the bar's left end)
                fromMem = true
            } else {
                return out
            }
            var tip = -1.0
            var bn = 0
            var brun = 0
            var bStart = -1
            for (xx in 0..w) {
                val ok = xx < w && bc[xx] * 2 >= rows
                if (ok) {
                    if (brun == 0) bStart = xx
                    brun++
                } else {
                    if (brun >= 3 && bStart <= 0.03 * screenW) {
                        val last = bc[xx - 1].toDouble() / rows
                        val next = if (xx < w) bc[xx].toDouble() / rows else 0.0
                        tip = ((xx - 1) + last + next).coerceIn(xx - 1.0, xx + 1.0)
                        bn = brun
                    }
                    brun = 0
                }
            }
            if (fromMem) {
                // only trust it when the fill's right end is inside the box, i.e. the bar really is still there
                if (tip < 0.0 || tip >= w - 3.0) return doubleArrayOf(0.0, 0.0, 0.0, 0.0, -1.0, 1.0, x0.toDouble(), y0.toDouble(), (x0 + w).toDouble(), (y0 + h).toDouble(), 0.0)
                out[0] = rCells.toDouble()
                out[1] = rZl.toDouble()
                out[2] = rZr.toDouble()
            } else {
                out[0] = cells.toDouble()
                out[1] = (x0 + zl).toDouble()
                out[2] = (x0 + zr).toDouble()
            }
            out[3] = bn.toDouble()
            out[4] = if (tip >= 0.0) x0 + tip else -1.0
            return out
        } catch (e: Throwable) {
            Dbg.add("OpenCV box scan error: " + e.message)
            return null
        } finally {
            zm?.release()
            bm?.release()
        }
    }

    // The zone is not visible (the fill covers it) but the bar was seen a moment ago: if the blue fill still starts at
    // the left end of the remembered bar and the rest of the bar is dark track, the bar is still there.
    private fun fromMemory(hsv: Mat, bm: Mat, w: Int, h: Int, step: Int, redN: Double): DoubleArray? {
        val now = SystemClock.uptimeMillis()
        if (memW <= 0 || memStep != step || now - memAt > 400L) return null
        val x0 = memX
        val bw = minOf(memW, w - x0)
        val ya = memY + memH / 4
        val yb = maxOf(ya, memY + memH - 1 - memH / 4)
        if (bw < 8 || yb >= h) return null
        val rows = yb - ya + 1
        val counts = colCounts(bm, ya, yb, x0, x0 + bw - 1)
        val tipRel = runEnd(counts, rows, 0, w)
        if (tipRel < 0) return null
        if (tipRel >= bw - 3) return null
        val track = Mat()
        Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(179.0, 79.0, 119.0), track)
        val tc = colCounts(track, ya, yb, x0, x0 + bw - 1)
        track.release()
        var tn = 0
        var all = 0
        for (k in tipRel until tc.size) {
            all++
            if (tc[k] * 2 >= rows) tn++
        }
        if (all >= 6 && tn * 2 < all) return null
        lastUsedMemory = true
        val out = DoubleArray(11)
        out[0] = memArea
        out[1] = (memZl * step).toDouble()
        out[2] = (memZr * step).toDouble()
        out[3] = tipRel.toDouble()
        out[4] = ((x0 + tipRel) * step).toDouble()
        out[5] = 1.0
        out[6] = (x0 * step).toDouble()
        out[7] = ((memY - 2) * step).toDouble()
        out[8] = ((x0 + bw) * step).toDouble()
        out[9] = ((memY + memH + 3) * step).toDouble()
        out[10] = redN
        return out
    }
}

// Result-panel detector: a coarse picture of the screen (64 x 36 brightness samples). Comparing the screen to the
// picture taken while the bar was showing tells when the caught / failed panel slid in and when it is gone again.
object Pn {
    const val GX = 64
    const val GY = 36

    fun sig(px: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(GX * GY)
        if (w < 2 || h < 2 || px.size < w * h) return out
        for (gy in 0 until GY) {
            val y = (((gy + 0.5) * h) / GY).toInt().coerceIn(0, h - 1)
            for (gx in 0 until GX) {
                val x = (((gx + 0.5) * w) / GX).toInt().coerceIn(0, w - 1)
                val p = px[y * w + x]
                out[gy * GX + gx] = (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10
            }
        }
        return out
    }

    // fraction of samples that changed at all (used by the anti-freeze check: a frozen screen changes nothing)
    fun motion(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        if (n <= 0) return 0.0
        var changed = 0
        for (i in 0 until n) if (Math.abs(a[i] - b[i]) > 6) changed++
        return changed.toDouble() / n
    }

    // fraction of samples that changed a lot; ex = box to ignore as fractions of the screen [x0, y0, x1, y1]
    fun diff(a: IntArray, b: IntArray, ex: FloatArray?): Double {
        val n = minOf(a.size, b.size)
        if (n < GX * GY) return 0.0
        var changed = 0
        var total = 0
        for (gy in 0 until GY) {
            val fy = (gy + 0.5f) / GY
            for (gx in 0 until GX) {
                val fx = (gx + 0.5f) / GX
                if (ex != null && fx >= ex[0] && fx <= ex[2] && fy >= ex[1] && fy <= ex[3]) continue
                total++
                val i = gy * GX + gx
                if (Math.abs(a[i] - b[i]) > 40) changed++
            }
        }
        return if (total == 0) 0.0 else changed.toDouble() / total
    }
}

// Adaptive frame processing (v2.0). Watches how long each scan takes compared with how fast the screen delivers
// frames and how often the bar is found, then picks the scan detail on its own:
//   - scans too slow for the frame rate  -> coarser sampling (every pixel step bigger), so no frame is ever late
//   - plenty of time left and the bar is being missed -> finer sampling for a more exact marker position
// It only changes by one step at a time and at most twice a second, so it never jumps around.
object Adapt {
    private var fullStep = 0
    private var roiStep = 0
    private var fullMs = 0.0
    private var roiMs = 0.0
    private var hit = 1.0
    private var lastChange = 0L
    private var screenW = 0

    @Synchronized
    fun reset(w: Int) {
        screenW = w
        fullStep = maxOf(2, w / 500)
        roiStep = if (w >= 1600) 2 else 1
        fullMs = 0.0
        roiMs = 0.0
        hit = 1.0
        lastChange = 0L
        info()
    }

    @Synchronized
    fun full(w: Int, on: Boolean): Int {
        if (!on) return maxOf(2, w / 500)
        if (fullStep == 0 || screenW != w) reset(w)
        return fullStep
    }

    @Synchronized
    fun roi(w: Int, on: Boolean): Int {
        if (!on) return if (w >= 1600) 2 else 1
        if (roiStep == 0 || screenW != w) reset(w)
        return roiStep
    }

    // ms = time the scan itself took, found = the bar was found, inRoi = it was the small-box scan
    @Synchronized
    fun report(ms: Long, found: Boolean, inRoi: Boolean, w: Int) {
        if (screenW != w || fullStep == 0) reset(w)
        if (inRoi) roiMs = if (roiMs == 0.0) ms.toDouble() else 0.8 * roiMs + 0.2 * ms
        else fullMs = if (fullMs == 0.0) ms.toDouble() else 0.8 * fullMs + 0.2 * ms
        hit = 0.9 * hit + 0.1 * (if (found) 1.0 else 0.0)
        val now = SystemClock.uptimeMillis()
        if (now - lastChange < 500L) return
        val fps = Dbg.capFps
        // time budget for one scan: a bit less than the gap between two frames (8 - 40 ms)
        val budget = (if (fps in 5..240) 1000.0 / fps else 33.0).coerceIn(8.0, 40.0) * 0.8
        if (inRoi) {
            if (roiMs > budget && roiStep < 3) { roiStep++; lastChange = now }
            else if (roiStep > 1 && roiMs < budget * 0.35 && hit < 0.85) { roiStep--; lastChange = now }
        } else {
            val maxStep = maxOf(4, w / 300)
            if (fullMs > budget && fullStep < maxStep) { fullStep++; lastChange = now }
            else if (fullStep > 2 && fullMs < budget * 0.4 && hit < 0.85) { fullStep--; lastChange = now }
        }
        info()
    }

    private fun info() {
        Dbg.scanInfo = "full 1/$fullStep ${fullMs.toInt()} ms, box 1/$roiStep ${roiMs.toInt()} ms"
    }
}
