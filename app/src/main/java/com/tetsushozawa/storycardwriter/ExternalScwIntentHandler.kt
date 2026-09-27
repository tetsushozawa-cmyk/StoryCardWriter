package com.tetsushozawa.storycardwriter

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import com.tetsushozawa.storycardwriter.data.StoryData
import com.tetsushozawa.storycardwriter.data.StoryRepository

internal object ExternalScwIntentHandler {
    private val fallbackMimeTypes = setOf("application/json", "application/octet-stream")

    fun importFromViewIntent(context: Context, intent: Intent): Result<StoryData>? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "content") return null

        val mimeType = intent.type?.lowercase()
        val isSupported = when (mimeType) {
            StoryRepository.ScwMimeType -> true
            in fallbackMimeTypes -> displayName(context, uri).endsWith(".scw", ignoreCase = true)
            else -> false
        }
        if (!isSupported) return null

        return runCatching { StoryRepository.importFromUri(context, uri) }
    }

    private fun displayName(context: Context, uri: android.net.Uri): String {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }
}
