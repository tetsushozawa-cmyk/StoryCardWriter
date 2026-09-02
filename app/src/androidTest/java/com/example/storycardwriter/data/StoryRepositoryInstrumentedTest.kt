package com.example.storycardwriter.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryRepositoryInstrumentedTest {
    @Test
    fun saveAndReload_twoAndThreePersonStories() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            StoryData(
                title = "二人作品",
                participantCount = 2,
                cards = listOf(StoryCard(type = CardType.Action, body = "歩く"))
            ),
            StoryData(
                title = "三人作品",
                participantCount = 3,
                partner2Name = "友人",
                cards = listOf(StoryCard(type = CardType.Partner2, body = "こんにちは"))
            )
        ).forEach { expected ->
            StoryRepository.createNew(context, expected)
            StoryRepository.save(context, expected)
            val actual = StoryRepository.load(context)
            assertEquals(expected.title, actual.title)
            assertEquals(expected.participantCount, actual.participantCount)
            assertEquals(expected.partner2Name, actual.partner2Name)
            assertEquals(expected.cards.map { it.type }, actual.cards.map { it.type })
            assertEquals(expected.cards.map { it.body }, actual.cards.map { it.body })
        }
    }

    @Test
    fun oldDataWithoutParticipantCount_defaultsToTwoPeople() {
        val oldJson = """{
          "title":"旧作品",
          "heroName":"太郎",
          "partnerName":"花子",
          "cards":[{"id":"1","type":"Partner","body":"こんにちは"}]
        }""".trimIndent()

        val story = StoryRepository.readStoryJson(oldJson).getOrThrow()
        assertEquals(2, story.participantCount)
        assertEquals("太郎", story.protagonistName)
        assertEquals("花子", story.partner1Name)
        assertEquals(CardType.Partner, story.cards.single().type)
        assertEquals(emptyList<CharacterData>(), story.characters)
    }

    @Test
    fun characterNotesAndSections_areRestoredWithStableCharacterId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = StoryData(
            title = "人物メモ作品",
            participantCount = 3,
            protagonistName = "人物A",
            characters = listOf(
                CharacterData(
                    id = "protagonist",
                    name = "人物A",
                    photoBase64 = "cGhvdG8=",
                    notes = listOf(CharacterNote(id = "note-1", text = "雨が嫌い")),
                    sections = listOf(
                        CharacterSection(
                            id = "section-1",
                            title = "過去",
                            notes = listOf(CharacterNote(id = "note-2", text = "引っ越しが多かった"))
                        )
                    )
                )
            )
        )

        StoryRepository.createNew(context, expected)
        StoryRepository.save(context, expected)
        val actual = StoryRepository.load(context).character("protagonist")

        assertEquals("protagonist", actual.id)
        assertEquals("cGhvdG8=", actual.photoBase64)
        assertEquals("雨が嫌い", actual.notes.single().text)
        assertEquals("過去", actual.sections.single().title)
        assertEquals("引っ越しが多かった", actual.sections.single().notes.single().text)
    }
}
