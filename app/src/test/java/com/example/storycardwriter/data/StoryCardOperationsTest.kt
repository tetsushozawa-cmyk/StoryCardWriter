package com.example.storycardwriter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCardOperationsTest {
    @Test
    fun ensureScwFileName_addsExtensionOnlyOnce() {
        assertEquals("filename.scw", ensureScwFileName("filename"))
        assertEquals("filename.scw", ensureScwFileName("filename.scw"))
        assertEquals("filename.scw", ensureScwFileName("filename.SCW"))
    }

    @Test
    fun commitCardInput_addsTrimmedCardAndDoesNotDuplicateAfterInputIsCleared() {
        val cards = emptyList<StoryCard>().commitCardInput(
            selectedType = CardType.Idea,
            body = "  保存前の入力\n",
            editingCardId = null,
            insertAfterCardId = null
        )
        val savedAgain = cards.commitCardInput(
            selectedType = CardType.Idea,
            body = "",
            editingCardId = null,
            insertAfterCardId = null
        )

        assertEquals(1, savedAgain.size)
        assertEquals(CardType.Idea, savedAgain.single().type)
        assertEquals("相手", savedAgain.single().saveType)
        assertEquals("保存前の入力", savedAgain.single().body)
    }

    @Test
    fun commitCardInput_ignoresWhitespaceOnlyInput() {
        val existing = listOf(StoryCard(id = "existing", type = CardType.Subject, body = "本文"))

        val result = existing.commitCardInput(
            selectedType = CardType.Target,
            body = "  \n\t",
            editingCardId = null,
            insertAfterCardId = null
        )

        assertEquals(existing, result)
    }

    @Test
    fun twoPersonMode_addEditAndInsertAfter_keepExpectedOrder() {
        val hero = StoryCard(id = "hero", type = CardType.Subject, body = "最初")
        val partner = StoryCard(id = "partner", type = CardType.Idea, body = "返事")
        val narration = StoryCard(id = "narration", type = CardType.Target, body = "夕方")

        val cards = listOf(hero, partner)
            .insertCardAfter("hero", narration)
            .updateCard("partner", CardType.Reference, "走り出す")

        assertEquals(listOf("hero", "narration", "partner"), cards.map { it.id })
        assertEquals(CardType.Reference, cards.last().type)
        assertEquals("走り出す", cards.last().body)
    }

    @Test
    fun threePersonMode_addEditAndInsertAfter_preserveSecondPartner() {
        val hero = StoryCard(id = "hero", type = CardType.LegacyHero, body = "始めよう")
        val partner1 = StoryCard(id = "partner1", type = CardType.Idea, body = "はい")
        val partner2 = StoryCard(id = "partner2", type = CardType.LegacyPartner2, body = "待って")

        val cards = listOf(hero, partner1)
            .insertCardAfter("partner1", partner2)
            .updateCard("partner2", CardType.LegacyPartner2, "私も行く")

        assertEquals(listOf(CardType.LegacyHero, CardType.Idea, CardType.LegacyPartner2), cards.map { it.type })
        assertEquals("私も行く", cards.last().body)
    }
}
