package com.example.storycardwriter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.storycardwriter.data.StoryData
import com.example.storycardwriter.data.StoryRepository
import com.example.storycardwriter.ui.theme.StoryCardWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoryRepository.createNew(this, StoryData(title = "無題"))
        startActivity(Intent(this, WriterActivity::class.java))
        finish()
    }
}

private enum class StartStep {
    Home,
    NewStory
}

@Composable
private fun StoryCardWriterStartScreen() {
    var step by remember { mutableStateOf(StartStep.Home) }

    when (step) {
        StartStep.Home -> HomeScreen(onNewStory = { step = StartStep.NewStory })
        StartStep.NewStory -> NewStorySettingsScreen(onBack = { step = StartStep.Home })
    }
}

@Composable
private fun HomeScreen(onNewStory: () -> Unit) {
    val context = LocalContext.current
    var openFileMessage by remember { mutableStateOf<String?>(null) }
    val openStoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            openFileMessage = "ファイル選択をキャンセルしました"
        } else {
            runCatching { StoryRepository.importFromUri(context, uri) }
                .onSuccess {
                    openFileMessage = null
                    context.startActivity(Intent(context, WriterActivity::class.java))
                }
                .onFailure { openFileMessage = "ファイルを開けませんでした" }
        }
    }

    BaseScreen {
        AppHeader()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "開始",
                    color = AppInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Button(
                        onClick = onNewStory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                    ) {
                        Text("新規作成")
                    }
                    OutlinedButton(
                        onClick = {
                            openFileMessage = null
                            openStoryLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/*",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                    ) {
                        Text("ファイルを開く")
                    }
                }
                openFileMessage?.let { message ->
                    Text(
                        text = message,
                        color = if (message == "ファイルを開けませんでした") Color(0xFFB3261E) else AppMutedInk,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NewStorySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var participantCount by remember { mutableStateOf(2) }
    var protagonistName by remember { mutableStateOf("人物A") }
    var partner1Name by remember { mutableStateOf("人物B") }
    var partner2Name by remember { mutableStateOf("友人") }

    BaseScreen {
        AppHeader()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "新規作成",
                    color = AppInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("作品タイトル") },
                    singleLine = true,
                    colors = storyTextFieldColors(Color(0xFF73777F), Color(0xFFC4C7CE), AppMutedInk)
                )
                Text("登場人物数", color = AppMutedInk, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3).forEach { count ->
                        OutlinedButton(
                            onClick = { participantCount = count },
                            modifier = Modifier.weight(1f).height(44.dp),
                            border = BorderStroke(1.dp, if (participantCount == count) HeroBorderColor else Color.LightGray),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (participantCount == count) HeroSoftBorderColor else Color.White,
                                contentColor = AppInk
                            )
                        ) { Text("${count}人") }
                    }
                }
                OutlinedTextField(
                    value = protagonistName,
                    onValueChange = { protagonistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("主人公名（任意）") },
                    singleLine = true,
                    colors = storyTextFieldColors(HeroBorderColor, HeroSoftBorderColor, HeroLabelColor)
                )
                OutlinedTextField(
                    value = partner1Name,
                    onValueChange = { partner1Name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (participantCount == 2) "相手役名（任意）" else "相手役1名（任意）") },
                    singleLine = true,
                    colors = storyTextFieldColors(PartnerBorderColor, PartnerSoftBorderColor, PartnerLabelColor)
                )
                if (participantCount == 3) {
                    OutlinedTextField(
                        value = partner2Name,
                        onValueChange = { partner2Name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("相手役2名（任意）") },
                        singleLine = true,
                        colors = storyTextFieldColors(Color(0xFFB17BD4), Color(0xFFE7D4F3), Color(0xFF713B93))
                    )
                }
                Button(
                    onClick = {
                        val story = StoryData(
                            title = title.trim(),
                            participantCount = participantCount,
                            protagonistName = protagonistName.trim().ifBlank { "人物A" },
                            partner1Name = partner1Name.trim().ifBlank { "人物B" },
                            partner2Name = partner2Name.trim().ifBlank { "友人" },
                            cards = emptyList()
                        )
                        StoryRepository.createNew(context, story)
                        context.startActivity(Intent(context, WriterActivity::class.java))
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("執筆開始")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("戻る")
                }
            }
        }
    }
}

@Composable
private fun BaseScreen(content: @Composable ColumnScope.() -> Unit) {
    Scaffold { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppBackground),
            color = AppBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "StoryCardWriter",
        color = AppInk,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "思いついた瞬間を逃さず、一枚のカードとして残す",
        color = AppMutedInk,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

@Composable
private fun storyTextFieldColors(
    focusedBorderColor: Color,
    unfocusedBorderColor: Color,
    labelColor: Color
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppInk,
    unfocusedTextColor = AppInk,
    disabledTextColor = AppMutedInk,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    focusedLabelColor = labelColor,
    unfocusedLabelColor = labelColor,
    cursorColor = labelColor
)

internal val AppBackground = Color(0xFFF7F8FA)
internal val AppInk = Color(0xFF202124)
internal val AppMutedInk = Color(0xFF62676F)
private val HeroBorderColor = Color(0xFF6BA3E8)
private val HeroSoftBorderColor = Color(0xFFE4F0FF)
private val HeroLabelColor = Color(0xFF195CA8)
private val PartnerBorderColor = Color(0xFF74BE8A)
private val PartnerSoftBorderColor = Color(0xFFE3F6E8)
private val PartnerLabelColor = Color(0xFF26733A)
