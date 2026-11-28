package io.github.chsbuffer.revancedxposed.spotify.misc.lyrics

import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.hookMethod
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import java.lang.reflect.Modifier

internal fun SpotifyHook.installLyricsCard(rememberTrack: (Any) -> Boolean) {
    val mapper = ::lyricsScrollMapperFingerprint.memberOrNull ?: return
    val card = runCatching { SpotifyLyricsCard(classLoader) }.getOrNull() ?: return
    mapper.hookMethod {
        before { param ->
            val pair = param.args[0] ?: return@before
            card.addSection(pair, rememberTrack)?.let { param.args[0] = it }
        }
    }
}

private class SpotifyLyricsCard(loader: ClassLoader) {
    private val trackClass = loader.loadClass("com.spotify.player.model.ContextTrack")
    private val responseClass = loader.loadClass("com.spotify.scrollsita.v1.NpvScrollResponse")
    private val sectionClass = loader.loadClass("com.spotify.scrollsita.v1.Section")
    private val lyricsClass = loader.loadClass("com.spotify.scrollsita.v1.Lyrics")
    private val structure = responseClass.getDeclaredField("structure_").apply { isAccessible = true }
    private val sections = structure.type.getDeclaredField("sections_").apply { isAccessible = true }
    private val sectionType = sectionClass.getDeclaredField("sectionTypeCase_").apply { isAccessible = true }
    private val structureNumber = responseClass.fieldNumber("STRUCTURE")
    private val sectionsNumber = structure.type.fieldNumber("SECTIONS")
    private val lyricsNumber = sectionClass.fieldNumber("LYRICS")
    private val entityUriNumber = lyricsClass.fieldNumber("ENTITY_URI")
    private val toByteArray = responseClass.getMethod("toByteArray")
    private val proto = SpotifyProto(responseClass)

    fun addSection(pair: Any, rememberTrack: (Any) -> Boolean): Any? {
        val pairClass = pair.javaClass
        val fields = pairClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        if (fields.size != 2 || fields.any { it.type != Any::class.java }) return null
        val values = fields.map { it.apply { isAccessible = true }.get(pair) }
        val track = values.singleOrNull(trackClass::isInstance) ?: return null
        val response = values.singleOrNull(responseClass::isInstance) ?: return null
        val existingSections = structure.get(response)?.let { sections.get(it) as? List<*> }.orEmpty()
        if (existingSections.any { sectionClass.isInstance(it) && sectionType.getInt(it) == lyricsNumber }) {
            return null
        }
        if (!rememberTrack(track)) return null
        val uri = track.callMethod("uri") as? String ?: return null
        val payload = ProtoMessage().apply {
            message(structureNumber, ProtoMessage().apply {
                message(sectionsNumber, ProtoMessage().apply {
                    message(lyricsNumber, ProtoMessage().apply { text(entityUriNumber, uri) })
                })
            })
            // Protobuf merges the two structure fields and puts the added section first.
            write(toByteArray.invoke(response) as ByteArray)
        }
        val withLyrics = proto.parse(payload)
        return pairClass.getDeclaredConstructor(Any::class.java, Any::class.java)
            .apply { isAccessible = true }.newInstance(track, withLyrics)
    }
}
