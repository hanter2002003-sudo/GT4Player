// قالب جسر JNI بين التطبيق ومحرك PS2.
// هذا الملف ليس جزءاً من البناء الافتراضي — انسخه إلى مشروع NDK (CMake) خاص بك
// واربطه مع محرك PS2 مفتوح المصدر (مثل PCSX2 أو أي core متوافق)، ثم ضع الناتج باسم
//   app/src/main/jniLibs/arm64-v8a/libps2core.so
//
// تنبيه ترخيص: PCSX2 مرخّص GPL-3.0؛ إن دمجت شيفرته فيجب أن يكون تطبيقك مفتوح المصدر بنفس الترخيص.

#include <jni.h>
#include <android/native_window_jni.h>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_gt4_player_engine_NativeEngine_nInit(JNIEnv* env, jobject, jstring biosPath, jint isoFd, jstring dataDir) {
    // TODO: حوّل isoFd إلى مسار "/proc/self/fd/<isoFd>" أو مرّره مباشرة للمحرك،
    //       ثم هيّئ المحرك بملف BIOS ومجلد البيانات (حفظ اللعبة/Memory Card).
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_gt4_player_engine_NativeEngine_nSetSurface(JNIEnv* env, jobject, jobject surface, jint w, jint h) {
    // TODO: ANativeWindow* win = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    //       سلّمها للمحرك ليرسم عليها (Vulkan أو OpenGL ES).
}

JNIEXPORT void JNICALL
Java_com_gt4_player_engine_NativeEngine_nSetPad(JNIEnv*, jobject, jfloat steer, jfloat gas, jfloat brake, jint buttons) {
    // TODO: ترجمة للقيم إلى يد DualShock 2:
    //   steer  (-1..1) -> العصا اليسرى X   (128 + steer * 127)
    //   gas    (0..1)  -> ضغط زر X التناظري (0..255)
    //   brake  (0..1)  -> ضغط زر المربع التناظري (0..255)
    //   buttons        -> قناع البتات المعرّف في Ps2Engine.kt (Pad)
}

JNIEXPORT void JNICALL Java_com_gt4_player_engine_NativeEngine_nPause(JNIEnv*, jobject)    { /* TODO */ }
JNIEXPORT void JNICALL Java_com_gt4_player_engine_NativeEngine_nResume(JNIEnv*, jobject)   { /* TODO */ }
JNIEXPORT void JNICALL Java_com_gt4_player_engine_NativeEngine_nShutdown(JNIEnv*, jobject) { /* TODO */ }

}  // extern "C"
