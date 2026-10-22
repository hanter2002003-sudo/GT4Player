package com.gt4.player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * طبقة التحكم باللمس (متعددة اللمسات):
 *  - يسار: شريط مقود — اسحب الإصبع يميناً/يساراً، وعند الرفع يعود للمنتصف.
 *  - يمين: دواسة فرامل (حمراء) ودواسة بنزين (خضراء، أكبر).
 * يمكنك تحريك المقود وضغط البنزين في نفس الوقت.
 */
class ControlsOverlay(context: Context) : View(context) {

    /** -1 (يسار) … +1 (يمين) */
    var steer = 0f
        private set
    var gasPressed = false
        private set
    var brakePressed = false
        private set

    private val d = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 16f * d
    }

    private val track = RectF()
    private val steerZone = RectF()
    private val gasRect = RectF()
    private val brakeRect = RectF()
    private val gasHit = RectF()
    private val brakeHit = RectF()
    private var thumbX = 0f
    private val thumbR = 30f * d

    private var steerId = -1
    private var gasId = -1
    private var brakeId = -1

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val m = 28f * d
        val trackW = minOf(w * 0.42f, 360f * d)
        track.set(m, h - m - 76f * d, m + trackW, h - m)
        steerZone.set(0f, h * 0.35f, w * 0.5f, h.toFloat())
        thumbX = track.centerX()

        val pedalW = 96f * d
        gasRect.set(w - m - pedalW, h - m - 200f * d, w - m, h - m)
        val bRight = gasRect.left - 22f * d
        brakeRect.set(bRight - pedalW, h - m - 130f * d, bRight, h - m)

        gasHit.set(gasRect)
        gasHit.inset(-14f * d, -14f * d)
        brakeHit.set(brakeRect)
        brakeHit.inset(-14f * d, -14f * d)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                claim(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    if (e.getPointerId(i) == steerId) updateSteer(e.getX(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                release(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    private fun claim(id: Int, x: Float, y: Float) {
        when {
            gasId == -1 && gasHit.contains(x, y) -> { gasId = id; gasPressed = true }
            brakeId == -1 && brakeHit.contains(x, y) -> { brakeId = id; brakePressed = true }
            steerId == -1 && steerZone.contains(x, y) -> { steerId = id; updateSteer(x) }
        }
    }

    private fun updateSteer(x: Float) {
        val half = track.width() / 2f - thumbR
        steer = ((x - track.centerX()) / half).coerceIn(-1f, 1f)
        thumbX = track.centerX() + steer * half
    }

    private fun release(id: Int) {
        when (id) {
            gasId -> { gasId = -1; gasPressed = false }
            brakeId -> { brakeId = -1; brakePressed = false }
            steerId -> { steerId = -1; steer = 0f; thumbX = track.centerX() }
        }
    }

    private fun releaseAll() {
        gasId = -1; brakeId = -1; steerId = -1
        gasPressed = false; brakePressed = false
        steer = 0f; thumbX = track.centerX()
    }

    override fun onDraw(c: Canvas) {
        // --- المقود ---
        fill.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(track, track.height() / 2, track.height() / 2, fill)
        stroke.color = Color.argb(140, 255, 255, 255)
        c.drawRoundRect(track, track.height() / 2, track.height() / 2, stroke)
        c.drawLine(track.centerX(), track.top + 14f * d, track.centerX(), track.bottom - 14f * d, stroke)

        label.textSize = 26f * d
        label.color = Color.argb(200, 255, 255, 255)
        c.drawText("‹", track.left + 26f * d, track.centerY() + 9f * d, label)
        c.drawText("›", track.right - 26f * d, track.centerY() + 9f * d, label)

        fill.color = if (steerId != -1) Color.argb(235, 90, 170, 255) else Color.argb(200, 255, 255, 255)
        c.drawCircle(thumbX, track.centerY(), thumbR, fill)

        // --- الدواسات ---
        drawPedal(c, brakeRect, Color.rgb(231, 76, 60), brakePressed, "فرامل")
        drawPedal(c, gasRect, Color.rgb(46, 204, 113), gasPressed, "بنزين")
    }

    private fun drawPedal(c: Canvas, r: RectF, color: Int, pressed: Boolean, text: String) {
        fill.color = Color.argb(if (pressed) 230 else 110, Color.red(color), Color.green(color), Color.blue(color))
        c.drawRoundRect(r, 18f * d, 18f * d, fill)
        stroke.color = Color.argb(if (pressed) 255 else 170, Color.red(color), Color.green(color), Color.blue(color))
        c.drawRoundRect(r, 18f * d, 18f * d, stroke)

        // خطوط الدواسة
        stroke.color = Color.argb(70, 255, 255, 255)
        var y = r.top + 28f * d
        while (y < r.bottom - 40f * d) {
            c.drawLine(r.left + 16f * d, y, r.right - 16f * d, y, stroke)
            y += 18f * d
        }

        label.textSize = 16f * d
        label.color = Color.WHITE
        c.drawText(text, r.centerX(), r.bottom - 14f * d, label)
    }
}
