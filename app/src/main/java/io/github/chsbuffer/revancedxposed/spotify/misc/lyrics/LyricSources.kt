package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import android.net.Uri
import android.util.LruCache
import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class LyricTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?
)

internal data class LyricLine(val startMs: Long, val words: String)
internal data class FoundLyrics(val provider: String, val displayName: String, val lines: List<LyricLine>)

internal class LyricSources(
    private val betterLyrics: Boolean,
    private val netEase: Boolean
) {
    private data class Cached(val expiresAt: Long, val lyrics: FoundLyrics?)
    private val cache = LruCache<String, Cached>(64)

    fun find(track: LyricTrack): FoundLyrics? {
        val now = android.os.SystemClock.elapsedRealtime()
        cache.get(track.id)?.takeIf { now < it.expiresAt }?.let { return it.lyrics }
        val found = if (betterLyrics) runCatching { fromBetterLyrics(track) }.getOrNull() else null
        val result = found ?: if (netEase) runCatching { fromNetEase(track) }.getOrNull() else null
        val completed = android.os.SystemClock.elapsedRealtime()
        return synchronized(cache) {
            val alreadyFound = cache.get(track.id)?.takeIf { completed < it.expiresAt }?.lyrics
            if (result == null && alreadyFound != null) return@synchronized alreadyFound
            val lifetime = if (result == null) 60_000L else 30 * 60_000L
            cache.put(track.id, Cached(completed + lifetime, result))
            result
        }
    }

    private fun fromBetterLyrics(track: LyricTrack): FoundLyrics? {
        val uri = Uri.parse("https://api.betterlyrics.org/getLyrics").buildUpon()
            .appendQueryParameter("s", track.title)
            .appendQueryParameter("a", track.artist)
        track.album?.let { uri.appendQueryParameter("al", it) }
        track.durationMs?.let { uri.appendQueryParameter("d", (it / 1000.0).roundToLong().toString()) }
        val ttml = getJson(uri.build())?.optString("ttml").orEmpty()
        val lines = parseTtml(ttml)
        return if (lines.isEmpty()) null else FoundLyrics("better-lyrics", "Better Lyrics", lines)
    }

    private fun fromNetEase(track: LyricTrack): FoundLyrics? {
        val query = Uri.parse("https://music.163.com/api/cloudsearch/pc").buildUpon()
            .appendQueryParameter("s", "${track.title} ${track.artist}")
            .appendQueryParameter("type", "1")
            .appendQueryParameter("limit", "8")
            .build()
        val songs = getJson(query)?.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        val album = track.album?.let(::normalize)
        val candidates = (0 until songs.length()).mapNotNull { songs.optJSONObject(it) }.filter { song ->
            val artists = song.optJSONArray("ar") ?: return@filter false
            val names = (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
            val duration = song.optLong("dt", 0)
            normalize(song.optString("name")) == title &&
                (names.any { normalize(it) == artist } || normalize(names.joinToString(" ")) == artist) &&
                (track.durationMs == null || duration == 0L || abs(track.durationMs - duration) <= 5_000)
        }
        val song = candidates.minWithOrNull(
            compareBy<JSONObject> { album != null && normalize(it.optJSONObject("al")?.optString("name").orEmpty()) != album }
                .thenBy { track.durationMs?.let { duration -> abs(duration - it.optLong("dt")) } ?: 0L }
        ) ?: return null
        val songId = song.optLong("id")
        if (songId <= 0) return null
        val lyrics = Uri.parse("https://music.163.com/api/song/lyric/v1").buildUpon()
            .appendQueryParameter("id", songId.toString())
            .appendQueryParameter("lv", "0")
            .build()
        val lrc = getJson(lyrics)?.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val lines = parseLrc(lrc)
        return if (lines.isEmpty()) null else FoundLyrics("netease", "NetEase Cloud Music", lines)
    }

    private fun getJson(uri: Uri): JSONObject? {
        val connection = URI(uri.toString()).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ReVancedXposedSpotify")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            return connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 512 * 1024) return null
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toString(Charsets.UTF_8.name()))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}

private val lrcTimestamp = Regex("""\[(\d{1,3}:\d{2}(?:[.:]\d{1,3})?)]""")
private val lrcOffset = Regex("""(?im)^\[offset:\s*([+-]?\d+)]""")

internal fun parseLrc(lrc: String): List<LyricLine> {
    val offset = lrcOffset.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return lrc.lineSequence().flatMap { line ->
        val timestamps = lrcTimestamp.findAll(line).toList()
        val words = timestamps.lastOrNull()?.let { line.substring(it.range.last + 1).trim() }.orEmpty()
        if (words.isBlank()) emptySequence() else timestamps.asSequence().mapNotNull {
            val time = it.groupValues[1]
            val normalized = time.substringBefore(':') + ":" + time.substringAfter(':').replace(':', '.')
            timedMillis(normalized)?.let { start -> LyricLine((start + offset).coerceAtLeast(0), words) }
        }
    }.distinct().sortedBy { it.startMs }.toList()
}

internal fun parseTtml(ttml: String): List<LyricLine> {
    if (ttml.isBlank()) return emptyList()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(StringReader(ttml))
    val lines = mutableListOf<LyricLine>()
    val words = StringBuilder()
    var start: Long? = null
    var ignoredDepth = 0
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                if (parser.name == "p") {
                    start = parser.getAttributeValue(null, "begin")?.let(::timedMillis)
                    words.setLength(0)
                } else if (start != null && ignoredDepth == 0) {
                    val secondaryText = (0 until parser.attributeCount).any { index ->
                        parser.getAttributeName(index).substringAfter(':') == "role" &&
                            parser.getAttributeValue(index) in setOf("x-translation", "x-roman", "x-bg")
                    }
                    if (secondaryText) ignoredDepth = parser.depth
                }
            }
            XmlPullParser.TEXT -> if (start != null && ignoredDepth == 0) words.append(parser.text)
            XmlPullParser.END_TAG -> {
                if (parser.depth == ignoredDepth) ignoredDepth = 0
                if (parser.name == "p") {
                    val text = words.toString().trim()
                    start?.let { if (text.isNotEmpty()) lines.add(LyricLine(it, text)) }
                    start = null
                }
            }
        }
        parser.next()
    }
    return lines.distinct().sortedBy { it.startMs }
}

private fun timedMillis(value: String): Long? {
    val milliseconds = when {
        value.endsWith("ms") -> value.removeSuffix("ms").toDoubleOrNull()
        value.endsWith("s") -> value.removeSuffix("s").toDoubleOrNull()?.times(1_000)
        else -> {
            val fields = value.split(':')
            if (fields.size !in 2..3) return null
            val seconds = fields.last().toDoubleOrNull() ?: return null
            val minutes = fields[fields.size - 2].toIntOrNull() ?: return null
            val hours = if (fields.size == 3) fields[0].toIntOrNull() ?: return null else 0
            (hours * 3_600.0 + minutes * 60.0 + seconds) * 1_000
        }
    }
    return milliseconds?.takeIf { it.isFinite() && it >= 0 }?.roundToLong()
}
