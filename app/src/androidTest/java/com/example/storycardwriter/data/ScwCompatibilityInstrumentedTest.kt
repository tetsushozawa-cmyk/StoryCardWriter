package com.example.storycardwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScwCompatibilityInstrumentedTest {
    @Test
    fun quick4_usesDesktopCanonicalValuesAndText() {
        val cards = listOf(
            CardType.Idea to "相手",
            CardType.Target to "ナレーション",
            CardType.Reference to "アクション",
            CardType.Opinion to "心情"
        ).map { (type, saved) -> StoryCard(type = type, body = type.displayName, saveType = saved) }

        val json = StoryRepository.externalJson(StoryData(cards = cards))
        assertFalse(json.has("story"))
        cards.indices.forEach { index ->
            assertEquals(listOf("相手", "ナレーション", "アクション", "心情")[index], json.getJSONArray("cards").getJSONObject(index).getString("type"))
            assertTrue(json.getJSONArray("cards").getJSONObject(index).has("text"))
        }
    }

    @Test
    fun desktopSixAndUnknown_roundTripWithoutDataLoss() {
        val source = """{
          "title":"desktop","rootExtra":{"kept":true},
          "cards":[
            {"id":"1","type":"主人公","text":"主題本文","label":"L","color":"#123","order":7,"extra":"x"},
            {"id":"2","type":"Partner","text":"idea"},
            {"id":"3","type":"Narration","text":"target"},
            {"id":"4","type":"Action","text":"reference"},
            {"id":"5","type":"Emotion","text":"opinion"},
            {"id":"6","type":"SoundEffect","text":"decision"},
            {"id":"7","type":"FutureType","text":"future","futureField":[1,2]}
          ]
        }"""
        val story = StoryRepository.readStoryJson(source).getOrThrow()
        assertEquals(listOf(CardType.Subject, CardType.Idea, CardType.Target, CardType.Reference, CardType.Opinion, CardType.Decision, CardType.Unknown), story.cards.map { it.type })
        assertEquals("主題本文", story.cards.first().body)

        val saved = StoryRepository.externalJson(story)
        assertTrue(saved.getJSONObject("rootExtra").getBoolean("kept"))
        assertEquals(7, saved.getJSONArray("cards").length())
        val first = saved.getJSONArray("cards").getJSONObject(0)
        assertEquals("主人公", first.getString("type"))
        assertEquals("L", first.getString("label"))
        assertEquals("#123", first.getString("color"))
        assertEquals(7, first.getInt("order"))
        assertEquals("x", first.getString("extra"))
        val unknown = saved.getJSONArray("cards").getJSONObject(6)
        assertEquals("FutureType", unknown.getString("type"))
        assertEquals(2, unknown.getJSONArray("futureField").length())
    }

    @Test
    fun legacyBodyAndTypes_areReadAndOriginalTypeValuesArePreserved() {
        val source = """{"legacyRoot":1,"cards":[
          {"id":"h","type":"Hero","body":"hero"},
          {"id":"p","type":"Partner","body":"partner"},
          {"id":"p2","type":"Partner2","body":"partner2"},
          {"id":"n","type":"Narration","body":"narration"},
          {"id":"a","type":"Action","body":"action"}
        ]}"""
        val story = StoryRepository.readStoryJson(source).getOrThrow()
        assertEquals(listOf("hero", "partner", "partner2", "narration", "action"), story.cards.map { it.body })

        val saved = StoryRepository.externalJson(story)
        assertEquals(1, saved.getInt("legacyRoot"))
        assertEquals(listOf("Hero", "Partner", "Partner2", "Narration", "Action"),
            (0 until 5).map { saved.getJSONArray("cards").getJSONObject(it).getString("type") })
        assertTrue((0 until 5).all { saved.getJSONArray("cards").getJSONObject(it).has("body") })
        assertTrue((0 until 5).all { saved.getJSONArray("cards").getJSONObject(it).has("text") })
    }

    @Test
    fun editingBodyPreservesOriginalTypeAndUnknownFields() {
        val story = StoryRepository.readStoryJson("""{"cards":[{"id":"x","type":"FutureType","text":"before","z":9}]}""").getOrThrow()
        val edited = story.copy(cards = story.cards.updateCard("x", CardType.Unknown, "after"))
        val card = StoryRepository.externalJson(edited).getJSONArray("cards").getJSONObject(0)
        assertEquals("FutureType", card.getString("type"))
        assertEquals("after", card.getString("text"))
        assertEquals(9, card.getInt("z"))
    }

    @Test
    fun wrappedInternalFile_exportsRootLevelAndKeepsUnknownRootField() {
        val source = """{"formatVersion":2,"fileType":"StoryCardWriter","wrapperExtra":"keep","story":{"title":"x","cards":[{"type":"効果音","text":"決定"}]}}"""
        val saved = StoryRepository.externalJson(StoryRepository.readStoryJson(source).getOrThrow())
        assertFalse(saved.has("story"))
        assertFalse(saved.has("fileType"))
        assertEquals("keep", saved.getString("wrapperExtra"))
        assertEquals("効果音", saved.getJSONArray("cards").getJSONObject(0).getString("type"))
    }
}
