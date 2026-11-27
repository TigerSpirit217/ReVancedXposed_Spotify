package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import android.content.SharedPreferences
import io.github.chsbuffer.revancedxposed.PREF_LYRICS_BETTER_LYRICS
import io.github.chsbuffer.revancedxposed.PREF_LYRICS_NETEASE

internal data class LyricsSourceSettings(val betterLyrics: Boolean, val netEase: Boolean) {
    companion object {
        fun read(prefs: SharedPreferences): LyricsSourceSettings {
            val betterLyrics = prefs.getBoolean(PREF_LYRICS_BETTER_LYRICS, false)
            return LyricsSourceSettings(betterLyrics, prefs.getBoolean(PREF_LYRICS_NETEASE, betterLyrics))
        }
    }
}
