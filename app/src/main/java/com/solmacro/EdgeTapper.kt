package com.solmacro

// v2.3 "script-like" tapping. Rule (what the macro scripts do):
//   the blue fill falls to the LEFT EDGE of the green zone  -> exactly ONE tap
//   it falls back to the edge again                          -> one more tap
//   the fill is BELOW the zone (zone moved right / up)       -> quick taps until the fill is in the zone
//   the fill is ABOVE the zone (zone moved left / down)      -> no taps at all until it has drained back into the zone
// It does not learn the lift or the drain while reeling (that learning drifted in 2.2 and ended in tap spam).
// The tap that was just sent is counted as "on its way" until its lift has had time to show, and a new tap needs a
// pause after the last one, so it can not run away. Python mirror with results: tests/edge_sim.py.
class EdgeTapper {
    private val taps = ArrayList<Long>()
    private val zh = ArrayList<DoubleArray>()      // [time, zone centre]
    private var g = 58.0
    private var d = 215.0
    private var lat = 50.0
    private var minGap = 80L
    private var ts = 0L
    private var tip = 0.0
    private var zc = 0.0
    private var zw = 0.0
    private var zv = 0.0
    private var last = 0L
    private var haveZone = false
    private val ramp = 60.0                        // a tap's lift takes about this long to show completely

    @Volatile var edgeTaps = 0
        private set
    @Volatile var rushTaps = 0
        private set

    @Synchronized
    fun reset(gain: Double, drain: Double, latencyMs: Double, holdMs: Long, gapMs: Long) {
        taps.clear()
        zh.clear()
        g = gain.coerceAtLeast(10.0)
        d = drain.coerceAtLeast(20.0)
        lat = latencyMs.coerceIn(20.0, 140.0)
        minGap = maxOf(gapMs, holdMs + 20L, 80L)
        ts = 0L
        tip = 0.0
        zv = 0.0
        last = 0L
        haveZone = false
        edgeTaps = 0
        rushTaps = 0
    }

    // A new picture of the bar (capT = when it was taken, uptime ms). tip = right end of the blue fill.
    @Synchronized
    fun see(capT: Long, tipPx: Double, haveTip: Boolean, zoneCenter: Double, zoneWidth: Double) {
        zc = zoneCenter
        zw = zoneWidth
        haveZone = true
        zh.add(doubleArrayOf(capT.toDouble(), zoneCenter))
        while (zh.size > 0 && capT - zh[0][0] > 400.0) zh.removeAt(0)
        var old: DoubleArray? = null
        for (z in zh) if (capT - z[0] in 100.0..260.0) { old = z; break }
        if (old != null) {
            val v = (zoneCenter - old[1]) / ((capT - old[0]) / 1000.0)
            zv = if (Math.abs(v) < 25.0) 0.0 else 0.6 * zv + 0.4 * v
        }
        if (haveTip) {
            ts = capT
            tip = tipPx
        }
        var i = 0
        while (i < taps.size) { if (taps[i] < capT - 1500L) taps.removeAt(i) else i++ }
    }

    // tap delay as measured by the ledger (Tapper.delay) once it has enough samples
    @Synchronized
    fun setLatency(ms: Double) {
        lat = ms.coerceIn(20.0, 140.0)
    }

    @Synchronized
    fun usable(now: Long): Boolean = haveZone && ts != 0L && now - ts <= 300L

    @Synchronized
    fun cancelled(fire: Long) {
        taps.remove(fire)
    }

    @Synchronized
    fun commit(now: Long, rush: Boolean) {
        last = now
        taps.add(now)
        if (rush) rushTaps++ else edgeTaps++
    }

    // true when a tap should go out right now
    @Synchronized
    fun want(now: Long): Boolean = decide(now) != 0

    // 0 = no tap, 1 = the edge tap, 2 = a quick catch-up tap
    @Synchronized
    fun decide(now: Long): Int {
        if (!usable(now)) return 0
        val t = now + lat + ramp / 2.0
        var unseen = 0.0
        for (f in taps) {
            val vis = ((ts - f - lat) / ramp).coerceIn(0.0, 1.0)
            unseen += 1.0 - vis
        }
        val p = tip - d * (t - ts) / 1000.0 + g * unseen
        val zl = zc - zw / 2.0 + (zv * (t - ts) / 1000.0).coerceIn(-zw, zw)
        val e = minOf(0.12 * zw, 0.5 * maxOf(0.0, zw - g))
        // below the zone: tap fast until the predicted fill (taps already on their way included) is inside the zone
        val rush = p < zl
        val refr = if (rush) minGap else maxOf(minGap.toDouble(), 0.5 * g / d * 1000.0).toLong()
        if (p <= zl + e && now - last >= refr) return if (rush) 2 else 1
        // watchdog: clearly below the zone and 260 ms without a tap (a tap that never lifted) -> tap again
        val raw = tip - d * (t - ts) / 1000.0
        if (raw < zl - 0.5 * g && now - last >= 260L) return 2
        return 0
    }

    fun info(): String = "edge taps $edgeTaps, catch-up taps $rushTaps"
}
