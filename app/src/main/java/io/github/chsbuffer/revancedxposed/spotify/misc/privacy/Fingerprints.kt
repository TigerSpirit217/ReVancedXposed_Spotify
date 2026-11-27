package io.github.chsbuffer.revancedxposed.spotify.misc.privacy

import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import java.lang.reflect.Modifier

val shareCopyUrlFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "Spotify Link")
            methodMatcher { name = "invokeSuspend" }
        }
    }.getOrElse {
        fingerprint {
            returns("Ljava/lang/Object;")
            parameters("Ljava/lang/Object;")
            strings("clipboard", "createNewSession failed")
            methodMatcher { name = "apply" }
        }
    }
}

val formatAndroidShareSheetUrlFingerprint = findMethodDirect {
    runCatching {
        findMethod {
            matcher {
                returnType("java.lang.String")
                addUsingString("\n")
                modifiers = Modifier.PUBLIC or Modifier.STATIC
                paramTypes(null, "java.lang.String")
            }
        }.single {
            // Exclude other text formatters taking two strings
            // (e.g. the debug reporter builder).
            it.paramTypeNames.firstOrNull()?.startsWith("java.") != true
        }
    }.getOrElse {
        findMethod {
            matcher {
                returnType("java.lang.String")
                addUsingString("\n")
                paramTypes(null, "java.lang.String")
            }
        }.single {
            it.paramTypeNames.firstOrNull()?.startsWith("java.") != true
        }
    }
}
