package com.solmacro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

// v2.3: small circles that show where LuminFish really taps. One see-through window that ignores touches; every tap
// adds a point that fades in under a second. Cheap enough for the 12+ taps per second of the reel.
class MarksView(ctx: Context) : View(ctx) {
    private val xs = IntArray(10)
    private val ys = IntArray(10)
    private val ts = LongArray(10)
    private var n = 0
    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val loc = IntArray(2)

    fun add(x: Int, y: Int) {
        synchronized(this) {
            val i = n % xs.size
            xs[i] = x
            ys[i] = y
            ts[i] = SystemClock.uptimeMillis()
            n++
        }
        postInvalidate()
    }

    override fun onDraw(c: Canvas) {
        getLocationOnScreen(loc)
        val now = SystemClock.uptimeMillis()
        var live = false
        synchronized(this) {
            for (i in xs.indices) {
                val age = now - ts[i]
                if (ts[i] == 0L || age > 800L) continue
                live = true
                val k = 1f - age / 800f
                val px = (xs[i] - loc[0]).toFloat()
                val py = (ys[i] - loc[1]).toFloat()
                fill.color = ((90 * k).toInt() shl 24) or 0xFF8A00
                ring.color = ((255 * k).toInt() shl 24) or 0xFFFFFF
                val r = (11f + 9f * (1f - k)) * d      // the circle spreads out while it fades
                c.drawCircle(px, py, r, fill)
                c.drawCircle(px, py, r, ring)
                fill.color = ((255 * k).toInt() shl 24) or 0xFFFFFF
                c.drawCircle(px, py, 2.2f * d, fill)
            }
        }
        if (live) postInvalidateOnAnimation()
    }
}
