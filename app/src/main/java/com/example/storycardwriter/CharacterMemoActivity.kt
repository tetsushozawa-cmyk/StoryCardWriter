package com.example.storycardwriter

import android.os.Bundle
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storycardwriter.data.CharacterData
import com.example.storycardwriter.data.CharacterNote
import com.example.storycardwriter.data.CharacterSection
import com.example.storycardwriter.data.StoryRepository
import com.example.storycardwriter.ui.theme.StoryCardWriterTheme
import java.io.ByteArrayOutputStream

class CharacterMemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val characterId = intent.getStringExtra(CharacterIdExtra) ?: "protagonist"
        setContent {
            StoryCardWriterTheme {
                CharacterMemoScreen(characterId = characterId, onBack = { finish() })
            }
        }
    }

    companion object {
        const val CharacterIdExtra = "characterId"
    }
}

private data class NoteDialogState(
    val sectionId: String?,
    val afterNoteId: String? = null,
    val editingNoteId: String? = null,
    val initialText: String = ""
)

@Composable
private fun CharacterMemoScreen(characterId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var story by remember { mutableStateOf(StoryRepository.load(context)) }
    var character by remember { mutableStateOf(story.character(characterId)) }
    var freeMemo by remember { mutableStateOf("") }
    var sectionDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSectionDialog by remember { mutableStateOf(false) }
    var sectionTitle by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf<NoteDialogState?>(null) }
    var dialogText by remember { mutableStateOf("") }

    fun save(updated: CharacterData) {
        character = updated
        story = story.withCharacter(updated)
        StoryRepository.save(context, story)
    }

    fun notesFor(sectionId: String?): List<CharacterNote> = if (sectionId == null) {
        character.notes
    } else {
        character.sections.firstOrNull { it.id == sectionId }?.notes.orEmpty()
    }

    fun replaceNotes(sectionId: String?, notes: List<CharacterNote>) {
        if (sectionId == null) save(character.copy(notes = notes))
        else save(character.copy(sections = character.sections.map { section ->
            if (section.id == sectionId) section.copy(notes = notes) else section
        }))
    }

    fun addNote(sectionId: String?, text: String, afterId: String? = null) {
        val note = CharacterNote(text = text.trim())
        val current = notesFor(sectionId)
        val index = current.indexOfFirst { it.id == afterId }
        val updated = if (afterId != null && index >= 0) {
            current.toMutableList().apply { add(index + 1, note) }
        } else current + note
        replaceNotes(sectionId, updated)
    }

    val saveCharacterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            runCatching { StoryRepository.exportCharacterToUri(context, it, character) }
                .onSuccess { Toast.makeText(context, "キャラクターメモを保存しました", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "保存できませんでした", Toast.LENGTH_SHORT).show() }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching { imageUriToBase64(context, it) }
                .onSuccess { encoded -> save(character.copy(photoBase64 = encoded)) }
                .onFailure { Toast.makeText(context, "写真を読み込めませんでした", Toast.LENGTH_SHORT).show() }
        }
    }

    val openCharacterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching { StoryRepository.importCharacterFromUri(context, it) }
                .onSuccess { imported ->
                    save(
                        character.copy(
                            photoBase64 = imported.photoBase64,
                            notes = imported.notes,
                            sections = imported.sections
                        )
                    )
                    Toast.makeText(context, "キャラクターメモを呼び出しました", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "ファイルを開けませんでした", Toast.LENGTH_SHORT).show() }
        }
    }

    fun noteList(sectionId: String?, notes: List<CharacterNote>) = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { note ->
                MemoCard(
                    note = note,
                    onEdit = {
                        dialogText = note.text
                        noteDialog = NoteDialogState(sectionId, editingNoteId = note.id, initialText = note.text)
                    },
                    onDelete = { replaceNotes(sectionId, notes.filterNot { it.id == note.id }) },
                    onInsertAfter = {
                        dialogText = ""
                        noteDialog = NoteDialogState(sectionId, afterNoteId = note.id)
                    }
                )
            }
        }
    }

    Scaffold(containerColor = AppBackground) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = character.name,
                    modifier = Modifier.padding(top = 14.dp),
                    color = AppInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                character.photoBase64?.let { encoded ->
                    val decodedBitmap = remember(encoded) { decodeBase64Image(encoded) }
                    decodedBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "${character.name}の写真",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color(0xFFEDEFF2)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (character.photoBase64 == null) "写真を選ぶ" else "写真を変更") }
                    if (character.photoBase64 != null) {
                        OutlinedButton(
                            onClick = { save(character.copy(photoBase64 = null)) },
                            modifier = Modifier.weight(1f)
                        ) { Text("写真を削除") }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = freeMemo,
                    onValueChange = { freeMemo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自由メモ") },
                    minLines = 3,
                    colors = memoTextFieldColors()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (freeMemo.isNotBlank()) {
                                addNote(null, freeMemo)
                                freeMemo = ""
                            }
                        },
                        enabled = freeMemo.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("メモを追加") }
                    OutlinedButton(
                        onClick = { sectionTitle = ""; showSectionDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("項目枠を追加") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            saveCharacterLauncher.launch(suggestCharacterFileName(character.name))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("別ファイルに保存") }
                    OutlinedButton(
                        onClick = {
                            openCharacterLauncher.launch(
                                arrayOf("application/json", "text/*", "application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("ファイルを呼び出す") }
                }
            }
            item { noteList(null, character.notes) }
            items(character.sections, key = { it.id }) { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFB7BCC4)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(section.title, color = AppInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        noteList(section.id, section.notes)
                        OutlinedTextField(
                            value = sectionDrafts[section.id].orEmpty(),
                            onValueChange = { sectionDrafts = sectionDrafts + (section.id to it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("この項目にメモ") },
                            colors = memoTextFieldColors()
                        )
                        Button(
                            onClick = {
                                val text = sectionDrafts[section.id].orEmpty()
                                if (text.isNotBlank()) {
                                    addNote(section.id, text)
                                    sectionDrafts = sectionDrafts + (section.id to "")
                                }
                            },
                            enabled = sectionDrafts[section.id].orEmpty().isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("メモを追加") }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) { Text("戻る") }
            }
        }
    }

    if (showSectionDialog) {
        AlertDialog(
            onDismissRequest = { showSectionDialog = false },
            containerColor = Color.White,
            titleContentColor = AppInk,
            textContentColor = AppInk,
            title = { Text("項目名") },
            text = {
                OutlinedTextField(
                    value = sectionTitle,
                    onValueChange = { sectionTitle = it },
                    label = { Text("例：性格、過去、秘密") },
                    colors = memoTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (sectionTitle.isNotBlank()) {
                            save(character.copy(sections = character.sections + CharacterSection(title = sectionTitle.trim())))
                            showSectionDialog = false
                        }
                    },
                    enabled = sectionTitle.isNotBlank()
                ) { Text("作成") }
            },
            dismissButton = { TextButton(onClick = { showSectionDialog = false }) { Text("キャンセル") } }
        )
    }

    noteDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { noteDialog = null },
            containerColor = Color.White,
            titleContentColor = AppInk,
            textContentColor = AppInk,
            title = { Text(if (state.editingNoteId != null) "メモを編集" else "後に追加") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = memoTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dialogText.isNotBlank()) {
                            if (state.editingNoteId != null) {
                                replaceNotes(state.sectionId, notesFor(state.sectionId).map { note ->
                                    if (note.id == state.editingNoteId) note.copy(text = dialogText.trim()) else note
                                })
                            } else addNote(state.sectionId, dialogText, state.afterNoteId)
                            noteDialog = null
                        }
                    },
                    enabled = dialogText.isNotBlank()
                ) { Text(if (state.editingNoteId != null) "更新" else "追加") }
            },
            dismissButton = { TextButton(onClick = { noteDialog = null }) { Text("キャンセル") } }
        )
    }
}

@Composable
private fun MemoCard(
    note: CharacterNote,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInsertAfter: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFD4D7DC)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(note.text, color = AppInk, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onInsertAfter) { Text("後に追加") }
                TextButton(onClick = onEdit) { Text("編集") }
                TextButton(onClick = onDelete) { Text("削除") }
            }
        }
    }
}

@Composable
private fun memoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppInk,
    unfocusedTextColor = AppInk,
    cursorColor = AppInk,
    focusedLabelColor = AppMutedInk,
    unfocusedLabelColor = AppMutedInk
)

private fun suggestCharacterFileName(name: String): String {
    val baseName = name
        .ifBlank { "character" }
        .replace(Regex("[\\/:*?\"<>|\r\n]+"), "_")
        .trim(' ', '.')
        .ifBlank { "character" }
    return "$baseName.character.json"
}

private fun imageUriToBase64(context: android.content.Context, uri: android.net.Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Cannot open image")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: error("Cannot decode image")
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val resized = if (maxSide > 1200) {
        val scale = 1200f / maxSide
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else bitmap
    return ByteArrayOutputStream().use { output ->
        resized.compress(Bitmap.CompressFormat.JPEG, 85, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}

private fun decodeBase64Image(encoded: String): Bitmap? = runCatching {
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
