package com.example.storycardwriter.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object StoryRepository {
    const val FileExtension = "scw"
    const val ScwMimeType = "application/vnd.storycardwriter+json"

    private const val RootDirectoryName = "story_card_writer"
    private const val StoriesDirectoryName = "stories"
    private const val StateFileName = "state.json"
    private const val DefaultStoryFileName = "untitled.scw"
    private const val FormatVersion = 2

    fun load(context: Context): StoryData {
        ensureStorage(context)
        val activeFile = activeStoryFile(context)
        if (!activeFile.exists()) {
            saveToFile(activeFile, StoryData())
        }
        return readStoryFile(activeFile).getOrDefault(StoryData())
    }

    fun save(context: Context, story: StoryData) {
        ensureStorage(context)
        saveToFile(activeStoryFile(context), story)
    }

    fun newStory(context: Context): StoryData {
        return createNew(context, StoryData())
    }

    fun createNew(context: Context, story: StoryData): StoryData {
        ensureStorage(context)
        val emptyStory = story.copy(cards = emptyList())
        val file = uniqueStoryFile(context, emptyStory.title.ifBlank { "untitled" })
        saveToFile(file, emptyStory)
        setActiveFileName(context, file.name)
        return emptyStory
    }

    fun open(context: Context, fileName: String): StoryData {
        ensureStorage(context)
        val file = storyFile(context, fileName)
        val story = readStoryFile(file).getOrThrow()
        setActiveFileName(context, file.name)
        return story
    }

    fun saveAs(context: Context, story: StoryData, requestedName: String): StoryFileInfo {
        ensureStorage(context)
        val file = storyFile(context, requestedName)
        saveToFile(file, story)
        setActiveFileName(context, file.name)
        return file.toStoryFileInfo()
    }

    fun importFromUri(context: Context, uri: Uri): StoryData {
        ensureStorage(context)
        val rawJson = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: error("Cannot open selected file")

        val story = readStoryJson(rawJson).getOrThrow()
        val file = uniqueStoryFile(context, suggestedBaseName(uri, story))
        saveToFile(file, story)
        setActiveFileName(context, file.name)
        return story
    }

    fun exportToUri(context: Context, uri: Uri, story: StoryData) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(story.toExternalScwJson().toString(2))
            }
        } ?: error("Cannot write selected file")
    }

    fun ensureScwExtension(context: Context, uri: Uri): Uri {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
        if (displayName.endsWith(".$FileExtension", ignoreCase = true)) return uri

        val renamedUri = DocumentsContract.renameDocument(
            context.contentResolver,
            uri,
            ensureScwFileName(displayName)
        )
        return requireNotNull(renamedUri) { "Cannot add .$FileExtension extension" }
    }

    fun exportCharacterToUri(context: Context, uri: Uri, character: CharacterData) {
        val json = JSONObject()
            .put("formatVersion", FormatVersion)
            .put("fileType", "StoryCardWriterCharacter")
            .put("character", character.toCharacterJson())
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.bufferedWriter().use { it.write(json.toString(2)) }
        } ?: error("Cannot write selected file")
    }

    fun importCharacterFromUri(context: Context, uri: Uri): CharacterData {
        val rawJson = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: error("Cannot open selected file")
        val root = JSONObject(rawJson)
        val json = root.optJSONObject("character") ?: root
        return json.toCharacterData()
    }

    fun createShareUri(context: Context, story: StoryData): Uri {
        val shareDirectory = File(context.cacheDir, "shared_stories").apply { mkdirs() }
        val file = File(shareDirectory, normalizeFileName(story.title.ifBlank { "untitled" }))
        file.writeText(story.toExternalScwJson().toString(2))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun listStoryFiles(context: Context): List<StoryFileInfo> {
        ensureStorage(context)
        return storiesDirectory(context)
            .listFiles { file -> file.isFile && file.extension == FileExtension }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.toStoryFileInfo() }
    }

    fun activeFileInfo(context: Context): StoryFileInfo {
        ensureStorage(context)
        return activeStoryFile(context).toStoryFileInfo()
    }

    private fun ensureStorage(context: Context) {
        storiesDirectory(context).mkdirs()
        if (!stateFile(context).exists()) {
            setActiveFileName(context, DefaultStoryFileName)
        }
    }

    private fun activeStoryFile(context: Context): File {
        val activeFileName = readActiveFileName(context) ?: DefaultStoryFileName
        return storyFile(context, activeFileName)
    }

    private fun readActiveFileName(context: Context): String? {
        val file = stateFile(context)
        if (!file.exists()) return null
        return runCatching {
            JSONObject(file.readText()).optString("activeFileName").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun setActiveFileName(context: Context, fileName: String) {
        rootDirectory(context).mkdirs()
        val json = JSONObject()
            .put("formatVersion", FormatVersion)
            .put("activeFileName", normalizeFileName(fileName))
        stateFile(context).writeText(json.toString(2))
    }

    private fun readStoryFile(file: File): Result<StoryData> {
        return readStoryJson(file.readText())
    }

    internal fun readStoryJson(rawJson: String): Result<StoryData> {
        return runCatching {
            val json = JSONObject(rawJson)
            val storyJson = json.optJSONObject("story") ?: json
            val cardsJson = storyJson.optJSONArray("cards") ?: JSONArray()
            val cards = buildList {
                for (index in 0 until cardsJson.length()) {
                    val cardJson = cardsJson.optJSONObject(index) ?: continue
                    val type = parseCardType(cardJson.optString("type")) ?: continue

                    add(
                        StoryCard(
                            id = cardJson.optString("id", UUID.randomUUID().toString()),
                            type = type,
                            body = cardJson.optString("body")
                        )
                    )
                }
            }
            val charactersJson = storyJson.optJSONArray("characters")
                ?: storyJson.optJSONArray("characterNotes")
                ?: JSONArray()
            val characters = buildList {
                for (index in 0 until charactersJson.length()) {
                    val characterJson = charactersJson.optJSONObject(index) ?: continue
                    val id = characterJson.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        CharacterData(
                            id = id,
                            name = characterJson.optString("name"),
                            photoBase64 = characterJson.optString("photoBase64")
                                .takeUnless { it.isBlank() || it == "null" },
                            notes = characterJson.optJSONArray("notes").toCharacterNotes(),
                            sections = characterJson.optJSONArray("sections").toCharacterSections()
                        )
                    )
                }
            }

            StoryData(
                title = storyJson.optString("title"),
                template = runCatching {
                    StoryTemplate.valueOf(
                        storyJson.optString(
                            "template",
                            storyJson.optString("templateName", StoryTemplate.Scenario.name)
                        )
                    )
                }.getOrDefault(StoryTemplate.Scenario),
                participantCount = storyJson.optInt("participantCount", 2).coerceIn(2, 3),
                protagonistName = storyJson.firstNonBlank("protagonistName", "heroName", fallback = "人物A"),
                partner1Name = storyJson.firstNonBlank("partner1Name", "partnerName", fallback = "人物B"),
                partner2Name = storyJson.firstNonBlank("partner2Name", fallback = "友人"),
                cards = cards,
                characters = characters
            )
        }
    }

    private fun parseCardType(value: String): CardType? = when (value) {
        "Partner1" -> CardType.Partner
        else -> runCatching { CardType.valueOf(value) }.getOrNull()
    }

    private fun JSONObject.firstNonBlank(vararg keys: String, fallback: String): String {
        keys.forEach { key ->
            optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        return fallback
    }

    private fun JSONArray?.toCharacterNotes(): List<CharacterNote> = buildList {
        val array = this@toCharacterNotes ?: return@buildList
        for (index in 0 until array.length()) {
            val note = array.optJSONObject(index) ?: continue
            add(
                CharacterNote(
                    id = note.optString("id", UUID.randomUUID().toString()),
                    text = note.optString("text")
                )
            )
        }
    }

    private fun JSONArray?.toCharacterSections(): List<CharacterSection> = buildList {
        val array = this@toCharacterSections ?: return@buildList
        for (index in 0 until array.length()) {
            val section = array.optJSONObject(index) ?: continue
            add(
                CharacterSection(
                    id = section.optString("id", UUID.randomUUID().toString()),
                    title = section.optString("title"),
                    notes = section.optJSONArray("notes").toCharacterNotes()
                )
            )
        }
    }

    private fun JSONObject.toCharacterData(): CharacterData = CharacterData(
        id = optString("id").ifBlank { "imported" },
        name = optString("name"),
        photoBase64 = optString("photoBase64").takeUnless { it.isBlank() || it == "null" },
        notes = optJSONArray("notes").toCharacterNotes(),
        sections = optJSONArray("sections").toCharacterSections()
    )

    private fun CharacterData.toCharacterJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("photoBase64", photoBase64 ?: JSONObject.NULL)
        .put("notes", notes.toNotesJson())
        .put("sections", JSONArray().apply {
            sections.forEach { section ->
                put(
                    JSONObject()
                        .put("id", section.id)
                        .put("title", section.title)
                        .put("notes", section.notes.toNotesJson())
                )
            }
        })

    private fun saveToFile(file: File, story: StoryData) {
        file.parentFile?.mkdirs()
        file.writeText(story.toScwJson().toString(2))
    }

    private fun StoryData.cardsJson(): JSONArray {
        val cardsJson = JSONArray()
        cards.forEach { card ->
            cardsJson.put(
                JSONObject()
                    .put("id", card.id)
                    .put("type", card.type.name)
                    .put("body", card.body)
            )
        }
        return cardsJson
    }

    private fun StoryData.charactersJson(): JSONArray = JSONArray().apply {
        characters.forEach { character ->
            put(
                character.copy(name = characterName(character.id)).toCharacterJson()
            )
        }
    }

    private fun List<CharacterNote>.toNotesJson(): JSONArray = JSONArray().apply {
        this@toNotesJson.forEach { note ->
            put(JSONObject().put("id", note.id).put("text", note.text))
        }
    }

    internal fun StoryData.toScwJson(): JSONObject {
        val storyJson = JSONObject()
            .put("title", title)
            .put("template", template.name)
            .put("participantCount", participantCount)
            .put("protagonistName", protagonistName)
            .put("partner1Name", partner1Name)
            .put("partner2Name", partner2Name)
            // Older app versions can continue to read these aliases.
            .put("heroName", protagonistName)
            .put("partnerName", partner1Name)
            .put("cards", cardsJson())
            .put("characters", charactersJson())

        return JSONObject()
            .put("formatVersion", FormatVersion)
            .put("fileType", "StoryCardWriter")
            .put("story", storyJson)
    }

    private fun StoryData.toExternalScwJson(): JSONObject {
        return JSONObject()
            .put("title", title)
            .put("templateName", template.name)
            .put("participantCount", participantCount)
            .put("protagonistName", protagonistName)
            .put("partner1Name", partner1Name)
            .put("partner2Name", partner2Name)
            .put("partnerName", partner1Name)
            .put("cards", cardsJson())
            .put("characters", charactersJson())
    }

    private fun rootDirectory(context: Context): File {
        return File(context.filesDir, RootDirectoryName)
    }

    private fun storiesDirectory(context: Context): File {
        return File(rootDirectory(context), StoriesDirectoryName)
    }

    private fun stateFile(context: Context): File {
        return File(rootDirectory(context), StateFileName)
    }

    private fun storyFile(context: Context, requestedName: String): File {
        return File(storiesDirectory(context), normalizeFileName(requestedName))
    }

    private fun suggestedBaseName(uri: Uri, story: StoryData): String {
        val lastPathSegment = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            .orEmpty()
        return lastPathSegment.ifBlank { story.title }.ifBlank { "imported-story" }
    }

    private fun uniqueStoryFile(context: Context, baseName: String): File {
        val directory = storiesDirectory(context)
        var index = 0
        while (true) {
            val name = if (index == 0) {
                normalizeFileName(baseName)
            } else {
                normalizeFileName("$baseName-$index")
            }
            val file = File(directory, name)
            if (!file.exists()) return file
            index++
        }
    }

    private fun normalizeFileName(requestedName: String): String {
        val baseName = requestedName
            .substringAfterLast(File.separatorChar)
            .removeSuffix(".$FileExtension", ignoreCase = true)
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim(' ', '.')
            .ifBlank { "untitled" }
        return ensureScwFileName(baseName)
    }

    private fun File.toStoryFileInfo(): StoryFileInfo {
        return StoryFileInfo(
            fileName = name,
            displayName = name.removeSuffix(".$FileExtension"),
            lastModifiedMillis = if (exists()) lastModified() else 0L,
            sizeBytes = if (exists()) length() else 0L
        )
    }
}

internal fun ensureScwFileName(requestedName: String): String {
    val name = requestedName.trim().ifBlank { "untitled" }
    return if (name.endsWith(".${StoryRepository.FileExtension}", ignoreCase = true)) {
        name.dropLast(StoryRepository.FileExtension.length + 1) + ".${StoryRepository.FileExtension}"
    } else {
        "$name.${StoryRepository.FileExtension}"
    }
}

private fun String.removeSuffix(suffix: String, ignoreCase: Boolean): String =
    if (endsWith(suffix, ignoreCase = ignoreCase)) dropLast(suffix.length) else this
