package com.HeheJuice.Notes

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONObject
import java.io.File
import org.json.JSONArray

data class SpanData(
    val start: Int,
    val end: Int,
    val type: String,
    val size: Float? = null
)

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val spans: List<SpanData>,
    val lastModified: Long,
    val tag: String? = null,
    val audioPaths: List<String> = emptyList(),
    val isPinned: Boolean = false
)

data class AudioMetadata(
    val filePath: String,
    val title: String,
    val artist: String,
    val coverPath: String? = null
)

object NoteRepository {
    private const val NOTES_DIR = "notes"
    private const val AUDIOS_DIR = "audios"
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        val dir = getNotesDir()
        if (!dir.exists()) dir.mkdirs()
        val audioDir = getAudiosDir()
        if (!audioDir.exists()) audioDir.mkdirs()
    }

    private fun getNotesDir(): File = context.filesDir.resolve(NOTES_DIR)
    private fun getAudiosDir(): File = context.filesDir.resolve(AUDIOS_DIR)

    fun copyAudioToAppStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, AUDIOS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val fileName = "audio_${System.currentTimeMillis()}_${(1000..9999).random()}.mp3"
            val destFile = File(dir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getOrExtractAudioMetadata(filePath: String): AudioMetadata {
        val file = File(filePath)
        if (!file.exists()) {
            return AudioMetadata(filePath, file.name, "Unknown Artist")
        }

        val cacheDir = File(context.cacheDir, "audio_metadata")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val hash = filePath.hashCode().toString()
        val metaCacheFile = File(cacheDir, "$hash.json")
        val coverFile = File(cacheDir, "$hash.png")

        if (metaCacheFile.exists()) {
            try {
                val json = JSONObject(metaCacheFile.readText())
                return AudioMetadata(
                    filePath = filePath,
                    title = json.getString("title"),
                    artist = json.getString("artist"),
                    coverPath = if (coverFile.exists()) coverFile.absolutePath else null
                )
            } catch (_: Exception) { /* fall through */ }
        }

        val uri = Uri.fromFile(file)
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var coverSaved = false

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                coverFile.writeBytes(picture)
                coverSaved = true
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val finalTitle = title?.takeIf { it.isNotBlank() } ?: file.name
        val finalArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"

        val json = JSONObject().apply {
            put("title", finalTitle)
            put("artist", finalArtist)
        }
        metaCacheFile.writeText(json.toString())

        return AudioMetadata(
            filePath = filePath,
            title = finalTitle,
            artist = finalArtist,
            coverPath = if (coverSaved) coverFile.absolutePath else null
        )
    }

    private fun sanitizeTitle(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9\\-\\_ ]"), "")
            .replace(" ", "_")
            .trim()
            .ifEmpty { "note" }
    }

    private fun generateId(title: String): String {
        val sanitized = sanitizeTitle(title)
        return "$sanitized---${System.currentTimeMillis()}"
    }

    private fun getTitleFromFilename(filename: String): String {
        val parts = filename.split("---")
        return if (parts.isNotEmpty()) parts[0] else filename
    }

    private fun getMetaFile(id: String): File = getNotesDir().resolve("$id.meta")

    fun getAllNotes(): List<Note> {
        val dir = getNotesDir()
        return dir.listFiles { file -> file.extension == "txt" }
            ?.mapNotNull { file ->
                val id = file.nameWithoutExtension
                val plainText = file.readText()
                val title = getTitleFromFilename(id)
                val metaFile = getMetaFile(id)
                var spans = emptyList<SpanData>()
                var tag: String? = null
                var audioPaths = emptyList<String>()
                var isPinned = false
                if (metaFile.exists()) {
                    val metaContent = metaFile.readText()
                    spans = parseSpans(metaContent)
                    tag = parseTag(metaContent)
                    audioPaths = parseAudioPaths(metaContent)
                    isPinned = parseIsPinned(metaContent)
                }
                Note(
                    id = id,
                    title = title,
                    content = plainText,
                    spans = spans,
                    lastModified = file.lastModified(),
                    tag = tag,
                    audioPaths = audioPaths,
                    isPinned = isPinned
                )
            }
            ?.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.lastModified })
            ?: emptyList()
    }

    fun getNote(id: String): Note? {
        val file = getNotesDir().resolve("$id.txt")
        val metaFile = getMetaFile(id)
        return if (file.exists()) {
            val plainText = file.readText()
            val title = getTitleFromFilename(id)
            var spans = emptyList<SpanData>()
            var tag: String? = null
            var audioPaths = emptyList<String>()
            var isPinned = false
            if (metaFile.exists()) {
                val metaContent = metaFile.readText()
                spans = parseSpans(metaContent)
                tag = parseTag(metaContent)
                audioPaths = parseAudioPaths(metaContent)
                isPinned = parseIsPinned(metaContent)
            }
            Note(id, title, plainText, spans, file.lastModified(), tag, audioPaths, isPinned)
        } else null
    }

    // ✅ FIXED: added isPinned parameter and writes it
    fun saveNote(
        title: String,
        plainText: String,
        spans: List<SpanData>,
        tag: String? = null,
        audioPaths: List<String> = emptyList(),
        isPinned: Boolean = false
    ) {
        val id = generateId(title)
        val file = getNotesDir().resolve("$id.txt")
        val metaFile = getMetaFile(id)

        file.writeText(plainText)

        if (spans.isNotEmpty() || !tag.isNullOrEmpty() || audioPaths.isNotEmpty() || isPinned) {
            val rootObj = JSONObject()
            val jsonSpans = JSONArray()
            for (span in spans) {
                val obj = JSONObject()
                obj.put("start", span.start)
                obj.put("end", span.end)
                obj.put("type", span.type)
                span.size?.let { obj.put("size", it) }
                jsonSpans.put(obj)
            }
            rootObj.put("spans", jsonSpans)

            if (!tag.isNullOrEmpty()) {
                rootObj.put("tag", tag)
            }

            if (audioPaths.isNotEmpty()) {
                val jsonAudios = JSONArray()
                for (path in audioPaths) {
                    jsonAudios.put(path)
                }
                rootObj.put("audios", jsonAudios)
            }

            // ✅ Write isPinned if true
            if (isPinned) {
                rootObj.put("isPinned", true)
            }

            metaFile.writeText(rootObj.toString())
        } else {
            metaFile.delete()
        }
    }

    fun togglePinNote(id: String) {
        val metaFile = getMetaFile(id)
        val rootObj = if (metaFile.exists()) {
            try { JSONObject(metaFile.readText()) } catch (_: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        val currentPinned = if (rootObj.has("isPinned")) rootObj.getBoolean("isPinned") else false
        rootObj.put("isPinned", !currentPinned)
        metaFile.writeText(rootObj.toString())
    }

    fun deleteNote(id: String, deleteAudioFiles: Boolean = true) {
        if (deleteAudioFiles) {
            getNote(id)?.audioPaths?.forEach { path ->
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
        getNotesDir().resolve("$id.txt").delete()
        getMetaFile(id).delete()
    }

    private fun parseSpans(jsonString: String): List<SpanData> {
        val list = mutableListOf<SpanData>()
        try {
            val rootObj = JSONObject(jsonString)
            if (rootObj.has("spans")) {
                val jsonArray = rootObj.getJSONArray("spans")
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val start = obj.getInt("start")
                    val end = obj.getInt("end")
                    val type = obj.getString("type")
                    val size = if (obj.has("size")) obj.getDouble("size").toFloat() else null
                    list.add(SpanData(start, end, type, size))
                }
            }
        } catch (_: Exception) { /* ignore */ }
        return list
    }

    private fun parseTag(jsonString: String): String? {
        return try {
            val rootObj = JSONObject(jsonString)
            if (rootObj.has("tag")) rootObj.getString("tag") else null
        } catch (_: Exception) { null }
    }

    private fun parseAudioPaths(jsonString: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val rootObj = JSONObject(jsonString)
            if (rootObj.has("audios")) {
                val jsonArray = rootObj.getJSONArray("audios")
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
            }
        } catch (_: Exception) { /* ignore */ }
        return list
    }

    private fun parseIsPinned(jsonString: String): Boolean {
        return try {
            val rootObj = JSONObject(jsonString)
            if (rootObj.has("isPinned")) rootObj.getBoolean("isPinned") else false
        } catch (_: Exception) { false }
    }
}