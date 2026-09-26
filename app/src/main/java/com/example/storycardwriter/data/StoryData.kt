package com.example.storycardwriter.data

import java.util.UUID

enum class CardType(val displayName: String) {
    Subject("主題"),
    Idea("アイデア"),
    Target("対象"),
    Reference("参考"),
    Opinion("意見"),
    Decision("決定"),
    LegacyHero("旧 Hero"),
    LegacyPartner2("旧 Partner2"),
    Unknown("不明")
}

enum class StoryTemplate(val displayName: String) {
    Scenario("シナリオ")
}

data class StoryCard(
    val id: String = UUID.randomUUID().toString(),
    val type: CardType,
    val body: String,
    /** Original JSON is overlaid on save so unrecognized fields and spellings survive. */
    val sourceJson: String? = null,
    /** Null means preserve sourceJson.type; non-null is a deliberate/new classification. */
    val saveType: String? = null
)

data class CharacterNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String
)

data class CharacterSection(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: List<CharacterNote> = emptyList()
)

data class CharacterData(
    val id: String,
    val name: String,
    val photoBase64: String? = null,
    val notes: List<CharacterNote> = emptyList(),
    val sections: List<CharacterSection> = emptyList()
)

data class StoryData(
    val title: String = "",
    val template: StoryTemplate = StoryTemplate.Scenario,
    val participantCount: Int = 2,
    val protagonistName: String = "人物A",
    val partner1Name: String = "人物B",
    val partner2Name: String = "友人",
    val cards: List<StoryCard> = emptyList(),
    val characters: List<CharacterData> = emptyList(),
    /** Original story/root objects used for lossless pass-through of fields Android does not know. */
    val sourceStoryJson: String? = null,
    val sourceRootJson: String? = null,
    val sourceWasWrapped: Boolean = false
) {
    val heroName: String get() = protagonistName
    val partnerName: String get() = partner1Name

    fun characterName(id: String): String = when (id) {
        "protagonist" -> protagonistName
        "partner1" -> partner1Name
        "partner2" -> partner2Name
        else -> id
    }

    fun character(id: String): CharacterData = characters.firstOrNull { it.id == id }
        ?.copy(name = characterName(id))
        ?: CharacterData(id = id, name = characterName(id))

    fun withCharacter(updated: CharacterData): StoryData = copy(
        characters = characters.filterNot { it.id == updated.id } + updated.copy(name = characterName(updated.id))
    )
}

data class StoryFileInfo(
    val fileName: String,
    val displayName: String,
    val lastModifiedMillis: Long,
    val sizeBytes: Long
)
