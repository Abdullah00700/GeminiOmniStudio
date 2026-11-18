package com.example.geminidevagent.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.geminidevagent.data.AgentMode
import com.example.geminidevagent.data.ArtifactItem
import com.example.geminidevagent.data.ArtifactType
import com.example.geminidevagent.data.MemoryItem
import com.example.geminidevagent.tools.McpServerConfig
import com.example.geminidevagent.data.WorkspaceFile
import com.example.geminidevagent.model.AttachmentData
import com.example.geminidevagent.model.ChatMessage
import com.example.geminidevagent.model.MessageSender
import com.example.geminidevagent.model.TaskItem
import com.example.geminidevagent.model.TaskPriority

@Composable
private fun GeminiSparkleLogo(modifier: Modifier = Modifier) {
    // Light blue (#7DD3FC) top-right to White (#FFFFFF) bottom-left
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.55f, h * 0.35f, w * 0.65f, h * 0.45f, w, h * 0.5f)
            cubicTo(w * 0.65f, h * 0.55f, w * 0.55f, h * 0.65f, w * 0.5f, h)
            cubicTo(w * 0.45f, h * 0.65f, w * 0.35f, h * 0.55f, 0f, h * 0.5f)
            cubicTo(w * 0.35f, h * 0.45f, w * 0.45f, h * 0.35f, w * 0.5f, 0f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF7DD3FC), Color.White),
                start = Offset(w, 0f),
                end = Offset(0f, h)
            )
        )
    }
}

@Composable
private fun CleanGlassCanvas(content: @Composable BoxScope.() -> Unit) {
    val drift = rememberInfiniteTransition(label = "glassDrift")
    val shift by drift.animateFloat(
        initialValue = -0.03f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(tween(10000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift"
    )
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topGlow = Offset(size.width * (0.22f + shift), size.height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF4F46E5).copy(alpha = 0.16f), Color.Transparent), topGlow, size.width * 0.72f),
                center = topGlow, radius = size.width * 0.72f
            )
            val botGlow = Offset(size.width * (0.80f - shift), size.height * 0.90f)
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.11f), Color.Transparent), botGlow, size.width * 0.65f),
                center = botGlow, radius = size.width * 0.65f
            )
        }
        content()
    }
}

@Composable
private fun FloatingGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fillAlpha: Float = 0.76f,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val fill = Color(0xFF14161B).copy(alpha = fillAlpha)
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f)))
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, border = BorderStroke(1.dp, rim), shadowElevation = 10.dp, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, border = BorderStroke(1.dp, rim), shadowElevation = 10.dp, content = content)
    }
}

@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Playground, 1=Build Workspace, 2=Tasks, 3=Memory
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    val pendingConfirm by viewModel.pendingConfirm.collectAsState()
    pendingConfirm?.let { pc ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveConfirm(false) },
            title = { Text("Allow ${pc.tool}?") },
            text = { Text(pc.details) },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.alwaysAllow(pc.tool) }) { Text("Always allow") }
                    TextButton(onClick = { viewModel.resolveConfirm(true) }) { Text("Allow") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.resolveConfirm(false) }) { Text("Deny") } }
        )
    }

    val messages by viewModel.messages.collectAsState()
    val files by viewModel.workspaceFiles.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val artifacts by viewModel.artifacts.collectAsState()
    val agentMode by viewModel.agentMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val activeKeyIndex by viewModel.activeKeyIndex.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val linkedAccount by viewModel.linkedAccount.collectAsState()
    val customInstructions by viewModel.customInstructions.collectAsState()
    val featureToggles by viewModel.featureToggles.collectAsState()
    val screenControlEnabled by viewModel.screenControlEnabled.collectAsState()
    val voiceModeEnabled by viewModel.voiceModeEnabled.collectAsState()
    val pendingAttachment by viewModel.pendingAttachment.collectAsState()

    CleanGlassCanvas {
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { it / 4 * dir } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(160)))
            },
            label = "tabTransition"
        ) { tab ->
            when (tab) {
                0 -> ChatTab(messages, isLoading, streamingText, agentMode, viewModel::setAgentMode, pendingAttachment, viewModel::attachUri, viewModel::clearAttachment, viewModel::sendMessage)
                1 -> WorkspaceTab(
                    files = files,
                    onWriteFile = { p, c -> viewModel.workspaceRepository.writeFile(p, c) },
                    onReadFile = { viewModel.workspaceRepository.readFile(it) },
                    onResolveFile = { viewModel.workspaceRepository.resolveSafe(it) },
                    onDelete = { viewModel.workspaceRepository.deleteFile(it) },
                    onClearAll = { viewModel.workspaceRepository.clearAll() }
                )
                2 -> ArtifactsTab(
                    artifacts = artifacts,
                    onApprove = { art -> selectedTab = 0; viewModel.approvePlanAndExecute(art) },
                    onDelete = { viewModel.artifactRepository.deleteArtifact(it) },
                    onClearAll = { viewModel.artifactRepository.clearAll() }
                )
                3 -> TasksTab(tasks, viewModel.taskRepository::toggleTask, viewModel.taskRepository::deleteTaskByIdOrTitle, viewModel.taskRepository::clearCompleted)
                else -> MemoryTab(
                    memories = memories,
                    onGenerateFromChat = { selectedTab = 0; viewModel.generateMemoryFromChat() },
                    onAddMemory = { f, c -> viewModel.memoryRepository.addMemory(f, c, "User") },
                    onDelete = { viewModel.memoryRepository.deleteMemory(it) },
                    onClearAll = { viewModel.memoryRepository.clearAll() }
                )
            }
        }

        // Floating Smoked-Glass Top Bar
        FloatingGlassSurface(
            shape = RoundedCornerShape(26.dp),
            fillAlpha = 0.72f,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedTab = (selectedTab + 1) % 5 }) {
                    Icon(Icons.Default.Menu, contentDescription = "Cycle View")
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                        viewModel.refreshModelsFromApi()
                        showModelSelector = true
                    }.padding(vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GeminiSparkleLogo(modifier = Modifier.size(18.dp))
                        Text(
                            text = when (selectedTab) { 0 -> "Gemini"; 1 -> "Build"; 2 -> "Artifacts"; 3 -> "Tasks"; else -> "Memory" },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Model", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = if (apiKeys.isEmpty()) "Add API Key" else "$activeModel • Key ${activeKeyIndex + 1}/${apiKeys.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (apiKeys.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.toggleVoiceMode() }) {
                    Icon(
                        imageVector = if (voiceModeEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Voice Mode",
                        tint = if (voiceModeEnabled) Color(0xFF7DD3FC) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { selectedTab = if (selectedTab == 1) 0 else 1 }) {
                    Icon(Icons.Default.Build, contentDescription = "Build Mode", tint = if (selectedTab == 1) Color(0xFF7DD3FC) else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Settings")
                }
            }
        }
    }

    if (linkedAccount == null) {
        AccountLinkDialog(onLink = { name, email -> viewModel.linkAccount(name, email) })
    }

    if (showModelSelector) {
        ModelSelectorDialog(
            currentModel = activeModel,
            models = availableModels,
            onRefresh = { viewModel.refreshModelsFromApi() },
            onSelect = { viewModel.selectModel(it); showModelSelector = false },
            onDismiss = { showModelSelector = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            initialKeys = apiKeys,
            activeKeyIndex = activeKeyIndex,
            initialModel = activeModel,
            initialCustomInstructions = customInstructions,
            initialGithubToken = viewModel.getGithubToken(),
            initialWaContacts = viewModel.getWhatsAppContacts(),
            initialMcpServers = viewModel.getMcpServers(),
            initialScreenControl = screenControlEnabled,
            initialToggles = featureToggles,
            initialApprovalSkipAll = viewModel.getApprovalSkipAll(),
            initialAlwaysAllowed = viewModel.getAlwaysAllowedTools(),
            onSaveApproval = { skip, allowed -> viewModel.saveApproval(skip, allowed) },
            linkedAccount = linkedAccount,
            isAccessibilityConnected = viewModel.isAccessibilityConnected(),
            onUnlinkAccount = { viewModel.unlinkAccount(); showSettingsDialog = false },
            onDismiss = { showSettingsDialog = false },
            onSaveWaContacts = { viewModel.saveWhatsAppContacts(it) },
            onSaveMcpServers = { viewModel.saveMcpServers(it) },
            onSave = { keys, model, instr, gh, sc, tg ->
                viewModel.saveSettings(keys, model, instr, gh, sc, tg)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun ChatTab(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    streamingText: String,
    agentMode: AgentMode,
    onSetMode: (AgentMode) -> Unit,
    pendingAttachment: AttachmentData?,
    onAttachUri: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showPlusMenu by remember { mutableStateOf(false) }
    var showQuickSuggestions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onAttachUri(uri) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onAttachUri(uri) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onSend)
        }
    }

    val quickPrompts = listOf(
        "Build a sleek TypeScript Pomodoro web app in index.html",
        "Open Chrome and find the best cat images",
        "Set an alarm for 7:30 AM and add a task to stretch",
        "Test GET https://api.github.com/zen",
        "Turn on my flashlight"
    )

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 104.dp, bottom = 152.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Box(modifier = Modifier.animateItem(fadeInSpec = tween(220))) { MessageRow(msg) }
            }
            if (isLoading && streamingText.isNotBlank()) {
                item(key = "streaming") {
                    Text(streamingText, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (isLoading) item(key = "thinking") { ThinkingIndicator() }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().fillMaxWidth()) {
            AnimatedVisibility(visible = showQuickSuggestions || messages.size <= 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickPrompts) { prompt ->
                        FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.72f, onClick = { showQuickSuggestions = false; onSend(prompt) }) {
                            Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = pendingAttachment != null) {
                pendingAttachment?.let { att ->
                    FloatingGlassSurface(shape = RoundedCornerShape(14.dp), fillAlpha = 0.80f, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (att.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.AttachFile, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(att.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = onClearAttachment, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                AgentMode.entries.forEach { m ->
                    FilterChip(
                        selected = agentMode == m,
                        onClick = { onSetMode(m) },
                        label = { Text(m.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Text("Gemini is AI and can make mistakes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))

            FloatingGlassSurface(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), shape = RoundedCornerShape(30.dp), fillAlpha = 0.76f) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showPlusMenu = true }) { Icon(Icons.Default.Add, "Attach") }
                        DropdownMenu(expanded = showPlusMenu, onDismissRequest = { showPlusMenu = false }) {
                            DropdownMenuItem(text = { Text("Attach Image") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { showPlusMenu = false; imagePicker.launch("image/*") })
                            DropdownMenuItem(text = { Text("Attach File (PDF, Code, Doc)") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = { showPlusMenu = false; filePicker.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text(if (showQuickSuggestions) "Hide Quick Prompts" else "Show Quick Prompts") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }, onClick = { showPlusMenu = false; showQuickSuggestions = !showQuickSuggestions })
                        }
                    }

                    TextField(
                        value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("What's next?", color = MaterialTheme.colorScheme.onSurfaceVariant) }, maxLines = 4,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                    )
                    IconButton(onClick = {
                        runCatching {
                            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "What's next?")
                            })
                        }
                    }, enabled = !isLoading) { Icon(Icons.Default.Mic, "Voice") }

                    val canSend = (inputText.isNotBlank() || pendingAttachment != null) && !isLoading
                    val sendScale by animateFloatAsState(if (canSend) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "send")
                    FilledIconButton(
                        onClick = { val s = inputText.trim(); inputText = ""; onSend(s) },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.padding(end = 4.dp).size(40.dp).scale(sendScale)
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val t = rememberInfiniteTransition(label = "thinking")
    val d1 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(480), RepeatMode.Reverse), label = "d1")
    val d2 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(480, delayMillis = 140), RepeatMode.Reverse), label = "d2")
    val d3 by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(480, delayMillis = 280), RepeatMode.Reverse), label = "d3")
    Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(d1, d2, d3).forEach { a -> Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)) }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val context = LocalContext.current
    when (message.sender) {
        MessageSender.TOOL_EVENT -> {
            val icon = when (message.toolName) {
                "set_alarm" -> Icons.Default.Alarm
                "set_timer" -> Icons.Default.Timer
                "create_calendar_event" -> Icons.Default.CalendarMonth
                "compose_email" -> Icons.Default.Email
                "open_chrome_search", "web_search" -> Icons.Default.Search
                "add_task", "complete_task", "list_tasks" -> Icons.Default.CheckCircle
                "key_failover" -> Icons.Default.Key
                else -> Icons.Default.Build
            }
            FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.65f, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        MessageSender.ERROR -> Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(message.text, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
        MessageSender.USER -> Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (!message.attachmentName.isNullOrBlank()) {
                FloatingGlassSurface(shape = RoundedCornerShape(12.dp), fillAlpha = 0.65f, modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(message.attachmentName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.72f, modifier = Modifier.widthIn(max = 310.dp)) {
                Text(message.text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
        MessageSender.ASSISTANT -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(message.text, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
            if (!message.generatedImageBase64.isNullOrBlank()) {
                val bmp = remember(message.generatedImageBase64) {
                    runCatching { val b = Base64.decode(message.generatedImageBase64, Base64.DEFAULT); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
                }
                if (bmp != null) {
                    Spacer(Modifier.height(10.dp))
                    Image(bitmap = bmp, contentDescription = "Generated", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                IconButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Gemini", message.text)) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, message.text); type = "text/plain" }, "Share")) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTab(
    files: List<WorkspaceFile>,
    onWriteFile: (String, String) -> WorkspaceFile,
    onReadFile: (String) -> String,
    onResolveFile: (String) -> java.io.File,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var editingFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var previewHtml by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(78.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.68f, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Build Workspace", style = MaterialTheme.typography.titleMedium)
                    Text("${files.size} file(s) • TypeScript default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (files.any { it.relativePath.endsWith(".html") }) {
                    TextButton(onClick = {
                        val f = files.first { it.relativePath.endsWith(".html") }
                        previewHtml = runCatching { onReadFile(f.relativePath) }.getOrNull()
                    }) { Text("Run HTML") }
                }
                if (files.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workspace files yet. Ask Gemini to build an app, PDF, Excel sheet, Word doc, or slide deck!", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(files, key = { it.relativePath }) { f ->
                FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.65f, onClick = {
                    editingFile = f.relativePath to runCatching { onReadFile(f.relativePath) }.getOrDefault("")
                }, modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.relativePath, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${f.sizeBytes} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val ctx = LocalContext.current
                        IconButton(onClick = {
                            runCatching {
                                val file = onResolveFile(f.relativePath)
                                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                val share = Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Share ${f.relativePath}"
                                )
                                ctx.startActivity(share)
                            }
                        }) { Icon(Icons.Default.Share, "Share") }
                        IconButton(onClick = { onDelete(f.relativePath) }) { Icon(Icons.Default.DeleteOutline, "Delete") }
                    }
                }
            }
        }
        }
    }

    previewHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { previewHtml = null },
            title = { Text("Live WebView Preview") },
            text = {
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply { settings.javaScriptEnabled = true; loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) }
                }, modifier = Modifier.fillMaxWidth().height(380.dp))
            },
            confirmButton = { TextButton(onClick = { previewHtml = null }) { Text("Close") } }
        )
    }

    editingFile?.let { (name, body) ->
        var draft by remember(name) { mutableStateOf(body) }
        AlertDialog(
            onDismissRequest = { editingFile = null },
            title = { Text(name) },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            },
            confirmButton = { Button(onClick = { onWriteFile(name, draft); editingFile = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingFile = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TasksTab(tasks: List<TaskItem>, onToggle: (String) -> Unit, onDelete: (String) -> Unit, onClearCompleted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(78.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.68f, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tasks (${tasks.count { !it.isCompleted }} pending)", style = MaterialTheme.typography.titleMedium)
                if (tasks.any { it.isCompleted }) TextButton(onClick = onClearCompleted) { Text("Clear done") }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(tasks, key = { it.id }) { task ->
                FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.65f, modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle(task.id) })
                        Text(task.title, modifier = Modifier.weight(1f), textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                        IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Default.DeleteOutline, "Delete") }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MemoryTab(memories: List<MemoryItem>, onGenerateFromChat: () -> Unit, onAddMemory: (String, String) -> Unit, onDelete: (String) -> Unit, onClearAll: () -> Unit) {
    var newFact by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(78.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.68f, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Memory (${memories.size})", style = MaterialTheme.typography.titleMedium)
                    if (memories.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Clear") }
                }
                Button(onClick = onGenerateFromChat, modifier = Modifier.fillMaxWidth()) { Text("Generate Memory from Chat") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = newFact, onValueChange = { newFact = it }, label = { Text("Add context") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (newFact.isNotBlank()) { onAddMemory(newFact, "Personal"); newFact = "" } }) { Icon(Icons.Default.Add, "Add") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(memories, key = { it.id }) { m ->
                FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.65f, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.fact, style = MaterialTheme.typography.bodyLarge)
                            Text("${m.category} • ${m.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(m.id) }) { Icon(Icons.Default.DeleteOutline, "Delete") }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AccountLinkDialog(onLink: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Link Google Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Link your Google account to unlock Gemini Omni Studio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google Email (e.g. you@gmail.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onLink(name, email) }, enabled = email.contains("@")) { Text("Link Account") }
        }
    )
}

@Composable
private fun ModelSelectorDialog(currentModel: String, models: List<String>, onRefresh: () -> Unit, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Select Any Model")
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Fetch Models") }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Custom model ID") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { if (custom.isNotBlank()) onSelect(custom) }, enabled = custom.isNotBlank()) { Text("Use") }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                models.forEach { m ->
                    Surface(onClick = { onSelect(m) }, shape = RoundedCornerShape(10.dp), color = if (m == currentModel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m, modifier = Modifier.weight(1f), fontWeight = if (m == currentModel) FontWeight.SemiBold else FontWeight.Normal)
                            if (m == currentModel) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun SettingsDialog(
    initialKeys: List<String>,
    activeKeyIndex: Int,
    initialModel: String,
    initialCustomInstructions: String,
    initialGithubToken: String,
    initialWaContacts: Map<String, String>,
    initialMcpServers: List<McpServerConfig>,
    initialScreenControl: Boolean,
    initialToggles: Map<String, Boolean>,
    initialApprovalSkipAll: Boolean,
    initialAlwaysAllowed: Set<String>,
    onSaveApproval: (Boolean, Set<String>) -> Unit,
    linkedAccount: Pair<String, String>?,
    isAccessibilityConnected: Boolean,
    onSaveWaContacts: (Map<String, String>) -> Unit,
    onSaveMcpServers: (List<McpServerConfig>) -> Unit,
    onUnlinkAccount: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<String>, String, String, String, Boolean, Map<String, Boolean>) -> Unit
) {
    val keys = remember { mutableStateListOf<String>().apply { addAll(initialKeys) } }
    var newKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(initialModel) }
    var instr by remember { mutableStateOf(initialCustomInstructions) }
    var ghToken by remember { mutableStateOf(initialGithubToken) }
    val waContacts = remember { mutableStateMapOf<String, String>().apply { putAll(initialWaContacts) } }
    var waName by remember { mutableStateOf("") }
    var waPhone by remember { mutableStateOf("") }
    val mcpServers = remember { mutableStateListOf<McpServerConfig>().apply { addAll(initialMcpServers) } }
    var mcpName by remember { mutableStateOf("") }
    var mcpUrl by remember { mutableStateOf("") }
    var screenControl by remember { mutableStateOf(initialScreenControl) }
    var approvalSkipAll by remember { mutableStateOf(initialApprovalSkipAll) }
    val alwaysAllowed = remember { mutableStateListOf<String>().apply { addAll(initialAlwaysAllowed) } }
    val toggles = remember { mutableStateMapOf<String, Boolean>().apply { putAll(initialToggles) } }
    val context = LocalContext.current

    val clipText = remember {
        runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }.getOrDefault("")
    }
    val detectedKey = remember(clipText) { Regex("AIza[0-9A-Za-z_-]{30,}").find(clipText)?.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings & Account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                linkedAccount?.let { (n, e) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("$n ($e)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7DD3FC), modifier = Modifier.weight(1f))
                        TextButton(onClick = onUnlinkAccount) { Text("Switch") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.weight(1f)) {
                        Text("AI Studio", maxLines = 1)
                    }
                    if (detectedKey != null && !keys.contains(detectedKey)) {
                        Button(onClick = { keys.add(detectedKey) }, modifier = Modifier.weight(1f)) { Text("Import Key", maxLines = 1) }
                    }
                }
                keys.forEachIndexed { idx, k ->
                    val masked = if (k.length > 10) "${k.take(6)}•••${k.takeLast(4)}" else "••••••"
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Key #${idx + 1}: $masked" + if (idx == activeKeyIndex) " (Active)" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { keys.removeAt(idx) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it }, label = { Text("Add API Key") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { val t = newKey.trim(); if (t.isNotEmpty() && !keys.contains(t)) { keys.add(t); newKey = "" } }) { Icon(Icons.Default.Add, "Add") }
                }
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instr, onValueChange = { instr = it }, label = { Text("Custom Instructions") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ghToken, onValueChange = { ghToken = it }, label = { Text("GitHub Token (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("WhatsApp Linked Contacts", style = MaterialTheme.typography.labelLarge)
                waContacts.forEach { (name, phone) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("$name: $phone", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { waContacts.remove(name) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(15.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = waName, onValueChange = { waName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = waPhone, onValueChange = { waPhone = it }, label = { Text("+Code Phone") }, singleLine = true, modifier = Modifier.weight(1.2f))
                    IconButton(onClick = {
                        if (waName.isNotBlank() && waPhone.isNotBlank()) {
                            waContacts[waName.trim()] = waPhone.trim()
                            waName = ""; waPhone = ""
                        }
                    }) { Icon(Icons.Default.Add, "Add Contact") }
                }
                HorizontalDivider()
                Text("MCP Servers (Remote JSON-RPC)", style = MaterialTheme.typography.labelLarge)
                mcpServers.forEachIndexed { idx, srv ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${srv.name}: ${srv.url}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { mcpServers.removeAt(idx) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(15.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = mcpName, onValueChange = { mcpName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(0.9f))
                    OutlinedTextField(value = mcpUrl, onValueChange = { mcpUrl = it }, label = { Text("https://.../mcp") }, singleLine = true, modifier = Modifier.weight(1.4f))
                    IconButton(onClick = {
                        if (mcpUrl.isNotBlank()) {
                            mcpServers.add(McpServerConfig(mcpName.trim().ifEmpty { "MCP" }, mcpUrl.trim()))
                            mcpName = ""; mcpUrl = ""
                        }
                    }) { Icon(Icons.Default.Add, "Add MCP") }
                }
                HorizontalDivider()
                toggles.keys.sorted().forEach { key ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(key.removePrefix("enable"), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = toggles[key] != false, onCheckedChange = { toggles[key] = it })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Screen Control (Accessibility)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = screenControl, onCheckedChange = { screenControl = it })
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Skip ALL approvals (risky)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = approvalSkipAll, onCheckedChange = { approvalSkipAll = it })
                }
                if (!approvalSkipAll) {
                    Text("Auto-allow without asking:", style = MaterialTheme.typography.labelSmall)
                    listOf("run_shell_command", "send_sms", "send_whatsapp_message", "send_whatsapp_file", "workspace_delete_file", "http_request", "mcp_*").forEach { t ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t, style = MaterialTheme.typography.bodySmall)
                            Switch(checked = t in alwaysAllowed, onCheckedChange = { on -> if (on) { if (t !in alwaysAllowed) alwaysAllowed.add(t) } else alwaysAllowed.remove(t) })
                        }
                    }
                }
                HorizontalDivider()
                if (screenControl && !isAccessibilityConnected) {
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable in Android Settings")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalKeys = keys.toMutableList()
                newKey.trim().takeIf { it.isNotEmpty() && !finalKeys.contains(it) }?.let { finalKeys.add(it) }
                onSaveApproval(approvalSkipAll, alwaysAllowed.toSet()); onSaveWaContacts(waContacts.toMap()); onSaveMcpServers(mcpServers.toList()); onSave(finalKeys, model, instr, ghToken, screenControl, toggles.toMap())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun ArtifactsTab(
    artifacts: List<ArtifactItem>,
    onApprove: (ArtifactItem) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(78.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.68f, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Artifacts (${artifacts.size})", style = MaterialTheme.typography.titleMedium)
                    Text("Plans, walkthroughs & WebView captures", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (artifacts.isNotEmpty()) TextButton(onClick = onClearAll) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (artifacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No artifacts yet. Switch to PLAN mode to draft a plan for approval, or ask Gemini to capture a WebView screenshot.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            items(artifacts, key = { it.id }) { art ->
                FloatingGlassSurface(shape = RoundedCornerShape(18.dp), fillAlpha = 0.65f, modifier = Modifier.fillMaxWidth().animateItem()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${art.type.name}: ${art.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(art.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.DeleteOutline, "Delete", Modifier.size(16.dp)) }
                        }
                        if (art.content.isNotBlank()) {
                            Text(art.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!art.imageBase64.isNullOrBlank()) {
                            val bmp = remember(art.imageBase64) {
                                runCatching { val b = Base64.decode(art.imageBase64, Base64.DEFAULT); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(bitmap = bmp, contentDescription = art.title, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                            }
                        }
                        if (art.type == ArtifactType.PLAN && !art.isApproved) {
                            Button(onClick = { onApprove(art) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Approve Plan & Execute (Max Mode)")
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
