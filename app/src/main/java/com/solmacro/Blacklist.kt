package com.solmacro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// v2.3 blacklist, managed only by the owner.
//
// How it works (there is no server of ours, so the list lives in a file YOU host):
//  - blacklist_url (res/values/auth.xml) = a public file, for example a GitHub Gist raw link, with the format below.
//  - Every copy of the app downloads it when someone signs up / logs in, when the app starts, and before the macro starts.
//    The last download is kept, so a ban still works offline.
//  - An account (Discord or Roblox ID) that is in the list gets the animated "Access blocked" screen and can not start the macro.
//  - The owner (the reserved owner_username, signed in with the owner password) sees the Blacklist screen in the account menu: add / remove accounts, copy the
//    JSON, and (optional) publish it straight to the Gist with a GitHub token that is kept only on the owner's phone.
// Honest limit: the check runs inside the app. Someone who rebuilds / patches the APK can remove it. A server is the only
// real lock; this stops normal users and everyone who installs the APK you send.
object Blacklist {
    class Entry(val p: String, val id: String, val name: String, val reason: String)

    // the owner is the reserved username in auth.xml; it can only be signed in with the owner password (see Auth.kt)
    fun isOwner(c: Context, p: String, id: String): Boolean {
        val o = Auth.ownerName(c)
        return p == Auth.USER && o.isNotEmpty() && id.trim().lowercase() == o
    }

    // the signed-in account is the owner (the sign-in itself proves who it is)
    fun owner(c: Context): Boolean = Auth.signedIn(c) && isOwner(c, Auth.provider(c), Cfg.gets(c, "auth_id"))

    fun url(c: Context): String {
        if (owner(c)) { val u = Cfg.gets(c, "bl_url").trim(); if (u.isNotEmpty()) return u }
        return try { c.getString(R.string.blacklist_url).trim() } catch (_: Exception) { "" }
    }

    // ---- parsing and writing the JSON ----
    fun parse(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            val t = text.trim()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("banned") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val p = o.optString("p").lowercase()
                val id = o.optString("id").trim().lowercase()
                if (p == Auth.USER && id.isNotEmpty()) out.add(Entry(p, id, o.optString("name"), o.optString("reason")))
            }
        } catch (_: Exception) {}
        return out
    }

    fun toJson(list: List<Entry>): String {
        val a = JSONArray()
        for (e in list) a.put(JSONObject().put("p", e.p).put("id", e.id).put("name", e.name).put("reason", e.reason))
        return JSONObject().put("version", 1).put("banned", a).toString(2)
    }

    // ---- what is stored on this phone ----
    fun cached(c: Context): List<Entry> = parse(Cfg.gets(c, "bl_remote"))
    fun mine(c: Context): List<Entry> = parse(Cfg.gets(c, "bl_local"))     // owner's own working list
    fun saveMine(c: Context, list: List<Entry>) = Cfg.puts(c, "bl_local", toJson(list))

    // the entry that blocks this account, or null. The owner can never be blocked.
    fun check(c: Context, p: String, id: String): Entry? {
        if (id.isEmpty() || isOwner(c, p, id)) return null
        for (e in cached(c)) if (e.p == p && e.id == id) return e
        return null
    }

    fun blocked(c: Context): Entry? = if (Auth.signedIn(c)) check(c, Auth.provider(c), Cfg.gets(c, "auth_id")) else null

    // ---- network ----
    private fun get(u: String): String? {
        val cn = URL(u).openConnection() as HttpURLConnection
        return try {
            cn.connectTimeout = 6000
            cn.readTimeout = 8000
            cn.useCaches = false
            cn.setRequestProperty("Cache-Control", "no-cache")
            cn.setRequestProperty("User-Agent", "LuminFish/2.3")
            if (cn.responseCode in 200..299) cn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) { null } finally { cn.disconnect() }
    }

    // downloads the list now (call from a background thread); true when a fresh list was stored
    fun refreshBlocking(c: Context): Boolean {
        val u = url(c)
        if (u.isEmpty()) return false
        val t = get(u + (if (u.contains("?")) "&" else "?") + "t=" + System.currentTimeMillis() / 60000) ?: return false
        // a page that is not a list (an error page, an empty file) must not wipe the stored one
        val looksRight = t.trim().startsWith("{") || t.trim().startsWith("[")
        if (!looksRight) return false
        Cfg.puts(c, "bl_remote", toJson(parse(t)))
        Cfg.put(c, "bl_remote_t", (System.currentTimeMillis() / 1000).toInt())
        return true
    }

    fun refresh(c: Context, cb: (Boolean) -> Unit) {
        Thread {
            val ok = try { refreshBlocking(c) } catch (_: Exception) { false }
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(ok) }
        }.start()
    }

    // owner only: write the list to the Gist (GitHub token and Gist ID are typed on the owner's phone and stay there)
    fun publish(c: Context, list: List<Entry>, cb: (Boolean, String) -> Unit) {
        if (!owner(c)) { cb(false, "Only the owner can publish."); return }
        val gist = Cfg.gets(c, "bl_gist").trim()
        val token = Cfg.gets(c, "bl_token").trim()
        if (gist.isEmpty() || token.isEmpty()) { cb(false, "Enter the Gist ID and a GitHub token first."); return }
        Thread {
            var ok = false
            var msg: String
            try {
                val body = JSONObject().put("files", JSONObject().put("blacklist.json", JSONObject().put("content", toJson(list)))).toString()
                val cn = URL("https://api.github.com/gists/$gist").openConnection() as HttpURLConnection
                try {
                    // the plain Java HttpURLConnection refuses "PATCH"; Android's own accepts it, so try it and fall back to the override header
                    try { cn.requestMethod = "PATCH" } catch (_: java.net.ProtocolException) {
                        cn.requestMethod = "POST"
                        cn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    }
                    cn.connectTimeout = 8000
                    cn.readTimeout = 12000
                    cn.doOutput = true
                    cn.setRequestProperty("Authorization", "Bearer $token")
                    cn.setRequestProperty("Accept", "application/vnd.github+json")
                    cn.setRequestProperty("User-Agent", "LuminFish/2.3")
                    cn.setRequestProperty("Content-Type", "application/json")
                    cn.outputStream.use { it.write(body.toByteArray()) }
                    val code = cn.responseCode
                    ok = code in 200..299
                    msg = if (ok) "Published." else "GitHub answered HTTP $code"
                } finally { cn.disconnect() }
            } catch (e: Exception) { msg = "Could not publish (" + (e.message ?: "error") + ")" }
            val m = msg
            val o = ok
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(o, m) }
        }.start()
    }
}
