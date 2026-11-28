package io.github.chsbuffer.revancedxposed.spotify

import android.icu.text.Transliterator
import android.os.Build

internal fun createTraditionalToSimplified(): (String) -> String {
    val id = "Traditional-Simplified"
    if (Build.VERSION.SDK_INT >= 29) {
        val converter = Transliterator.getInstance(id)
        return converter::transliterate
    }

    // Android 8.1 and 9 also ship ICU Transliterator, before it became public SDK API.
    val type = Class.forName("android.icu.text.Transliterator")
    val converter = type.getMethod("getInstance", String::class.java).invoke(null, id)
    val convert = type.getMethod("transliterate", String::class.java)
    return { convert.invoke(converter, it) as String }
}
