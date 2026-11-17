package com.solmacro

// LuminFish 2.1 precision tap engine ("tap ledger").
//
// Why: the v2.0 timed engine guessed how long a tap takes to show up in the game (35 ms) and counted a tap as
// "landed" once that time had passed. If the phone is slower than the guess, the lift is still missing from the
// picture, the marker looks too low and a second, unneeded tap goes out. That is where late and doubled taps came from.
//
// How it works now:
//   1. Every tap goes into a ledger and counts as IN FLIGHT until a picture of the screen shows its lift.
//   2. The marker position at the moment a new tap would land = last seen position - drain + (taps in flight x gain).
//   3. A tap goes out when that predicted position is below (zone centre - half a tap). One tap then lifts the marker
//      to the same distance above the centre, so the marker swings evenly around the middle of the zone.
//   4. The tap-to-game delay, the lift of one tap and the drain speed are MEASURED from the pictures while reeling.
//   5. When Android reports that a tap was cancelled or refused, it leaves the ledger at once (no phantom lift).
//   6. The zone is followed ahead of where it was seen, using its speed over the last 100-260 ms.
//
// The same logic runs in tests/reel_sim.py (sim6.py) against a simulated reeling bar.
class Tapper {
    private class T(val fire: Long, var st: Int)   // st: 0 in flight, 1 lift seen, 2 lost (cancelled / never seen)

    private val taps = ArrayList<T>()
    private val zh = ArrayList<DoubleArray>()      // [time, zone centre]

    @Volatile var gain = 58.0
        private set
    @Volatile var drain = 215.0
        private set
    @Volatile var delay = 35.0
        private set
    @Volatile var gN = 0
        private set
    @Volatile var dN = 0
        private set
    @Volatile var latN = 0
        private set

    private var hold = 35.0
    // v2.3: what is learned stays close to the start values (it used to drift: the lift estimate shrank over a reel,
    // taps looked weaker and weaker, and the macro tapped more and more)
    private var gLo = 0.0
    private var gHi = 1e9
    private var dLo = 0.0
    private var dHi = 1e9
    private var minGap = 70L
    private var pts = 0L
    private var pp = 0.0
    private var acc = 0.0
    private var zc = 0.0
    private var zw = 0.0
    private var zv = 0.0
    private var haveZone = false
    private var lastFire = 0L
    private var dtEma = 25.0
    private var quietT = 0L
    private var quietP = 0.0
    private var zoneAt = 0L

    @Synchronized
    fun reset(gain0: Double, drain0: Double, delay0: Double, holdMs: Long, gapMs: Long) {
        taps.clear()
        zh.clear()
        gain = gain0.coerceAtLeast(5.0)
        drain = drain0.coerceAtLeast(10.0)
        delay = delay0.coerceIn(5.0, 140.0)
        gLo = gain * 0.8
        gHi = gain * 1.25
        dLo = drain * 0.75
        dHi = drain * 1.3
        hold = holdMs.toDouble()
        minGap = maxOf(gapMs, holdMs + 20L)
        pts = 0L
        pp = 0.0
        acc = 0.0
        zv = 0.0
        haveZone = false
        lastFire = 0L
        dtEma = 25.0
        quietT = 0L
        zoneAt = 0L
        gN = 0
        dN = 0
        latN = 0
    }

    // taps that are on their way but not yet visible in a picture
    @Synchronized
    fun inFlight(): Int {
        var n = 0
        for (t in taps) if (t.st == 0) n++
        return n
    }

    // true when there is a recent picture with a real marker position to start from
    @Synchronized
    fun usable(now: Long): Boolean = pts != 0L && haveZone && now - pts <= 300L

    // A new picture of the bar. capT = when the picture was taken (uptime ms), tip = right end of the blue fill (px,
    // may have decimals), haveTip = false when the blue fill is hidden (then only the zone is updated).
    @Synchronized
    fun see(capT: Long, tip: Double, haveTip: Boolean, zoneCenter: Double, zoneWidth: Double) {
        zc = zoneCenter
        zw = zoneWidth
        haveZone = true
        zoneAt = capT
        // zone speed from the last 100-260 ms
        zh.add(doubleArrayOf(capT.toDouble(), zoneCenter))
        while (zh.size > 0 && capT - zh[0][0] > 400.0) zh.removeAt(0)
        var old: DoubleArray? = null
        for (z in zh) if (capT - z[0] in 100.0..260.0) { old = z; break }
        if (old != null) {
            val v = (zoneCenter - old[1]) / ((capT - old[0]) / 1000.0)
            zv = if (Math.abs(v) < 25.0) 0.0 else 0.6 * zv + 0.4 * v
        }
        if (!haveTip) return
        if (pts != 0L && capT > pts) {
            val dt = (capT - pts) / 1000.0
            if (dt < 0.3) {
                dtEma = 0.9 * dtEma + 0.1 * dt * 1000.0
                val r = tip - (pp - drain * dt)            // lift since the previous picture
                acc += r
                // oldest taps first: one tap is credited for every 0.6 x gain of lift
                val fl = ArrayList<T>()
                for (t in taps) if (t.st == 0) fl.add(t)
                while (fl.isNotEmpty() && acc >= 0.6 * gain && fl[0].fire < capT) {
                    val t = fl.removeAt(0)
                    t.st = 1
                    val lat = capT - t.fire - 0.5 * dtEma
                    if (lat > 5.0 && lat < 200.0) { delay = 0.95 * delay + 0.05 * lat; latN++ }
                    if (fl.isEmpty() && acc < 1.6 * gain) {
                        gain = (0.96 * gain + 0.04 * acc.coerceIn(0.5 * gain, 1.6 * gain)).coerceIn(gLo, gHi)
                        gN++
                    }
                    acc -= gain
                }
                acc = if (fl.isNotEmpty()) acc.coerceIn(-0.5 * gain, 2.5 * gain) else 0.0
                // a tap that never showed a lift stops counting after a generous time
                val lim = maxOf(220.0, 3.0 * delay)
                for (t in fl) if (capT - t.fire > lim) t.st = 2
                // drain: a stretch with no tap in flight shows how fast the marker falls by itself
                if (inFlightNoLock() == 0) {
                    if (quietT != 0L && quietT <= capT) {
                        val qd = (capT - quietT) / 1000.0
                        if (qd >= 0.08) {
                            val d = (quietP - tip) / qd
                            if (d > 0.5 * drain && d < 1.6 * drain) { drain = (0.96 * drain + 0.04 * d).coerceIn(dLo, dHi); dN++ }
                            quietT = capT
                            quietP = tip
                        }
                    } else {
                        quietT = capT
                        quietP = tip
                    }
                } else {
                    quietT = 0L
                }
            }
        }
        pts = capT
        pp = tip
        // forget old entries
        var i = 0
        while (i < taps.size) {
            val t = taps[i]
            if (t.st != 0 && t.fire < capT - 1500L) taps.removeAt(i) else i++
        }
    }

    private fun inFlightNoLock(): Int {
        var n = 0
        for (t in taps) if (t.st == 0) n++
        return n
    }

    // Android said the tap with this fire time was cancelled or refused: it will not lift the marker
    @Synchronized
    fun cancelled(fire: Long) {
        for (t in taps) if (t.fire == fire && t.st == 0) t.st = 2
    }

    // Should a tap go out now? Pure question, nothing is recorded. gapMs = the smallest time between two taps.
    @Synchronized
    fun wantTap(now: Long, gapMs: Long): Boolean {
        if (!usable(now)) return false
        if (now - lastFire < maxOf(gapMs, minGap)) return false
        val n = inFlightNoLock()
        val horizon = now + delay + hold / 2.0
        val pT = pp - drain * (horizon - pts) / 1000.0 + gain * n
        val zT = zc + (zv * (horizon - pts) / 1000.0).coerceIn(-zw, zw)
        return pT < zT - 0.5 * gain
    }

    // The tap really goes out now: enter it in the ledger.
    @Synchronized
    fun commit(now: Long) {
        lastFire = now
        taps.add(T(now, 0))
    }

    fun info(): String =
        "ledger: delay ${delay.toInt()} ms, lift ${gain.toInt()} px, drain ${drain.toInt()} px/s, in flight ${inFlight()}"
}
