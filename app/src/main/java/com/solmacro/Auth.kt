package com.solmacro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// v2.4 simple account: a username and a password, kept on this phone only (no Discord, no Roblox, nothing to set up).
// Passwords are never stored, only a salted PBKDF2 hash. The OWNER name (owner_username in auth.xml) is reserved: it can only be
// used with the owner password (its hash is owner_hash in auth.xml), so nobody else can be the owner or see the Blacklist screen.
object Auth {
    const val USER = "user"

    class Result(val ok: Boolean, val msg: String, val banned: Boolean = false)
    class Account(val p: String, val id: String, val name: String)

    fun label(p: String) = "Username"

    fun isDone(c: Context): Boolean = Cfg.gets(c, "auth_state") == "ok" && Cfg.gets(c, "auth_provider") == USER
    fun signedIn(c: Context): Boolean = isDone(c)
    fun name(c: Context): String = Cfg.gets(c, "auth_name")
    fun provider(c: Context): String = Cfg.gets(c, "auth_provider")
    fun avatarFile(c: Context) = File(c.filesDir, "avatar.png")

    fun ownerName(c: Context): String = try { c.getString(R.string.owner_username).trim().lowercase() } catch (_: Exception) { "" }
    private fun ownerHash(c: Context): String = try { c.getString(R.string.owner_hash).trim().lowercase() } catch (_: Exception) { "" }

    // ---- hashing ----
    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xff))
        return sb.toString()
    }

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun pbkdf(pw: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 60000, 256)
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }

    private fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun ownerOk(c: Context, pw: String): Boolean {
        val h = ownerHash(c)
        if (h.isEmpty()) return false
        return same(pbkdf(pw, "luminfish-owner-v1".toByteArray()), h)
    }

    // ---- accounts on this phone ----
    fun accounts(c: Context): List<Account> {
        val out = ArrayList<Account>()
        try {
            val a = JSONArray(Cfg.gets(c, "auth_accounts").ifEmpty { "[]" })
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.optString("p") == USER) out.add(Account(USER, o.getString("id"), o.optString("name")))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun saveAccounts(c: Context, list: List<Account>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("p", x.p).put("id", x.id).put("name", x.name))
        Cfg.puts(c, "auth_accounts", a.toString())
    }

    fun setCurrent(c: Context, acc: Account) {
        Cfg.puts(c, "auth_state", "ok")
        Cfg.puts(c, "auth_provider", USER)
        Cfg.puts(c, "auth_name", acc.name)
        Cfg.puts(c, "auth_id", acc.id)
        avatarFile(c).delete()
        val list = accounts(c).filterNot { it.id == acc.id }.toMutableList()
        list.add(0, acc)
        saveAccounts(c, list)
    }

    fun removeAccount(c: Context, acc: Account) {
        saveAccounts(c, accounts(c).filterNot { it.id == acc.id })
        Cfg.puts(c, "auth_pw_" + acc.id, "")
        if (Cfg.gets(c, "auth_id") == acc.id) signOut(c)
    }

    fun current(c: Context): Account? = if (signedIn(c)) Account(USER, Cfg.gets(c, "auth_id"), name(c)) else null

    fun signOut(c: Context) {
        for (k in listOf("auth_state", "auth_provider", "auth_name", "auth_id")) Cfg.puts(c, k, "")
        avatarFile(c).delete()
    }

    fun validName(n: String): Boolean = n.length in 3..20 && n.all { it.isLetterOrDigit() || it == '_' }

    private fun banOf(c: Context, name: String, key: String): Boolean {
        val b = Blacklist.check(c, USER, key) ?: return false
        Cfg.puts(c, "ban_name", name)
        Cfg.puts(c, "ban_reason", b.reason)
        Cfg.puts(c, "ban_p", USER)
        Cfg.puts(c, "ban_id", key)
        return true
    }

    // call from a background thread (the password hash takes a moment)
    fun signUp(c: Context, name0: String, pw: String): Result {
        val name = name0.trim()
        val key = name.lowercase()
        if (!validName(name)) return Result(false, "Username: 3 to 20 letters, numbers or _")
        if (pw.length < 6) return Result(false, "The password needs at least 6 characters")
        if (banOf(c, name, key)) return Result(false, "", true)
        val own = ownerName(c)
        val isOwnerName = own.isNotEmpty() && key == own
        if (isOwnerName && !ownerOk(c, pw)) return Result(false, "That username is reserved")
        if (accounts(c).any { it.id == key }) return Result(false, "That username already exists on this phone. Use Log in.")
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        Cfg.puts(c, "auth_pw_$key", hex(salt) + ":" + pbkdf(pw, salt))
        setCurrent(c, Account(USER, key, name))
        return Result(true, "new:$name")
    }

    fun logIn(c: Context, name0: String, pw: String): Result {
        val name = name0.trim()
        val key = name.lowercase()
        if (key.isEmpty()) return Result(false, "Enter your username")
        if (banOf(c, name, key)) return Result(false, "", true)
        val own = ownerName(c)
        if (own.isNotEmpty() && key == own) {
            // the owner is checked against the owner password, on any phone
            if (!ownerOk(c, pw)) return Result(false, "Wrong username or password")
            setCurrent(c, Account(USER, key, name))
            return Result(true, "back:$name")
        }
        val rec = Cfg.gets(c, "auth_pw_$key")
        if (rec.isEmpty() || !rec.contains(":")) return Result(false, "No account with that username on this phone. Use Sign up.")
        val salt = unhex(rec.substringBefore(":"))
        if (!same(pbkdf(pw, salt), rec.substringAfter(":"))) return Result(false, "Wrong username or password")
        val shown = accounts(c).firstOrNull { it.id == key }?.name ?: name
        setCurrent(c, Account(USER, key, shown))
        return Result(true, "back:$shown")
    }

    // round picture with the first letter
    fun initialBitmap(c: Context, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2A3A82.toInt()
        cv.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = sizePx * 0.46f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        val ch = name(c).trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        cv.drawText(ch, sizePx / 2f, sizePx / 2f - (p.descent() + p.ascent()) / 2f, p)
        return bmp
    }
}
