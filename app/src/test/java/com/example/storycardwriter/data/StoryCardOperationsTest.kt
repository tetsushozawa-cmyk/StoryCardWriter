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
            selectedType = CardType.Partner,
            body = "  保存前の入力\n",
            editingCardId = null,
            insertAfterCardId = null
        )
        val savedAgain = cards.commitCardInput(
            selectedType = CardType.Partner,
            body = "",
            editingCardId = null,
            insertAfterCardId = null
        )

        assertEquals(1, savedAgain.size)
        assertEquals(CardType.Partner, savedAgain.single().type)
        assertEquals("保存前の入力", savedAgain.single().body)
    }

    @Test
    fun commitCardInput_ignoresWhitespaceOnlyInput() {
        val existing = listOf(StoryCard(id = "existing", type = CardType.Hero, body = "本文"))

        val result = existing.commitCardInput(
            selectedType = CardType.Narration,
            body = "  \n\t",
            editingCardId = null,
            insertAfterCardId = null
        )

        assertEquals(existing, result)
    }

    @Test
    fun twoPersonMode_addEditAndInsertAfter_keepExpectedOrder() {
        val hero = StoryCard(id = "hero", type = CardType.Hero, body = "最初")
        val partner = StoryCard(id = "partner", type = CardType.Partner, body = "返事")
        val narration = StoryCard(id = "narration", type = CardType.Narration, body = "夕方")

        val cards = listOf(hero, partner)
            .insertCardAfter("hero", narration)
            .updateCard("partner", CardType.Action, "走り出す")

        assertEquals(listOf("hero", "narration", "partner"), cards.map { it.id })
        assertEquals(CardType.Action, cards.last().type)
        assertEquals("走り出す", cards.last().body)
    }

    @Test
    fun threePersonMode_addEditAndInsertAfter_preserveSecondPartner() {
        val hero = StoryCard(id = "hero", type = CardType.Hero, body = "始めよう")
        val partner1 = StoryCard(id = "partner1", type = CardType.Partner, body = "はい")
        val partner2 = StoryCard(id = "partner2", type = CardType.Partner2, body = "待って")

        val cards = listOf(hero, partner1)
            .insertCardAfter("partner1", partner2)
            .updateCard("partner2", CardType.Partner2, "私も行く")

        assertEquals(listOf(CardType.Hero, CardType.Partner, CardType.Partner2), cards.map { it.type })
        assertEquals("私も行く", cards.last().body)
    }
}
