package com.solmacro

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val MP = ViewGroup.LayoutParams.MATCH_PARENT
private const val WC = ViewGroup.LayoutParams.WRAP_CONTENT

// OWNER ONLY. Add / remove blacklisted Discord and Roblox accounts, copy the JSON, publish it to the Gist.
class BlacklistActivity : Activity() {
    private val cBg = 0xFF03050E.toInt()
    private val cCard = 0xE60A1028.toInt()
    private val cBd = 0xFF22306B.toInt()
    private val cTx = 0xFFE6ECFF.toInt()
    private val cMut = 0xFF8E9BD0.toInt()
    private val cBad = 0xFFFF6B6B.toInt()
    private val cOk = 0xFF38D39F.toInt()
    private val cGold = 0xFFFFC857.toInt()

    private var list = ArrayList<Blacklist.Entry>()
    private var prov = Auth.USER
    private lateinit var listBox: LinearLayout
    private lateinit var count: TextView
    private lateinit var empty: TextView
    private lateinit var status: TextView
    private lateinit var pubStatus: TextView
    private lateinit var fId: EditText
    private lateinit var fName: EditText
    private lateinit var fReason: EditText
    private lateinit var addCard: LinearLayout
    private lateinit var indicator: View
    private lateinit var segD: TextView
    private lateinit var segR: TextView
    private lateinit var seg: FrameLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, r: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(stroke), sc)
    }
    private fun tv(t: String, size: Float, color: Int = cTx, bold: Boolean = false, center: Boolean = false) = TextView(this).apply {
        text = t; setTextSize(TypedValue.COMPLEX_UNIT_DIP, size); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.12f)
    }
    private fun field(hint: String, value: String = "", number: Boolean = false, secret: Boolean = false) = EditText(this).apply {
        setHint(hint); setText(value); setHintTextColor(0xFF5E6A9C.toInt()); setTextColor(cTx)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f); setSingleLine(true)
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT
        }
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = shape(0xFF070C20.toInt(), 10, 1, cBd)
    }
    private fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }
    private fun button(t: String, fill: Int, stroke: Int, onClick: () -> Unit) = tv(t, 14.5f, Color.WHITE, true, true).apply {
        background = shape(fill, 12, 1, stroke); setPadding(dp(10), dp(12), dp(10), dp(12)); isClickable = true
        setOnClickListener { onClick() }; press(this)
    }

    override fun attachBaseContext(base: Context) {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        if (cfg.fontScale > 1.0f) cfg.fontScale = 1.0f
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    // owner only, also when the screen comes back (for example after the account was switched)
    override fun onResume() {
        super.onResume()
        if (!Blacklist.owner(this)) finish()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // owner only: anyone else is sent back at once
        if (!Blacklist.owner(this)) { finish(); return }
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        list = ArrayList(Blacklist.mine(this))
        if (list.isEmpty()) list = ArrayList(Blacklist.cached(this)).also { if (it.isNotEmpty()) Blacklist.saveMine(this, it) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(34), dp(18), dp(30)) }

        // header
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = tv("‹", 30f, cTx, false, true).apply { setPadding(dp(8), 0, dp(14), dp(4)); isClickable = true; setOnClickListener { finish() } }
        head.addView(back)
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ht.addView(tv("Blacklist", 24f, cTx, true))
        count = tv("", 12.5f, cMut)
        ht.addView(count)
        head.addView(ht, LinearLayout.LayoutParams(0, WC, 1f))
        val badge = tv("OWNER", 11f, 0xFF2A1B00.toInt(), true, true).apply { background = shape(cGold, 20); setPadding(dp(12), dp(5), dp(12), dp(5)) }
        head.addView(badge)
        root.addView(head)

        // add card
        addCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(cCard, 18, 1, cBd); setPadding(dp(16), dp(14), dp(16), dp(16)) }
        addCard.addView(tv("Block an account", 15f, cTx, true))
        seg = FrameLayout(this).apply { background = shape(0xFF070C20.toInt(), 12, 1, cBd) }
        indicator = View(this).apply { background = shape(0xFF2A3A82.toInt(), 9) }
        seg.addView(indicator, FrameLayout.LayoutParams(0, dp(34), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dp(3) })
        val segRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        segD = tv("Discord", 13.5f, cTx, true, true).apply { setOnClickListener { pick(Auth.USER) } }
        segR = tv("Roblox", 13.5f, cMut, true, true).apply { setOnClickListener { pick("device") } }
        segRow.addView(segD, LinearLayout.LayoutParams(0, dp(40), 1f))
        segRow.addView(segR, LinearLayout.LayoutParams(0, dp(40), 1f))
        seg.addView(segRow, FrameLayout.LayoutParams(MP, dp(40)))
        addCard.addView(seg, LinearLayout.LayoutParams(MP, dp(40)).apply { topMargin = dp(10) })
        seg.visibility = View.GONE
        fId = field("Username to block")
        fName = field("Name (optional, so you recognise it)")
        fReason = field("Reason shown to them (optional)")
        for (f in listOf(fId, fName, fReason)) addCard.addView(f, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        addCard.addView(button("Block account", 0xFFB3122A.toInt(), 0xFFFF3B4E.toInt()) { add() }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) })
        status = tv("", 12.5f, cMut, false, true).apply { visibility = View.GONE }
        addCard.addView(status, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) })
        root.addView(addCard, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(16) })

        // list
        val listCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(cCard, 18, 1, cBd); setPadding(dp(14), dp(14), dp(14), dp(14)) }
        listCard.addView(tv("Blocked accounts", 15f, cTx, true))
        empty = tv("Nobody is blocked.", 13f, cMut, false, true).apply { setPadding(0, dp(18), 0, dp(10)) }
        listCard.addView(empty)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listCard.addView(listBox, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(6) })
        root.addView(listCard, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14) })

        // publish
        val pub = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(cCard, 18, 1, cBd); setPadding(dp(16), dp(14), dp(16), dp(16)) }
        pub.addView(tv("Apply to everyone", 15f, cTx, true))
        pub.addView(tv("Other phones read the list from the file at blacklist_url. Copy the JSON into that file yourself, or publish it to your Gist from here.", 11.5f, cMut).apply { setPadding(0, dp(4), 0, dp(4)) })
        val fUrl = field("Blacklist file link (raw), what the app downloads", Cfg.gets(this, "bl_url"))
        val fGist = field("Gist ID", Cfg.gets(this, "bl_gist"))
        val fTok = field("GitHub token (stays on this phone)", Cfg.gets(this, "bl_token"), secret = true)
        for ((k, f) in listOf("bl_url" to fUrl, "bl_gist" to fGist, "bl_token" to fTok)) {
            f.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { Cfg.puts(this@BlacklistActivity, k, s.toString().trim()) }
            })
            pub.addView(f, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Copy JSON", 0xFF1A2556.toInt(), cBd) { copyJson() }, LinearLayout.LayoutParams(0, WC, 1f))
        row.addView(button("Publish", 0xFF2A3A82.toInt(), 0xFF5A74D6.toInt()) { publish() }, LinearLayout.LayoutParams(0, WC, 1f).apply { leftMargin = dp(10) })
        row.addView(button("Refresh", 0xFF1A2556.toInt(), cBd) { refreshRemote() }, LinearLayout.LayoutParams(0, WC, 1f).apply { leftMargin = dp(10) })
        pub.addView(row, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) })
        pubStatus = tv("", 12.5f, cMut, false, true).apply { visibility = View.GONE }
        pub.addView(pubStatus, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) })
        root.addView(pub, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14) })

        val frame = FrameLayout(this)
        frame.addView(AuroraView(this), FrameLayout.LayoutParams(MP, MP))
        frame.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(root) }, FrameLayout.LayoutParams(MP, MP))
        setContentView(frame)

        seg.post {
            val half = (seg.width - dp(6)) / 2
            indicator.layoutParams = (indicator.layoutParams as FrameLayout.LayoutParams).apply { width = half }
            // entrance
            Fx.rise(head, 0, 16, 380)
            Fx.pop(badge, 200, 0.5f, 420)
            Fx.rise(addCard, 120, 24, 440)
            Fx.rise(listCard, 220, 24, 440)
            Fx.rise(pub, 320, 24, 440)
            Fx.breathe(badge, 1.06f, 1500)
            rebuild(true)
        }
    }

    private fun pick(p: String) {
        if (p == prov) return
        prov = p
        val half = (seg.width - dp(6)) / 2
        val x = 0f
        if (Fx.on(this)) indicator.animate().translationX(x).setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start() else indicator.translationX = x
        segD.setTextColor(cTx)
        segR.setTextColor(cMut)
    }

    private fun msg(t: TextView, text: String, color: Int) {
        t.setTextColor(color)
        if (t.visibility != View.VISIBLE) { t.text = text; t.visibility = View.VISIBLE; Fx.rise(t, 0, 8, 240) } else Fx.swapText(t, text)
    }

    private fun updateCount() {
        val t = if (list.isEmpty()) "Nobody blocked yet" else list.size.toString() + (if (list.size == 1) " account blocked" else " accounts blocked")
        if (count.text.toString() != t) { count.text = t; Fx.bump(count) }
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun rowFor(e: Blacklist.Entry): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = shape(0xFF0E1638.toInt(), 14, 1, cBd); setPadding(dp(10), dp(10), dp(4), dp(10))
        }
        val tile = tv(e.id.take(1).uppercase(), 15f, Color.WHITE, true, true).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2A3A82.toInt()) }
        }
        r.addView(tile, LinearLayout.LayoutParams(dp(38), dp(38)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(e.name.ifEmpty { e.id }, 14.5f, cTx, true))
        col.addView(tv("@" + e.id + (if (e.reason.isNotEmpty()) "  ·  " + e.reason else ""), 11.5f, cMut))
        r.addView(col, LinearLayout.LayoutParams(0, WC, 1f).apply { leftMargin = dp(12) })
        val x = tv("✕", 16f, 0xFFFF8A96.toInt(), true, true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); isClickable = true
            setOnClickListener { remove(e, r) }
        }
        r.addView(x)
        return r
    }

    private fun rebuild(animate: Boolean) {
        listBox.removeAllViews()
        var i = 0
        for (e in list) {
            val r = rowFor(e)
            listBox.addView(r, LinearLayout.LayoutParams(MP, WC).apply { topMargin = if (i == 0) 0 else dp(8) })
            if (animate) Fx.rise(r, 60L * i, 12, 320)
            i++
        }
        updateCount()
    }

    private fun add() {
        val id = fId.text.toString().trim().lowercase().removePrefix("@")
        if (!Auth.validName(id)) { msg(status, "Enter the username (3 to 20 letters, numbers or _).", cBad); Fx.shake(addCard); return }
        if (Blacklist.isOwner(this, prov, id)) { msg(status, "That is your own account, it can not be blocked.", cBad); Fx.shake(addCard); return }
        if (list.any { it.p == prov && it.id == id }) { msg(status, "Already blocked.", cBad); Fx.shake(addCard); return }
        val e = Blacklist.Entry(prov, id, fName.text.toString().trim(), fReason.text.toString().trim())
        list.add(0, e)
        Blacklist.saveMine(this, list)
        val r = rowFor(e)
        listBox.addView(r, 0, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = if (list.size > 1) dp(8) else 0 })
        Fx.pop(r, 0, 0.7f, 420)
        updateCount()
        fId.setText(""); fName.setText(""); fReason.setText("")
        msg(status, "Blocked. Press Publish so everyone's app gets the new list.", cOk)
    }

    private fun remove(e: Blacklist.Entry, row: View) {
        list.removeAll { it.p == e.p && it.id == e.id }
        Blacklist.saveMine(this, list)
        updateCount()
        val finish = {
            val h = row.height
            if (Fx.on(this)) {
                ValueAnimator.ofInt(h, 0).apply {
                    duration = 200
                    addUpdateListener { row.layoutParams = row.layoutParams.apply { height = it.animatedValue as Int }; row.requestLayout() }
                    addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { listBox.removeView(row) } })
                    start()
                }
            } else listBox.removeView(row)
        }
        if (Fx.on(this)) row.animate().translationX(row.width.toFloat()).alpha(0f).setDuration(240).withEndAction { finish() }.start() else finish()
    }

    private fun json() = Blacklist.toJson(list)

    private fun copyJson() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("blacklist.json", json()))
        msg(pubStatus, "JSON copied. Paste it into your blacklist file.", cOk)
    }

    private fun publish() {
        msg(pubStatus, "Publishing…", cMut)
        Blacklist.publish(this, list) { ok, m ->
            msg(pubStatus, m, if (ok) cOk else cBad)
            if (!ok) Fx.shake(pubStatus)
        }
    }

    private fun refreshRemote() {
        msg(pubStatus, "Downloading the list…", cMut)
        Blacklist.refresh(this) { ok ->
            if (ok) {
                val n = Blacklist.cached(this).size
                msg(pubStatus, "Downloaded: $n account(s) in the published list.", cOk)
            } else msg(pubStatus, "Could not download. Check the link (it must be a public raw link).", cBad)
        }
    }
}
