package com.tetsushozawa.storycardwriter.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    if (card.id == targetId) {
        val classificationChanged = card.type != type
        card.copy(
            type = type,
            body = body,
            saveType = if (classificationChanged) type.desktopSaveValue else card.saveType
        )
    } else card
}

internal fun List<StoryCard>.commitCardInput(
    selectedType: CardType,
    body: String,
    editingCardId: String?,
    insertAfterCardId: String?
): List<StoryCard> {
    val trimmedBody = body.trim()
    if (trimmedBody.isEmpty()) return this

    val newCard = StoryCard(type = selectedType, body = trimmedBody, saveType = selectedType.desktopSaveValue)
    return when {
        editingCardId != null -> updateCard(editingCardId, selectedType, trimmedBody)
        insertAfterCardId != null -> insertCardAfter(insertAfterCardId, newCard)
        else -> this + newCard
    }
}

internal fun requestedScwFileName(input: String): String? {
    val baseName = input
        .trim()
        .replace(Regex("(?i)(\\.${StoryRepository.FileExtension})+$"), "")
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
        .trim(' ', '.')
        .takeIf { it.isNotEmpty() }
        ?: return null
    return "$baseName.${StoryRepository.FileExtension}"
}

internal fun correctedScwDocumentName(displayName: String): String {
    if (displayName.endsWith(".${StoryRepository.FileExtension}", ignoreCase = true)) {
        return displayName
    }
    val providerDuplicate = Regex("(?i)^(.*)\\.scw \\((\\d+)\\)$").matchEntire(displayName)
    return providerDuplicate?.let { match ->
        "${match.groupValues[1]} (${match.groupValues[2]}).${StoryRepository.FileExtension}"
    } ?: ensureScwFileName(displayName)
}

internal fun timestampedScwFileName(
    timestampMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    return SimpleDateFormat("'SCW_'yyyyMMdd_HHmmss'.scw'", Locale.US).apply {
        this.timeZone = timeZone
    }.format(Date(timestampMillis))
}

internal val CardType.desktopSaveValue: String
    get() = when (this) {
        CardType.Subject -> "主人公"
        CardType.Idea -> "相手"
        CardType.Target -> "ナレーション"
        CardType.Reference -> "アクション"
        CardType.Opinion -> "心情"
        CardType.Decision -> "効果音"
        CardType.LegacyHero -> "Hero"
        CardType.LegacyPartner2 -> "Partner2"
        CardType.Unknown -> "Unknown"
    }
