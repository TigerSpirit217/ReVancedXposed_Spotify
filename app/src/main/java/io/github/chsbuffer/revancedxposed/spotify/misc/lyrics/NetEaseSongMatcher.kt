package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal class NetEaseSongMatcher(private val simplifyChinese: (String) -> String) {
    private data class Match(
        val id: Long,
        val artistMatches: Boolean,
        val albumMatches: Boolean,
        val durationDifferenceMs: Long
    )

    fun rank(songs: JSONArray, track: LyricTrack): List<Long> {
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        if (title.isEmpty() || artist.isEmpty()) return emptyList()
        val album = track.album?.let(::normalize)?.takeIf(String::isNotEmpty)

        val matches = (0 until songs.length()).mapNotNull { index ->
            val song = songs.optJSONObject(index) ?: return@mapNotNull null
            val id = song.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            val titles = listOf(song.optString("name")) + song.optJSONArray("tns").names()
            if (titles.none { normalize(it) == title }) return@mapNotNull null

            val artistArray = song.optJSONArray("ar") ?: return@mapNotNull null
            val credits = (0 until artistArray.length()).mapNotNull { artistArray.optJSONObject(it) }
            val artists = credits.map { normalize(it.optString("name")) }.filter(String::isNotEmpty)
            if (artists.isEmpty()) return@mapNotNull null
            val translatedArtists = credits.flatMap { it.optJSONArray("tns").names() }.map(::normalize)
            val artistMatches = artist in artists || artist in translatedArtists || artists.joinToString("") == artist
            val albumMatches = album != null && normalize(song.optJSONObject("al")?.optString("name").orEmpty()) == album
            val duration = song.optLong("dt").takeIf { it > 0 }
            val difference = if (track.durationMs != null && duration != null) {
                abs(track.durationMs - duration)
            } else {
                Long.MAX_VALUE
            }
            if (difference != Long.MAX_VALUE && difference > 5_000) return@mapNotNull null

            if (!artistMatches) {
                // Require a declared alias and corroborate it with the album or duration.
                if (!albumMatches && difference == Long.MAX_VALUE) return@mapNotNull null
                val aliases = credits.filter { it.optLong("id") > 0 }.flatMap {
                    it.optJSONArray("alias").names() + it.optJSONArray("alia").names()
                }
                if (aliases.none { normalize(it) == artist }) return@mapNotNull null
            }
            Match(id, artistMatches, albumMatches, difference)
        }

        return matches.sortedWith(
            compareByDescending<Match> { it.artistMatches }.thenByDescending { it.albumMatches }
                .thenBy { it.durationDifferenceMs }
        ).map { it.id }.distinct()
    }

    private fun JSONArray?.names(): List<String> = if (this == null) emptyList()
        else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    private fun normalize(value: String): String {
        val widthNormalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        val normalized = if (hasHan(widthNormalized)) simplifyChinese(widthNormalized) else widthNormalized
        return normalized.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
    }

    private fun hasHan(value: String) = value.any { it in '\u3400'..'\u9fff' || it in '\uf900'..'\ufaff' }
}
