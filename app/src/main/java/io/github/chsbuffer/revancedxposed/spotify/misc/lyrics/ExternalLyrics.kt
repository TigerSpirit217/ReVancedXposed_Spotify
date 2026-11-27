package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.util.LruCache
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.hookMethod
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun SpotifyHook.ExternalLyrics() {
    val bridge = ExternalLyricsBridge(classLoader, LyricSources(lyricsBetterLyrics, lyricsNetEase))
    val retrofit = classLoader.loadClass("com.spotify.connectivity.httpretrofit.RetrofitMaker")
    val factory = retrofit.declaredMethods.single { method ->
        method.name == "assertAndCreate" && Class::class.java in method.parameterTypes
    }
    val availability = ::trackLyricsAvailabilityFingerprint.method
    factory.hookMethod {
        after { param ->
            if (param.throwable != null) return@after
            val service = param.args.filterIsInstance<Class<*>>().singleOrNull() ?: return@after
            val original = param.result ?: return@after
            bridge.wrapService(service, original)?.let { param.result = it }
        }
    }
    availability.hookMethod {
        after { param ->
            if (param.throwable != null) return@after
            val track = param.args[0] ?: return@after
            if (bridge.rememberContextTrack(track) && param.result == false) param.result = true
        }
    }
    MediaSession::class.java.getMethod("setMetadata", MediaMetadata::class.java).hookMethod {
        after { param ->
            val metadata = param.args[0] as? MediaMetadata ?: return@after
            bridge.rememberMediaMetadata(metadata)
        }
    }
}

private class ExternalLyricsBridge(loader: ClassLoader, private val sources: LyricSources) {
    private val trackId = Regex("[A-Za-z0-9]{22}")
    private val tracks = LruCache<String, LyricTrack>(24)
    private val io = ThreadPoolExecutor(
        2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(12),
        { task -> Thread(task, "ReVancedXposed-Lyrics").apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }

    private val function = loader.loadClass("io.reactivex.rxjava3.functions.Function")
    private val single = loader.loadClass("io.reactivex.rxjava3.core.Single")
    private val onSubscribe = loader.loadClass("io.reactivex.rxjava3.core.SingleOnSubscribe")
    private val emitter = loader.loadClass("io.reactivex.rxjava3.core.SingleEmitter")
    private val httpError = loader.loadClass("retrofit2.HttpException")
    private val create = single.getMethod("create", onSubscribe)
    private val error = single.getMethod("error", Throwable::class.java)
    private val resume = single.getMethod("onErrorResumeNext", function)
    private val isDisposed = emitter.getMethod("isDisposed")
    private val onSuccess = emitter.getMethod("onSuccess", Any::class.java)
    private val tryOnError = emitter.getMethod("tryOnError", Throwable::class.java)

    fun rememberContextTrack(track: Any): Boolean {
        val id = track.callMethod("uri")?.toString()?.removePrefix("spotify:track:") ?: return false
        if (!trackId.matches(id)) return false
        val metadata = track.callMethod("metadata") as? Map<*, *> ?: return false
        val title = (metadata["title"] as? String)?.takeIf(String::isNotBlank)
        val artist = (metadata["artist_name"] as? String)?.takeIf(String::isNotBlank)
        if (title == null || artist == null) return tracks.get(id) != null
        val album = (metadata["album_title"] as? String)?.takeIf(String::isNotBlank)
        val duration = metadata["duration"]?.toString()?.toLongOrNull()?.takeIf { it > 0 }
        remember(LyricTrack(id, title, artist, album, duration))
        return true
    }

    fun rememberMediaMetadata(metadata: MediaMetadata) {
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.removePrefix("spotify:track:") ?: return
        if (!trackId.matches(id)) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: return
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf(String::isNotBlank)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
        remember(LyricTrack(id, title, artist, album, duration))
    }

    @Synchronized
    private fun remember(track: LyricTrack) {
        val old = tracks.get(track.id)
        tracks.put(track.id, track.copy(
            album = track.album ?: old?.album,
            durationMs = track.durationMs ?: old?.durationMs
        ))
    }

    fun wrapService(service: Class<*>, original: Any): Any? {
        if (!service.isInterface || !Proxy.isProxyClass(original.javaClass)) return null
        val methods = service.declaredMethods.mapNotNull { method ->
            if (method.returnType != single) return@mapNotNull null
            val result = (method.genericReturnType as? ParameterizedType)
                ?.actualTypeArguments?.singleOrNull() as? Class<*> ?: return@mapNotNull null
            if (result.name != LYRICS_V2 && result.name != LYRICS_V3) return@mapNotNull null
            val route = if (result.name == LYRICS_V3) "color-lyrics/v3/track/{trackId}" else "color-lyrics/v2/track/{trackId}"
            if (!method.hasRoute(route)) return@mapNotNull null
            method to SpotifyLyricsResponse(result)
        }.toMap()
        if (methods.isEmpty()) return null
        val handler = Proxy.getInvocationHandler(original)
        return Proxy.newProxyInstance(service.classLoader, arrayOf(service)) { proxy, method, args ->
            if (method.declaringClass == Any::class.java && method.name == "equals") {
                return@newProxyInstance proxy === args?.firstOrNull()
            }
            val spotifyResult = handler.invoke(original, method, args)
            val codec = methods[method] ?: return@newProxyInstance spotifyResult
            val id = (args?.firstOrNull() as? String)?.removePrefix("spotify:track:")
                ?: return@newProxyInstance spotifyResult
            if (!trackId.matches(id)) return@newProxyInstance spotifyResult

            val resumeOnMissing = singleCallback(function, "apply") { cause ->
                val failure = cause as Throwable
                val track = tracks.get(id)
                val missing = httpError.isInstance(failure) &&
                    (failure.message == "HTTP 404" || failure.message?.startsWith("HTTP 404 ") == true)
                if (missing && track != null) findLyrics(track, codec, failure) else error.invoke(null, failure)
            }
            resume.invoke(spotifyResult, resumeOnMissing)
        }
    }

    private fun Method.hasRoute(route: String): Boolean = declaredAnnotations.any { annotation ->
        annotation.annotationClass.java.declaredMethods.any { attribute ->
            attribute.returnType == String::class.java && attribute.parameterCount == 0 &&
                runCatching { attribute.invoke(annotation) == route }.getOrDefault(false)
        }
    }

    private fun findLyrics(track: LyricTrack, codec: SpotifyLyricsResponse, originalError: Throwable): Any {
        val subscribe = singleCallback(onSubscribe, "subscribe") { callback ->
            try {
                io.execute {
                    if (isDisposed.invoke(callback) == true) return@execute
                    val response = runCatching { sources.find(track)?.let { codec.encode(it, track.durationMs) } }.getOrNull()
                    if (response != null) onSuccess.invoke(callback, response) else tryOnError.invoke(callback, originalError)
                }
            } catch (_: RejectedExecutionException) {
                tryOnError.invoke(callback, originalError)
            }
            null
        }
        return checkNotNull(create.invoke(null, subscribe))
    }

    private fun singleCallback(type: Class<*>, name: String, call: (Any) -> Any?): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method: Method, args ->
            when (method.name) {
                name -> call(requireNotNull(args?.firstOrNull()))
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ExternalLyricsCallback"
                else -> null
            }
        }
}
