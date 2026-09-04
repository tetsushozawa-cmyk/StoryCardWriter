package com.example.storycardwriter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storycardwriter.data.CardType
import com.example.storycardwriter.data.StoryCard
import com.example.storycardwriter.data.StoryData
import com.example.storycardwriter.data.StoryRepository
import com.example.storycardwriter.data.commitCardInput
import com.example.storycardwriter.ui.theme.StoryCardWriterTheme
import kotlinx.coroutines.launch

class WriterActivity : ComponentActivity() {
    companion object {
        const val CreateNewStoryExtra = "create_new_story"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(CreateNewStoryExtra, false)) {
            StoryRepository.createNew(this, StoryData(title = "無題"))
        }
        enableEdgeToEdge()
        setContent {
            StoryCardWriterTheme {
                WriterScreen(onCreateNewStory = { createNewStory() })
            }
        }
    }

    private fun createNewStory() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WriterScreen(onCreateNewStory: () -> Unit) {
    val context = LocalContext.current
    var story by remember { mutableStateOf(StoryRepository.load(context)) }
    var selectedType by remember { mutableStateOf(CardType.Hero) }
    var body by remember { mutableStateOf("") }
    var storyForExternalSave by remember { mutableStateOf<StoryData?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showReturnConfirmDialog by remember { mutableStateOf(false) }
    var showStorySettingsDialog by remember { mutableStateOf(false) }
    var settingsTitle by remember { mutableStateOf("") }
    var settingsProtagonistName by remember { mutableStateOf("") }
    var settingsPartner1Name by remember { mutableStateOf("") }
    var settingsPartner2Name by remember { mutableStateOf("") }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var insertAfterCardId by remember { mutableStateOf<String?>(null) }
    var lastCardVisibilityRequest by remember { mutableStateOf(0) }
    var settingsExpanded by remember { mutableStateOf(false) }
    val cardListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val availableTypes = listOf(CardType.Hero, CardType.Partner, CardType.Narration, CardType.Action)

    fun saveStory(updatedStory: StoryData) {
        story = updatedStory
        StoryRepository.save(context, updatedStory)
        hasUnsavedChanges = true
    }

    fun clearInputMode() {
        editingCardId = null
        insertAfterCardId = null
        body = ""
    }

    fun commitPendingInput(): StoryData {
        val updatedCards = story.cards.commitCardInput(
            selectedType = selectedType,
            body = body,
            editingCardId = editingCardId,
            insertAfterCardId = insertAfterCardId
        )
        if (updatedCards === story.cards) return story

        val updatedStory = story.copy(cards = updatedCards)
        saveStory(updatedStory)
        clearInputMode()
        return updatedStory
    }

    val createStoryDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(StoryRepository.ScwMimeType)
    ) { uri ->
        val storyToSave = storyForExternalSave
        if (uri != null && storyToSave != null) {
            runCatching {
                val scwUri = StoryRepository.ensureScwExtension(context, uri)
                StoryRepository.exportToUri(context, scwUri, storyToSave)
            }
                .onSuccess {
                    hasUnsavedChanges = false
                    Toast.makeText(context, ".scwファイルを保存しました", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, ".scwファイルを保存できませんでした", Toast.LENGTH_SHORT).show()
                }
        }
        storyForExternalSave = null
    }

    val openStoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching { StoryRepository.importFromUri(context, it) }
                .onSuccess { openedStory ->
                    story = openedStory
                    selectedType = CardType.Hero
                    clearInputMode()
                    hasUnsavedChanges = false
                }
                .onFailure {
                    Toast.makeText(context, "ファイルを開けませんでした", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val characterMemoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        story = StoryRepository.load(context)
    }

    fun shareStory() {
        val storyToShare = commitPendingInput()
        StoryRepository.save(context, storyToShare)
        val shareUri = StoryRepository.createShareUri(context, storyToShare)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = StoryRepository.ScwMimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "シナリオを共有"))
    }

    fun requestReturnHome() {
        if (hasUnsavedChanges) {
            showReturnConfirmDialog = true
        } else {
            onCreateNewStory()
        }
    }

    BackHandler { requestReturnHome() }

    LaunchedEffect(story.cards.size) {
        if (story.cards.isNotEmpty()) {
            val firstCardIndex = 1 + if (settingsExpanded) 1 else 0
            cardListState.animateScrollToItem(firstCardIndex + story.cards.lastIndex)
        }
    }

    LaunchedEffect(lastCardVisibilityRequest) {
        if (lastCardVisibilityRequest > 0 && story.cards.isNotEmpty()) {
            val firstCardIndex = 1 + if (settingsExpanded) 1 else 0
            cardListState.animateScrollToItem(firstCardIndex + story.cards.lastIndex)
        }
    }

    if (showReturnConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReturnConfirmDialog = false },
            text = { Text("保存せずに新しいプロジェクトを作成しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReturnConfirmDialog = false
                        onCreateNewStory()
                    }
                ) {
                    Text("戻る")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showStorySettingsDialog) {
        AlertDialog(
            onDismissRequest = { showStorySettingsDialog = false },
            title = { Text("プロジェクト設定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settingsTitle,
                        onValueChange = { settingsTitle = it },
                        label = { Text("タイトル") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settingsProtagonistName,
                        onValueChange = { settingsProtagonistName = it },
                        label = { Text("人物A") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settingsPartner1Name,
                        onValueChange = { settingsPartner1Name = it },
                        label = { Text("人物B") },
                        singleLine = true
                    )
                    if (story.participantCount == 3) {
                        OutlinedTextField(
                            value = settingsPartner2Name,
                            onValueChange = { settingsPartner2Name = it },
                            label = { Text("人物C") },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveStory(
                            story.copy(
                                title = settingsTitle.trim().ifBlank { "無題" },
                                protagonistName = settingsProtagonistName.trim().ifBlank { "人物A" },
                                partner1Name = settingsPartner1Name.trim().ifBlank { "人物B" },
                                partner2Name = settingsPartner2Name.trim().ifBlank { "友人" }
                            )
                        )
                        showStorySettingsDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showStorySettingsDialog = false }) { Text("キャンセル") }
            }
        )
    }

    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppBackground),
            color = AppBackground
        ) {
            LazyColumn(
                state = cardListState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stickyHeader {
                    Surface(color = AppBackground) {
                        TextButton(
                            onClick = {
                                if (cardListState.firstVisibleItemIndex > 1) {
                                    settingsExpanded = true
                                    coroutineScope.launch { cardListState.animateScrollToItem(0) }
                                } else {
                                    settingsExpanded = !settingsExpanded
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text(
                                if (settingsExpanded) "▲ 設定・人物メモ" else "▼ 設定・人物メモ",
                                color = AppMutedInk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (settingsExpanded) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                story.title.ifBlank { "無題の作品" },
                                color = AppInk,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = {
                                    settingsTitle = story.title
                                    settingsProtagonistName = story.protagonistName
                                    settingsPartner1Name = story.partner1Name
                                    settingsPartner2Name = story.partner2Name
                                    showStorySettingsDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) { Text("タイトル・登場人物を編集", fontSize = 12.sp) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                buildList {
                                    add("protagonist" to story.protagonistName)
                                    add("partner1" to story.partner1Name)
                                    if (story.participantCount == 3) add("partner2" to story.partner2Name)
                                }.forEach { (characterId, characterName) ->
                                    OutlinedButton(
                                        onClick = {
                                            characterMemoLauncher.launch(
                                                Intent(context, CharacterMemoActivity::class.java)
                                                    .putExtra(CharacterMemoActivity.CharacterIdExtra, characterId)
                                            )
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("$characterName メモ", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { requestReturnHome() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("新規作成") }
                        }
                    }
                }
                items(story.cards, key = { it.id }) { card ->
                    StoryCardView(
                        card = card,
                        protagonistName = story.protagonistName,
                        partner1Name = story.partner1Name,
                        partner2Name = story.partner2Name,
                        canStartInsert = editingCardId == null,
                        onInsertAfter = {
                            if (editingCardId == null) {
                                insertAfterCardId = card.id
                                body = ""
                            }
                        },
                        onEdit = {
                            insertAfterCardId = null
                            selectedType = card.type.takeIf { it in availableTypes } ?: CardType.Narration
                            body = card.body
                            editingCardId = card.id
                        },
                        onDelete = {
                            if (editingCardId == card.id || insertAfterCardId == card.id) clearInputMode()
                            saveStory(story.copy(cards = story.cards.filterNot { it.id == card.id }))
                        }
                    )
                }
                item {
                    WriterBottomBar(
                        availableTypes = availableTypes,
                        selectedType = selectedType,
                        onSelectedTypeChange = { selectedType = it },
                        body = body,
                        onBodyChange = { body = it },
                        onBodyFocused = { lastCardVisibilityRequest++ },
                        isEditing = editingCardId != null,
                        isInserting = insertAfterCardId != null,
                        onCancel = { clearInputMode() },
                        onSave = {
                            val storyToSave = commitPendingInput()
                            StoryRepository.save(context, storyToSave)
                            storyForExternalSave = storyToSave
                            createStoryDocumentLauncher.launch(suggestScwFileName(storyToSave.title))
                        },
                        onShare = { shareStory() },
                        onOpen = { openStoryLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*")) },
                        onReturnHome = { requestReturnHome() },
                        onAdd = {
                            commitPendingInput()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WriterBottomBar(
    availableTypes: List<CardType>,
    selectedType: CardType,
    onSelectedTypeChange: (CardType) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    onBodyFocused: () -> Unit,
    isEditing: Boolean,
    isInserting: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onReturnHome: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isInserting) {
                Text(
                    text = "このカードの後に追加",
                    color = AppMutedInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAdd,
                    enabled = body.isNotBlank(),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (isEditing) "更新" else "追加",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                availableTypes.forEach { type ->
                    TypeButton(
                        type = type,
                        selected = type == selectedType,
                        onClick = { onSelectedTypeChange(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = onBodyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onBodyFocused()
                    },
                label = { Text("本文") },
                textStyle = TextStyle(fontSize = 16.sp),
                minLines = 4,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppInk,
                    unfocusedTextColor = AppInk,
                    cursorColor = AppInk,
                    focusedLabelColor = AppMutedInk,
                    unfocusedLabelColor = AppMutedInk,
                    focusedBorderColor = Color(0xFF73777F),
                    unfocusedBorderColor = Color(0xFFC4C7CE)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing || isInserting) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("キャンセル", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("保存", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("共有", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text(
                        "保存ファイルを開く",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            OutlinedButton(
                onClick = onReturnHome,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("新規作成")
            }
        }
    }
}

@Composable
private fun TypeButton(
    type: CardType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = cardColorsFor(type)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) colors.selectedBackground else Color.White,
            contentColor = colors.label
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(
            text = mobileCardTypeName(type),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun StoryCardView(
    card: StoryCard,
    protagonistName: String,
    partner1Name: String,
    partner2Name: String,
    canStartInsert: Boolean,
    onInsertAfter: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val alignment = when (card.type) {
        CardType.Hero -> Alignment.CenterStart
        CardType.Partner,
        CardType.Partner2 -> Alignment.CenterEnd
        CardType.Narration,
        CardType.Action -> Alignment.Center
    }
    val label = when (card.type) {
        else -> mobileCardTypeName(card.type)
    }
    val colors = cardColorsFor(card.type)
    val cardModifier = when (card.type) {
        CardType.Hero -> Modifier
            .fillMaxWidth(0.84f)
            .padding(end = 36.dp)
        CardType.Partner,
        CardType.Partner2 -> Modifier
            .fillMaxWidth(0.84f)
            .padding(start = 36.dp)
        CardType.Narration,
        CardType.Action -> Modifier.fillMaxWidth(0.68f)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            modifier = cardModifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border),
            colors = CardDefaults.cardColors(containerColor = colors.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.labelBackground,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = colors.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SmallCardButton(
                        text = "後に追加",
                        enabled = canStartInsert,
                        onClick = onInsertAfter
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SmallCardButton(text = "編集", onClick = onEdit)
                    Spacer(modifier = Modifier.width(6.dp))
                    SmallCardButton(text = "削除", onClick = onDelete)
                }
                Text(
                    text = card.body,
                    color = AppInk,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

private fun mobileCardTypeName(type: CardType): String = when (type) {
    CardType.Hero -> "アイデア"
    CardType.Partner, CardType.Partner2 -> "メモ"
    CardType.Narration -> "会話"
    CardType.Action -> "描写"
}

@Composable
private fun SmallCardButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(30.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 30.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(text, fontSize = 11.sp)
    }
}

private fun suggestScwFileName(title: String): String {
    val baseName = title
        .ifBlank { "untitled" }
        .replace(Regex("[\\/:*?\"<>|\r\n]+"), "_")
        .trim(' ', '.')
        .ifBlank { "untitled" }
    return com.example.storycardwriter.data.ensureScwFileName(baseName)
}

private data class StoryCardColors(
    val border: Color,
    val background: Color,
    val selectedBackground: Color,
    val labelBackground: Color,
    val label: Color
)

private fun cardColorsFor(type: CardType): StoryCardColors {
    return when (type) {
        CardType.Hero -> StoryCardColors(
            border = Color(0xFF6BA3E8),
            background = Color(0xFFF3F8FF),
            selectedBackground = Color(0xFFE4F0FF),
            labelBackground = Color(0xFFE4F0FF),
            label = Color(0xFF195CA8)
        )
        CardType.Partner -> StoryCardColors(
            border = Color(0xFF74BE8A),
            background = Color(0xFFF2FBF4),
            selectedBackground = Color(0xFFE3F6E8),
            labelBackground = Color(0xFFE3F6E8),
            label = Color(0xFF26733A)
        )
        CardType.Partner2 -> StoryCardColors(
            border = Color(0xFFB17BD4),
            background = Color(0xFFFBF5FF),
            selectedBackground = Color(0xFFF0E2FA),
            labelBackground = Color(0xFFF0E2FA),
            label = Color(0xFF713B93)
        )
        CardType.Narration -> StoryCardColors(
            border = Color(0xFFB7BCC4),
            background = Color(0xFFF7F7F8),
            selectedBackground = Color(0xFFEDEFF2),
            labelBackground = Color(0xFFEDEFF2),
            label = Color(0xFF565C66)
        )
        CardType.Action -> StoryCardColors(
            border = Color(0xFFE4A25E),
            background = Color(0xFFFFF7EE),
            selectedBackground = Color(0xFFFFEAD2),
            labelBackground = Color(0xFFFFEAD2),
            label = Color(0xFF9B5B15)
        )
    }
}
