package com.solmacro

import android.content.Context

// Fishing statistics (v1.9): this session (Dbg) and all time (saved in the app settings).
object Stats {
    fun addCatch(c: Context) = Cfg.put(c, "st_catch", Cfg.get(c, "st_catch") + 1)
    fun addFail(c: Context) = Cfg.put(c, "st_fail", Cfg.get(c, "st_fail") + 1)
    fun addUnverified(c: Context) = Cfg.put(c, "st_unver", Cfg.get(c, "st_unver") + 1)
    fun addSell(c: Context) = Cfg.put(c, "st_sell", Cfg.get(c, "st_sell") + 1)
    fun addSeconds(c: Context, s: Long) {
        if (s > 0L) Cfg.put(c, "st_sec", (Cfg.get(c, "st_sec").toLong() + s).coerceAtMost(2000000000L).toInt())
    }

    fun reset(c: Context) {
        for (k in listOf("st_catch", "st_fail", "st_unver", "st_sec", "st_sell")) Cfg.put(c, k, 0)
    }

    fun hms(sec: Long): String =
        String.format(java.util.Locale.US, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)

    private fun pct(a: Int, b: Int): String = if (b <= 0) "-" else (a * 100 / b).toString() + "%"

    // activeSec = fishing time of this session (paused time is not counted)
    fun session(activeSec: Long): String {
        val tries = Dbg.catches + Dbg.fails + Dbg.unverified
        val perHour = if (activeSec > 60L) Dbg.catches * 3600L / activeSec else 0L
        val avg = if (Dbg.catches > 0 && activeSec > 0L) (activeSec / Dbg.catches).toString() + " s" else "-"
        val bite = if (Dbg.biteMax > 0) "${Dbg.biteMin} / ${Dbg.biteMs} / ${Dbg.biteMax} ms" else "-"
        return "THIS SESSION\n" +
            "Successful catches  ${Dbg.catches}\n" +
            "Failed attempts     ${Dbg.fails}\n" +
            "Not verified        ${Dbg.unverified}\n" +
            "Success rate        ${pct(Dbg.catches, tries)}\n" +
            "Fishing time        ${hms(activeSec)}\n" +
            "Catches per hour    $perHour\n" +
            "Time per catch      $avg\n" +
            "Bite min/avg/max    $bite\n" +
            "Sells               ${Dbg.sells}\n" +
            "Click recoveries    ${Dbg.clickFix}\n" +
            "Freeze recoveries   ${Dbg.freezes}"
    }

    // liveSec = the running session, which is not saved yet
    fun allTime(c: Context, liveSec: Long): String {
        val ok = Cfg.get(c, "st_catch") + Dbg.catches
        val bad = Cfg.get(c, "st_fail") + Dbg.fails
        val un = Cfg.get(c, "st_unver") + Dbg.unverified
        val sec = Cfg.get(c, "st_sec").toLong() + liveSec
        return "ALL TIME\n" +
            "Successful catches  $ok\n" +
            "Failed attempts     $bad\n" +
            "Not verified        $un\n" +
            "Success rate        ${pct(ok, ok + bad + un)}\n" +
            "Total fishing time  ${hms(sec)}\n" +
            "Sells               ${Cfg.get(c, "st_sell") + Dbg.sells}"
    }
}
