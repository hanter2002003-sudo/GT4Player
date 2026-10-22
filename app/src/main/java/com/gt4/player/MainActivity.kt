package com.gt4.player

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gt4.player.engine.NativeEngine
import java.io.File

class MainActivity : Activity() {

    companion object {
        private const val REQ_ISO = 1
        private const val REQ_BIOS = 2
        private const val PREFS = "gt4"
        private const val K_ISO_URI = "iso_uri"
        private const val K_ISO_LABEL = "iso_label"

        private val BG = Color.rgb(14, 17, 24)
        private val CARD = Color.rgb(26, 31, 43)
        private val ACCENT = Color.rgb(46, 134, 255)
        private val OK = Color.rgb(46, 204, 113)
        private val WARN = Color.rgb(241, 196, 15)
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var biosStatus: TextView
    private lateinit var isoStatus: TextView
    private lateinit var engineStatus: TextView
    private lateinit var playBtn: Button

    private val biosFile get() = File(filesDir, "bios/ps2-bios.bin")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(24))
        }

        root.addView(text("GT4 Player", 32f, Color.WHITE, bold = true))
        root.addView(text("تطبيق مخصص للعبة Gran Turismo 4 فقط", 14f, Color.rgb(150, 160, 180)).apply {
            setPadding(0, dp(4), 0, dp(20))
        })

        // الخطوة 1: BIOS
        biosStatus = text("", 13f, Color.LTGRAY)
        root.addView(card("١. ملف BIOS للـ PS2", biosStatus, "اختيار ملف BIOS") { pick(REQ_BIOS) })

        // الخطوة 2: ISO
        isoStatus = text("", 13f, Color.LTGRAY)
        root.addView(card("٢. ملف لعبة GT4 (ISO)", isoStatus, "اختيار ملف ISO") { pick(REQ_ISO) })

        // حالة المحرك
        engineStatus = text("", 13f, Color.LTGRAY)
        root.addView(card("٣. محرك المحاكاة", engineStatus, null, null))

        playBtn = button("ابدأ اللعب", primary = true) {
            startActivity(Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_ISO_URI, prefs.getString(K_ISO_URI, null))
                putExtra(GameActivity.EXTRA_BIOS_PATH, biosFile.absolutePath)
            })
        }
        root.addView(playBtn, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })

        val demoBtn = button("تجربة التحكمات (بدون لعبة)", primary = false) {
            startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_DEMO, true))
        }
        root.addView(demoBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(root)
        })
        refresh()
    }

    // ---------- اختيار الملفات ----------
    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_ISO -> handleIso(uri)
            REQ_BIOS -> handleBios(uri)
        }
    }

    private fun handleIso(uri: Uri) {
        isoStatus.text = "جارٍ فحص الملف…"
        isoStatus.setTextColor(WARN)
        Thread {
            val result = Gt4IsoValidator.check(contentResolver, uri)
            runOnUiThread {
                when (result) {
                    is IsoCheck.Valid -> {
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (e: Exception) { /* بعض المزودين لا يدعمون الإذن الدائم */ }
                        prefs.edit()
                            .putString(K_ISO_URI, uri.toString())
                            .putString(K_ISO_LABEL, result.info.label())
                            .apply()
                    }
                    is IsoCheck.Invalid ->
                        Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        }.start()
    }

    private fun handleBios(uri: Uri) {
        biosStatus.text = "جارٍ النسخ…"
        biosStatus.setTextColor(WARN)
        Thread {
            val ok = try {
                val dst = biosFile.apply { parentFile?.mkdirs() }
                contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { out -> input.copyTo(out) }
                }
                val size = dst.length()
                if (size < (1L shl 20) || size > (16L shl 20)) { dst.delete(); false } else true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!ok) Toast.makeText(this, "ملف BIOS غير صالح (الحجم غير منطقي).", Toast.LENGTH_LONG).show()
                refresh()
            }
        }.start()
    }

    // ---------- الحالة ----------
    private fun refresh() {
        val biosOk = biosFile.exists()
        biosStatus.text = if (biosOk) "✓ تم تحميل BIOS" else "لم يتم اختيار BIOS بعد"
        biosStatus.setTextColor(if (biosOk) OK else Color.LTGRAY)

        val isoLabel = prefs.getString(K_ISO_LABEL, null)
        val isoOk = isoLabel != null && prefs.getString(K_ISO_URI, null) != null
        isoStatus.text = if (isoOk) "✓ $isoLabel" else "لم يتم اختيار اللعبة بعد (يقبل GT4 فقط)"
        isoStatus.setTextColor(if (isoOk) OK else Color.LTGRAY)

        val engineOk = NativeEngine.isInstalled()
        engineStatus.text = if (engineOk) "✓ المحرك مثبّت" else "غير مثبّت — مكتبة libps2core.so غير موجودة (راجع README)"
        engineStatus.setTextColor(if (engineOk) OK else WARN)

        playBtn.isEnabled = biosOk && isoOk
        playBtn.alpha = if (playBtn.isEnabled) 1f else 0.4f
    }

    // ---------- أدوات واجهة ----------
    private fun card(title: String, status: TextView, btnText: String?, onClick: (() -> Unit)?): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(CARD)
            }
        }
        box.addView(text(title, 16f, Color.WHITE, bold = true))
        status.setPadding(0, dp(6), 0, dp(if (btnText != null) 10 else 0))
        box.addView(status)
        if (btnText != null && onClick != null) {
            box.addView(button(btnText, primary = false) { onClick() }, LinearLayout.LayoutParams(-1, dp(44)))
        }
        return FrameWrap(box)
    }

    private fun FrameWrap(inner: View): View = LinearLayout(this).apply {
        setPadding(0, 0, 0, dp(12))
        addView(inner, LinearLayout.LayoutParams(-1, -2))
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) ACCENT else Color.rgb(48, 56, 76))
        }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
