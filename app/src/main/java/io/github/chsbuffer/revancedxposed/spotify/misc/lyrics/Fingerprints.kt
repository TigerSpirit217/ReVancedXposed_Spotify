package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import io.github.chsbuffer.revancedxposed.findMethodDirect

val trackLyricsAvailabilityFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingEqStrings("has_lyrics")
            paramTypes("com.spotify.player.model.ContextTrack")
            returnType("boolean")
        }
    }.single()
}
