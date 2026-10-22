package com.gt4.player

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class Gt4Info(val serial: String, val region: String) {
    fun label() = "Gran Turismo 4 — $region ($serial)"
}

sealed class IsoCheck {
    data class Valid(val info: Gt4Info) : IsoCheck()
    data class Invalid(val reason: String) : IsoCheck()
}

/**
 * يتأكد أن ملف الـ ISO هو لعبة Gran Turismo 4 فعلاً، وليس فقط من اسم الملف:
 * يقرأ نظام الملفات ISO9660 → يفتح SYSTEM.CNF → يستخرج رقم القرص (BOOT2)
 * ويقارنه بقائمة أرقام GT4 المعروفة.
 *
 * ملاحظة: القائمة تحتوي فقط على الأرقام التي تأكدت منها (أمريكا/أوروبا/اليابان).
 * إن كانت عندك نسخة أخرى (آسيا، كوريا...) أضف رقمها هنا.
 */
object Gt4IsoValidator {

    private const val SECTOR = 2048L

    private val KNOWN = mapOf(
        "SCUS-97328" to "أمريكا (NTSC-U)",
        "SCES-51719" to "أوروبا (PAL)",
        "SCPS-17001" to "اليابان (NTSC-J)",
    )

    private val BOOT_REGEX =
        Regex("""BOOT2\s*=\s*cdrom0:\\?([A-Za-z]{4})_(\d{3})\.(\d{2})""", RegexOption.IGNORE_CASE)

    fun check(cr: ContentResolver, uri: Uri): IsoCheck {
        return try {
            val pfd = cr.openFileDescriptor(uri, "r")
                ?: return IsoCheck.Invalid("تعذّر فتح الملف.")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                inspect(stream.channel)
            }
        } catch (e: Exception) {
            IsoCheck.Invalid("خطأ أثناء قراءة الملف: ${e.message}")
        }
    }

    private fun inspect(ch: FileChannel): IsoCheck {
        // 1) الـ Primary Volume Descriptor في القطاع 16
        val pvd = readAt(ch, 16 * SECTOR, 2048)
            ?: return IsoCheck.Invalid("الملف صغير جداً أو تالف.")
        val isIso = pvd[0].toInt() == 1 &&
            String(pvd, 1, 5, Charsets.US_ASCII) == "CD001"
        if (!isIso) {
            return IsoCheck.Invalid("الملف ليس ISO قياسي (2048 بايت لكل قطاع). ملفات BIN/CUE والصيغ المضغوطة غير مدعومة.")
        }

        // 2) سجل المجلد الجذر داخل الـ PVD (الإزاحة 156)
        val rootLba = leInt(pvd, 156 + 2)
        val rootSize = leInt(pvd, 156 + 10).coerceIn(2048L, 65536L).toInt()
        val dir = readAt(ch, rootLba * SECTOR, rootSize)
            ?: return IsoCheck.Invalid("تعذّر قراءة مجلد القرص.")

        // 3) البحث عن SYSTEM.CNF
        var cnfLba = -1L
        var cnfSize = 0
        var off = 0
        while (off + 33 < dir.size) {
            val len = dir[off].toInt() and 0xFF
            if (len == 0) { // الانتقال لبداية القطاع التالي
                off = (off / 2048 + 1) * 2048
                continue
            }
            val idLen = dir[off + 32].toInt() and 0xFF
            if (off + 33 + idLen <= dir.size) {
                val name = String(dir, off + 33, idLen, Charsets.US_ASCII)
                if (name.equals("SYSTEM.CNF;1", ignoreCase = true)) {
                    cnfLba = leInt(dir, off + 2)
                    cnfSize = leInt(dir, off + 10).toInt().coerceIn(1, 2048)
                    break
                }
            }
            off += len
        }
        if (cnfLba < 0) {
            return IsoCheck.Invalid("لم أجد SYSTEM.CNF — هذا ليس قرص PlayStation 2.")
        }

        // 4) قراءة رقم القرص
        val cnf = readAt(ch, cnfLba * SECTOR, cnfSize)
            ?: return IsoCheck.Invalid("تعذّر قراءة SYSTEM.CNF.")
        val text = String(cnf, Charsets.US_ASCII)
        val m = BOOT_REGEX.find(text)
            ?: return IsoCheck.Invalid("هذا ليس قرص PS2 (لا يوجد BOOT2).")
        val serial = "${m.groupValues[1].uppercase()}-${m.groupValues[2]}${m.groupValues[3]}"

        // 5) هل هو GT4؟
        val region = KNOWN[serial]
            ?: return IsoCheck.Invalid("هذه اللعبة ($serial) ليست Gran Turismo 4. التطبيق يدعم GT4 فقط.")
        return IsoCheck.Valid(Gt4Info(serial, region))
    }

    private fun readAt(ch: FileChannel, offset: Long, len: Int): ByteArray? {
        val buf = ByteBuffer.allocate(len)
        var pos = offset
        while (buf.hasRemaining()) {
            val n = ch.read(buf, pos)
            if (n <= 0) return null
            pos += n
        }
        return buf.array()
    }

    private fun leInt(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)
}
