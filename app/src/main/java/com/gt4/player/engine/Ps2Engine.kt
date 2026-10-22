package com.gt4.player.engine

import android.view.Surface

/** أزرار يد PS2 (قناع بتات) للأزرار غير التناظرية. */
object Pad {
    const val START = 1
    const val SELECT = 2
    const val TRIANGLE = 4
    const val CIRCLE = 8
    const val CROSS = 16
    const val SQUARE = 32
    const val L1 = 64
    const val R1 = 128
}

/**
 * الواجهة بين التطبيق ومحرك محاكاة PS2.
 * التطبيق لا يعرف شيئاً عن تفاصيل المحاكاة؛ كل ما يفعله هو تمرير القرص والـ BIOS والشاشة والتحكم.
 */
interface Ps2Engine {
    /** هل مكتبة المحرك موجودة فعلاً داخل التطبيق؟ */
    val available: Boolean

    /** @param isoFd واصف ملف مفتوح للقراءة لملف الـ ISO (يتولى المحرك إغلاقه). */
    fun init(biosPath: String, isoFd: Int, dataDir: String): Boolean

    fun setSurface(surface: Surface?, width: Int, height: Int)

    /**
     * @param steer -1 (يسار) … +1 (يمين) → العصا اليسرى، المحور X
     * @param gas   0…1 → بنزين (يُترجم لضغط تناظري على زر X)
     * @param brake 0…1 → فرامل (يُترجم لضغط تناظري على زر المربع)
     * @param buttons قناع من [Pad]
     */
    fun setPad(steer: Float, gas: Float, brake: Float, buttons: Int)

    fun pause()
    fun resume()
    fun shutdown()
}
