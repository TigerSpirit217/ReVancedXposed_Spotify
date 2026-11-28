package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.findMethodListDirect

internal val lyricsRetrofitInvocationFingerprint = findMethodDirect {
    val retrofitFactory = findMethod {
        matcher {
            usingEqStrings("API declarations must be interfaces.")
            paramTypes("java.lang.Class")
            returnType("java.lang.Object")
        }
    }.single()
    val handler = retrofitFactory.invokes.asSequence()
        .filter { it.name == "<init>" }
        .mapNotNull { it.declaredClass }
        .distinctBy { it.name }
        .single { type -> type.interfaces.any { it.name == "java.lang.reflect.InvocationHandler" } }
    handler.methods.single { it.name == "invoke" }
}

internal val lyricsServiceMethodsFingerprint = findMethodListDirect {
    listOf("color-lyrics/v2/track/{trackId}", "color-lyrics/v3/track/{trackId}").flatMap { route ->
        findMethod {
            matcher {
                returnType("io.reactivex.rxjava3.core.Single")
                addAnnotation { addElement { stringValue(route) } }
            }
        }
    }
}

internal val lyricsScrollMapperFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingEqStrings("Scroll section mapper threw for ")
            paramTypes("java.lang.Object")
            returnType("java.lang.Object")
        }
    }.single()
}

val trackLyricsAvailabilityFingerprint = findMethodDirect {
    findMethod {
        matcher {
            usingEqStrings("has_lyrics")
            paramTypes("com.spotify.player.model.ContextTrack")
            returnType("boolean")
        }
    }.single()
}
