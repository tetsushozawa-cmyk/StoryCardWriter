package com.example.storycardwriter.data

internal fun List<StoryCard>.insertCardAfter(targetId: String, newCard: StoryCard): List<StoryCard> {
    val targetIndex = indexOfFirst { it.id == targetId }
    if (targetIndex == -1) return this + newCard
    return toMutableList().apply { add(targetIndex + 1, newCard) }
}

internal fun List<StoryCard>.updateCard(
    targetId: String,
    type: CardType,
    body: String
): List<StoryCard> = map { card ->
    if (card.id == targetId) card.copy(type = type, body = body) else card
}

internal fun List<StoryCard>.commitCardInput(
    selectedType: CardType,
    body: String,
    editingCardId: String?,
    insertAfterCardId: String?
): List<StoryCard> {
    val trimmedBody = body.trim()
    if (trimmedBody.isEmpty()) return this

    val newCard = StoryCard(type = selectedType, body = trimmedBody)
    return when {
        editingCardId != null -> updateCard(editingCardId, selectedType, trimmedBody)
        insertAfterCardId != null -> insertCardAfter(insertAfterCardId, newCard)
        else -> this + newCard
    }
}
