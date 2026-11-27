package io.github.chsbuffer.revancedxposed.spotify.misc

import app.revanced.extension.shared.Logger
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import io.github.chsbuffer.revancedxposed.HookParam
import io.github.chsbuffer.revancedxposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

@Suppress("UNCHECKED_CAST")
fun SpotifyHook.UnlockPremium() {

    // --- 1. ATTRIBUTE UNLOCK (CORE PREMIUM) ---
    // Use 'after' to intercept the result.
    // Important: create a copy, do not modify the original object.
    ::productStateProtoFingerprint.hookMethod {
        after { param ->
            val result = param.result as? Map<String, *> ?: return@after
            // To be extra safe, there is no need to reassign param.result
            // because the map was modified internally.
            UnlockPremiumPatch.overrideAttributes(result)
        }
    }

    // --- 2. GOOGLE ASSISTANT (FIX URIs) ---
    ::contextFromJsonFingerprint.hookMethod {
        after { param ->
            val context = param.result ?: return@after
            runCatching {
                val uri = context.callMethod("uri") as? String
                val url = context.callMethod("url") as? String
                val fixedUri = uri?.let { UnlockPremiumPatch.removeStationString(it) }
                val fixedUrl = url?.let { UnlockPremiumPatch.removeStationString(it) }
                if (fixedUri == uri && fixedUrl == url) return@runCatching

                val builder = context.callMethod("toBuilder") ?: return@runCatching
                if (fixedUri != null) builder.callMethod("uri", fixedUri)
                if (fixedUrl != null) builder.callMethod("url", fixedUrl)
                param.result = builder.callMethod("build")
            }.onFailure {
                Logger.printDebug { "Context station hook failed: ${it.message}" }
            }
        }
    }

    // --- 3. ANTI-SHUFFLE (GOOGLE ASSISTANT) ---
    runCatching {
        XposedHelpers.findAndHookMethod(
            $$"com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides$Builder",
            classLoader,
            "build",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: HookParam) {
                    param.thisObject?.callMethod("shufflingContext", false)
                }
            })
    }.onFailure { Logger.printDebug { "PlayerOptionOverrides hook failed: ${it.message}" } }

    // --- 4. REMOVE AD SECTIONS (HOME & BROWSE) ---
    // For Home.
    ::homeStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result as? MutableList<*> ?: return@after
            runCatching {
                // Force the list to be mutable (avoids immutable-list errors).
                sections.javaClass.findFirstFieldByExactType(Boolean::class.java).set(sections, true)
                UnlockPremiumPatch.removeHomeSections(sections)
            }
        }
    }

    // For Browse.
    ::browseStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            val sections = param.result as? MutableList<*> ?: return@after
            runCatching {
                // Force the list to be mutable.
                sections.javaClass.findFirstFieldByExactType(Boolean::class.java).set(sections, true)
                UnlockPremiumPatch.removeBrowseSections(sections)
            }
        }
    }

    // --- 5. BLOCK AD POPUPS (PENDRAGON) ---
    // Simulate a natural network error instead of blocking the call.
    val replaceWithRxError = object : XC_MethodHook() {
        val justMethod = DexMethod("Lio/reactivex/rxjava3/core/Single;->just(Ljava/lang/Object;)Lio/reactivex/rxjava3/core/Single;").toMethod()
        val onErrorField = DexField("Lio/reactivex/rxjava3/internal/operators/single/SingleOnErrorReturn;->b:Lio/reactivex/rxjava3/functions/Function;").toField()

        override fun afterHookedMethod(param: HookParam) {
            val res = param.result ?: return
            if (!res.javaClass.name.endsWith("SingleOnErrorReturn")) return
            runCatching {
                val errorFunc = onErrorField.get(res)
                val applyMethod = errorFunc.javaClass.getMethod("apply", java.lang.Object::class.java)
                val fallbackValue = applyMethod.invoke(errorFunc, Exception("Pendragon block"))
                param.result = justMethod.invoke(null, fallbackValue)
            }
        }
    }

    ::pendragonJsonFetchMessageRequestFingerprint.hookMethod(replaceWithRxError)
    ::pendragonJsonFetchMessageListRequestFingerprint.hookMethod(replaceWithRxError)
}
