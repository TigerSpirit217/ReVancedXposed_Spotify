package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import java.io.ByteArrayOutputStream

internal const val LYRICS_V2 = "com.spotify.lyrics.serviceretrofit.proto.ColorLyricsResponse"
internal const val LYRICS_V3 = "com.spotify.lyrics.serviceretrofit.proto.v3.LyricsWrapperResponse"
private const val CHINESE_LANGUAGE = "zh"

internal class SpotifyLyricsResponse(private val responseClass: Class<*>) {
    private val v3 = responseClass.name == LYRICS_V3
    private val lyricsClass = responseClass.getDeclaredField("lyrics_").type
    private val lineClass = responseClass.classLoader!!.loadClass(lyricsClass.name + "\$LyricsLine")
    private val lyricsField = responseClass.fieldNumber("LYRICS")
    private val syncTypeField = lyricsClass.fieldNumber("SYNC_TYPE")
    private val linesField = lyricsClass.fieldNumber("LINES")
    private val previewLinesField = if (v3) lyricsClass.fieldNumber("PREVIEW_LINES") else 0
    private val providerField = lyricsClass.fieldNumber("PROVIDER")
    private val providerDisplayField = if (v3) 0 else lyricsClass.fieldNumber("PROVIDER_DISPLAY_NAME")
    private val providerClass = if (v3) lyricsClass.getDeclaredField("provider_").type else null
    private val startTimeField = lineClass.fieldNumber("START_TIME_MS")
    private val wordsField = lineClass.fieldNumber("WORDS")
    private val endTimeField = if (v3) lineClass.fieldNumber("END_TIME_MS") else 0
    private val proto = SpotifyProto(responseClass)
    private val lineSynced = lyricsClass.declaredMethods.asSequence().map { it.returnType }
        .distinct().flatMap { it.enumConstants?.asSequence() ?: emptySequence() }
        .filterIsInstance<Enum<*>>().single { it.name == "LINE_SYNCED" }.let {
            (it.javaClass.getMethod("getNumber").invoke(it) as Number).toLong()
        }

    fun encode(lyrics: FoundLyrics, durationMs: Long?): Any {
        val lyricMessage = ProtoMessage().apply {
            number(syncTypeField, lineSynced)
            if (providerClass != null) {
                message(providerField, ProtoMessage().apply {
                    text(providerClass.fieldNumber("NAME"), lyrics.provider)
                    text(providerClass.fieldNumber("DISPLAY_NAME"), lyrics.displayName)
                })
            } else {
                text(providerField, lyrics.provider)
                text(providerDisplayField, lyrics.displayName)
            }
            lyrics.lines.forEachIndexed { index, line ->
                val next = lyrics.lines.getOrNull(index + 1)?.startMs ?: durationMs ?: (line.startMs + 4_000)
                val lineMessage = ProtoMessage().apply {
                    number(startTimeField, line.startMs)
                    text(wordsField, line.words)
                    if (v3) number(endTimeField, next.coerceAtLeast(line.startMs))
                }
                message(linesField, lineMessage)
                if (v3 && index < 4) message(previewLinesField, lineMessage)
            }
            lyrics.chineseTranslation?.let { translation ->
                runCatching { encodeTranslation(translation) }.getOrNull()?.writeTo(this)
            }
        }
        return proto.parse(ProtoMessage().apply { message(lyricsField, lyricMessage) })
    }

    private fun encodeTranslation(translation: LyricTranslation): ProtoMessage = ProtoMessage().apply {
        val languageField = lyricsClass.fieldNumber("LANGUAGE")
        if (v3) {
            val languageClass = lyricsClass.getDeclaredField("language_").type
            val codeField = languageClass.fieldNumber("CODE")
            val translationClass = lyricsClass.getDeclaredField("translation_").type
            message(lyricsClass.fieldNumber("TRANSLATION"), ProtoMessage().apply {
                message(translationClass.fieldNumber("LANGUAGE"), ProtoMessage().apply {
                    text(codeField, CHINESE_LANGUAGE)
                })
                val linesField = translationClass.fieldNumber("LINES")
                translation.lines.forEach { text(linesField, it) }
            })
            message(languageField, ProtoMessage().apply { text(codeField, translation.originalLanguage) })
        } else {
            val alternativeClass = responseClass.classLoader!!.loadClass(lyricsClass.name + "\$Alternative")
            message(lyricsClass.fieldNumber("ALTERNATIVES"), ProtoMessage().apply {
                text(alternativeClass.fieldNumber("LANGUAGE"), CHINESE_LANGUAGE)
                val linesField = alternativeClass.fieldNumber("LINES")
                translation.lines.forEach { text(linesField, it) }
            })
            text(languageField, translation.originalLanguage)
            text(lyricsClass.fieldNumber("TRANSLATION_LANGUAGES"), CHINESE_LANGUAGE)
        }
    }
}

internal fun Class<*>.fieldNumber(field: String): Int =
    getDeclaredField("${field}_FIELD_NUMBER").apply { isAccessible = true }.getInt(null)

internal class SpotifyProto(type: Class<*>) {
    private val defaultInstance = type.getDeclaredField("DEFAULT_INSTANCE").apply { isAccessible = true }.get(null)
    private val parser = checkNotNull(type.superclass).let { base ->
        base.getDeclaredMethod("parseFrom", base, ByteArray::class.java).apply { isAccessible = true }
    }

    fun parse(message: ProtoMessage): Any =
        checkNotNull(parser.invoke(null, defaultInstance, message.toByteArray()))
}

internal class ProtoMessage : ByteArrayOutputStream() {
    fun number(field: Int, value: Long) {
        varint(field.toLong() shl 3)
        varint(value)
    }

    fun text(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(field: Int, value: ProtoMessage) = bytes(field, value.toByteArray())

    private fun bytes(field: Int, value: ByteArray) {
        varint((field.toLong() shl 3) or 2)
        varint(value.size.toLong())
        write(value)
    }

    private fun varint(value: Long) {
        var remaining = value
        while (remaining ushr 7 != 0L) {
            write((remaining.toInt() and 0x7f) or 0x80)
            remaining = remaining ushr 7
        }
        write(remaining.toInt())
    }
}
