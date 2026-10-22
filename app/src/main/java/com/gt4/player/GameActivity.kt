package com.gt4.player

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gt4.player.engine.NativeEngine
import com.gt4.player.engine.Pad
import com.gt4.player.engine.Ps2Engine
import com.gt4.player.ui.ControlsOverlay
import java.util.Locale

class GameActivity : Activity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_ISO_URI = "iso_uri"
        const val EXTRA_BIOS_PATH = "bios_path"
        const val EXTRA_DEMO = "demo"
    }

    private var engine: Ps2Engine? = null
    private var engineReady = false

    private lateinit var controls: ControlsOverlay
    private lateinit var readout: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastTick = 0L
    private var gas = 0f
    private var brake = 0f
    private var buttons = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTick).coerceIn(1L, 50L)
            lastTick = now

            // نحاكي الضغط التناظري: البنزين يرتفع تدريجياً (يحمي الإطارات من الانزلاق)
            gas = ramp(gas, if (controls.gasPressed) 1f else 0f, dt, upMs = 140f, downMs = 70f)
            brake = ramp(brake, if (controls.brakePressed) 1f else 0f, dt, upMs = 90f, downMs = 50f)
            val steer = controls.steer

            if (engineReady) {
                engine?.setPad(steer, gas, brake, buttons)
            } else {
                readout.text = String.format(
                    Locale.US, "مقود %+.2f   بنزين %.0f%%   فرامل %.0f%%", steer, gas * 100, brake * 100
                )
            }
            handler.postDelayed(this, 8)
        }
    }

    private fun ramp(cur: Float, target: Float, dt: Long, upMs: Float, downMs: Float): Float {
        return if (target > cur) minOf(target, cur + dt / upMs) else maxOf(target, cur - dt / downMs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val demo = intent.getBooleanExtra(EXTRA_DEMO, false)
        val isoUri = intent.getStringExtra(EXTRA_ISO_URI)
        val biosPath = intent.getStringExtra(EXTRA_BIOS_PATH)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val surface = SurfaceView(this)
        surface.holder.addCallback(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))

        controls = ControlsOverlay(this)
        root.addView(controls, FrameLayout.LayoutParams(-1, -1))

        readout = TextView(this).apply {
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(readout, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = chip("START")
        startBtn.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { buttons = buttons or Pad.START; v.alpha = 1f }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { buttons = buttons and Pad.START.inv(); v.alpha = 0.7f }
            }
            true
        }
        val pauseBtn = chip("إيقاف مؤقت")
        pauseBtn.setOnClickListener { showPauseMenu() }
        top.addView(startBtn)
        top.addView(pauseBtn)
        root.addView(top, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(dp(12), dp(12), dp(12), 0) })

        setContentView(root)
        goImmersive()

        // تشغيل المحرك
        if (!demo && isoUri != null && biosPath != null) {
            val e = NativeEngine()
            engine = e
            if (e.available) {
                val fd = contentResolver.openFileDescriptor(Uri.parse(isoUri), "r")?.detachFd() ?: -1
                engineReady = fd >= 0 && e.init(biosPath, fd, filesDir.absolutePath)
                if (!engineReady) readout.text = "فشل تشغيل المحرك. تحقق من ملف BIOS والـ ISO."
            } else {
                readout.text = "مكتبة المحرك (libps2core.so) غير مثبّتة — هذا وضع تجربة التحكمات فقط. راجع README."
            }
        } else {
            readout.text = "وضع تجربة التحكمات"
        }
    }

    private fun chip(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        alpha = 0.7f
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.argb(120, 255, 255, 255))
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
    }

    private fun showPauseMenu() {
        engine?.pause()
        running = false
        AlertDialog.Builder(this)
            .setTitle("إيقاف مؤقت")
            .setItems(arrayOf("متابعة", "خروج إلى القائمة")) { _, which ->
                if (which == 0) resumeGame() else finish()
            }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun resumeGame() {
        handler.removeCallbacks(tick)
        engine?.resume()
        running = true
        lastTick = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    override fun onResume() {
        super.onResume()
        resumeGame()
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(tick)
        engine?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.shutdown()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = showPauseMenu()

    // --- SurfaceHolder.Callback ---
    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (engineReady) engine?.setSurface(holder.surface, w, h)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (engineReady) engine?.setSurface(null, 0, 0)
    }

    private fun goImmersive() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
