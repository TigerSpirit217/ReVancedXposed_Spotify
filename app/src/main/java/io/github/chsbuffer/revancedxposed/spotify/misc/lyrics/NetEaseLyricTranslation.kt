package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import java.lang.Character.UnicodeScript
import java.text.Normalizer
import kotlin.math.abs

private const val MAX_TIMING_DIFFERENCE_MS = 300L
private val credit = Regex(
    """^\s*(?:(?:作[词詞曲]|填[词詞]|[编編]曲|[制製]作人?|翻[译譯]|混音|母[带帶]|[录錄]音)\s*[:：]|(?:lyrics?|words|music|arrangement|compos(?:er|ed)|produc(?:er|ed)|translat(?:ion|ed))\s*(?:[:：]|by\b))""",
    RegexOption.IGNORE_CASE
)
private val nonWord = Regex("""[^\p{L}\p{N}]+""")

internal fun netEaseChineseTranslation(
    original: List<LyricLine>,
    translated: List<LyricLine>
): LyricTranslation? {
    if (translated.isEmpty()) return null
    val sungLines = original.indices.filterNot { credit.containsMatchIn(original[it].words) }
    val scripts = sungLines.distinctBy { comparisonText(original[it].words) }
        .mapNotNull { scriptOf(original[it].words) }
    val hanLines = scripts.count { it == Script.HAN }
    val foreignLines = scripts.size - hanLines
    if (foreignLines <= hanLines) return null

    val japaneseLines = scripts.count { it == Script.JAPANESE }
    val koreanLines = scripts.count { it == Script.KOREAN }
    val originalLanguage = when {
        japaneseLines * 2 > foreignLines -> "ja"
        koreanLines * 2 > foreignLines -> "ko"
        else -> "und"
    }
    val words = MutableList(original.size) { "" }
    val distances = LongArray(original.size) { MAX_TIMING_DIFFERENCE_MS + 1 }
    for (line in translated) {
        if (scriptOf(line.words) != Script.HAN || credit.containsMatchIn(line.words)) continue
        val match = sungLines.binarySearchBy(line.startMs) { original[it].startMs }
        val index = if (match >= 0) {
            sungLines[match]
        } else {
            val after = -match - 1
            listOfNotNull(sungLines.getOrNull(after - 1), sungLines.getOrNull(after))
                .minBy { abs(original[it].startMs - line.startMs) }
        }
        val distance = abs(original[index].startMs - line.startMs)
        if (distance >= distances[index] || comparisonText(line.words) == comparisonText(original[index].words)) continue
        words[index] = line.words
        distances[index] = distance
    }
    // Spotify indexes translated text by original line number, including gaps in the translation.
    return if (words.any(String::isNotEmpty)) LyricTranslation(originalLanguage, words) else null
}

private fun comparisonText(text: String): String =
    nonWord.replace(Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(), "")

private enum class Script { HAN, JAPANESE, KOREAN, OTHER }

private fun scriptOf(text: String): Script? {
    var han = 0
    var kana = 0
    var hangul = 0
    var other = 0
    var index = 0
    while (index < text.length) {
        val character = text.codePointAt(index)
        index += Character.charCount(character)
        if (!Character.isLetter(character)) continue
        when (UnicodeScript.of(character)) {
            UnicodeScript.HAN -> han++
            UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA -> kana++
            UnicodeScript.HANGUL -> hangul++
            else -> other++
        }
    }
    return when {
        kana > 0 && han + kana > other + hangul -> Script.JAPANESE
        hangul > 0 && han + hangul > other + kana -> Script.KOREAN
        han > 0 && han >= other + kana + hangul -> Script.HAN
        other + kana + hangul > 0 -> Script.OTHER
        else -> null
    }
}
