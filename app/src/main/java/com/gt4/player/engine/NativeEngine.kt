package com.gt4.player.engine

import android.view.Surface

/**
 * يربط التطبيق بمكتبة المحرك الأصلية libps2core.so عبر JNI.
 *
 * المكتبة غير مرفقة مع المشروع: يجب وضعها في
 *   app/src/main/jniLibs/arm64-v8a/libps2core.so
 * وتنفيذ الدوال الست المعرّفة بالأسفل (راجع README.md و docs/native_bridge_template.cpp).
 * بدونها يعمل التطبيق (واجهة + فحص ISO + تجربة التحكمات) لكن اللعبة لا تبدأ.
 */
class NativeEngine : Ps2Engine {

    companion object {
        private val loaded: Boolean = try {
            System.loadLibrary("ps2core")
            true
        } catch (e: Throwable) {
            false
        }

        fun isInstalled(): Boolean = loaded
    }

    override val available: Boolean get() = loaded

    override fun init(biosPath: String, isoFd: Int, dataDir: String): Boolean =
        loaded && nInit(biosPath, isoFd, dataDir)

    override fun setSurface(surface: Surface?, width: Int, height: Int) {
        if (loaded) nSetSurface(surface, width, height)
    }

    override fun setPad(steer: Float, gas: Float, brake: Float, buttons: Int) {
        if (loaded) nSetPad(steer, gas, brake, buttons)
    }

    override fun pause() { if (loaded) nPause() }
    override fun resume() { if (loaded) nResume() }
    override fun shutdown() { if (loaded) nShutdown() }

    private external fun nInit(biosPath: String, isoFd: Int, dataDir: String): Boolean
    private external fun nSetSurface(surface: Surface?, width: Int, height: Int)
    private external fun nSetPad(steer: Float, gas: Float, brake: Float, buttons: Int)
    private external fun nPause()
    private external fun nResume()
    private external fun nShutdown()
}
