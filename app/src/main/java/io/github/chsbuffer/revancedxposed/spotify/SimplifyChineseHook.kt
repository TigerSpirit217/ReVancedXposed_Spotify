package io.github.chsbuffer.revancedxposed.spotify

import android.app.Notification
import android.graphics.Canvas
import android.graphics.Paint
import android.icu.text.Transliterator
import android.os.Build
import android.text.BoringLayout
import android.text.NoCopySpan
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import io.github.chsbuffer.revancedxposed.MainHook
import java.lang.reflect.Executable
import java.lang.reflect.Modifier

internal object SimplifyChineseHook {
    private val drawMethods = setOf("drawText", "drawTextRun", "drawTextOnPath", "drawPosText")
    private val measureMethods = setOf("measureText", "getTextBounds", "getTextWidths", "breakText")

    fun install() {
        val display = SimplifiedDisplayText()
        fun hookText(type: Class<*>, names: Set<String>, argument: Int = 0) {
            type.declaredMethods.filter { it.name in names }.forEach { hook(it, argument, display) }
        }

        // Use the same script for layout and drawing in both View and Compose screens.
        hookText(StaticLayout.Builder::class.java, setOf("obtain", "setText"))
        hookText(BoringLayout::class.java, setOf("make", "replaceOrMake", "isBoring"))
        BoringLayout::class.java.declaredConstructors.forEach { hook(it, 0, display) }
        platformClass("android.text.TextLine")?.let { hookText(it, setOf("set"), 1) }
        platformClass("android.graphics.text.MeasuredText\$Builder")?.declaredConstructors
            ?.forEach { hook(it, 0, display) }

        hookText(Paint::class.java, measureMethods)
        hookText(Canvas::class.java, drawMethods)
        listOf("BaseCanvas", "BaseRecordingCanvas", "RecordingCanvas", "DisplayListCanvas").forEach { name ->
            platformClass("android.graphics.$name")?.let { hookText(it, drawMethods) }
        }

        hookText(AccessibilityNodeInfo::class.java,
            setOf("setText", "setContentDescription", "setHintText", "setStateDescription", "setPaneTitle", "setTooltipText"))
        hookText(Notification.Builder::class.java,
            setOf("setContentTitle", "setContentText", "setSubText", "setTicker"))
        hookText(Toast::class.java, setOf("makeText"), 1)
        hookText(Toast::class.java, setOf("setText"))
    }

    private fun platformClass(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()

    private fun hook(method: Executable, argument: Int, display: SimplifiedDisplayText) {
        val parameter = method.parameterTypes.getOrNull(argument)
        if (parameter != String::class.java && parameter != CharSequence::class.java &&
            parameter != CharArray::class.java
        ) return
        if (Modifier.isNative(method.modifiers) || Modifier.isAbstract(method.modifiers)) return

        runCatching {
            MainHook.module.hook(method).intercept { chain ->
                val original = chain.args[argument]
                val rendered = when (original) {
                    is CharSequence -> display.convert(original)
                    is CharArray -> display.convert(original)
                    else -> original
                }
                if (rendered === original) {
                    chain.proceed()
                } else {
                    chain.proceed(chain.args.toTypedArray().apply { this[argument] = rendered })
                }
            }
        }.onFailure {
            MainHook.log(MainHook.TAG, "Could not simplify displayed text in ${method.declaringClass.name}.${method.name}", it)
        }
    }
}

internal class SimplifiedDisplayText {
    private val transliterate = createTransliterator()
    private val cache = LruCache<String, String>(256)

    fun convert(text: CharSequence): CharSequence {
        if (!text.any(::mayContainHan)) return text
        val original = text.toString()
        val simplified = simplify(original)
        if (original == simplified) return text
        if (text !is Spanned) return simplified

        return SpannableString(simplified).apply {
            for (span in text.getSpans(0, text.length, Any::class.java)) {
                if (span !is NoCopySpan) {
                    setSpan(span, text.getSpanStart(span), text.getSpanEnd(span), text.getSpanFlags(span))
                }
            }
        }
    }

    fun convert(text: CharArray): CharArray {
        if (!text.any(::mayContainHan)) return text
        val original = String(text)
        val simplified = simplify(original)
        return if (original == simplified) text else simplified.toCharArray()
    }

    private fun simplify(text: String): String {
        cache.get(text)?.let { return it }
        // Canvas and text spans address the input by UTF-16 offsets.
        val result = synchronized(transliterate) {
            runCatching {
                val converted = transliterate(text)
                if (converted.length == text.length) converted else simplifyWithFixedOffsets(text)
            }.getOrDefault(text)
        }
        if (text.length <= 2_048) {
            cache.put(text, result)
            if (text != result) cache.put(result, result)
        }
        return result
    }

    private fun simplifyWithFixedOffsets(text: String): String = buildString(text.length) {
        var offset = 0
        while (offset < text.length) {
            val next = offset + Character.charCount(text.codePointAt(offset))
            val character = text.substring(offset, next)
            append(transliterate(character).takeIf { it.length == character.length } ?: character)
            offset = next
        }
    }

    private fun mayContainHan(char: Char): Boolean =
        char in '\u3400'..'\u9fff' || char in '\uf900'..'\ufaff' || char.isHighSurrogate()

    private fun createTransliterator(): (String) -> String {
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
}
