package com.raqam.fast

object Digits {
    fun toEn(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private val rx = Regex("""\d[\d \-]*\d""")
    private val sep = Regex("[ \\-]")

    /** يرجع أطول رقم (4 خانات فأكثر) بعد تحويله للإنجليزي، أو null */
    fun extract(text: String): String? =
        rx.findAll(toEn(text))
            .map { it.value.replace(sep, "") }
            .filter { it.length >= 4 }
            .maxByOrNull { it.length }
}
