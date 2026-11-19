package com.example.geminidevagent.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.geminidevagent.data.WorkspaceFile
import com.example.geminidevagent.model.AttachmentData
import com.example.geminidevagent.model.ChatMessage
import com.example.geminidevagent.model.MessageSender
import com.example.geminidevagent.model.TaskItem
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Chat, 1=Build, 2=Artifacts, 3=Tasks, 4=Memory
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
    val keyEntries by viewModel.keyEntries.collectAsState()
    val activeKeyIndex by viewModel.activeKeyIndex.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isDetectingKey by viewModel.isDetectingKey.collectAsState()
    val keyDetectionStatus by viewModel.keyDetectionStatus.collectAsState()
    val linkedAccount by viewModel.linkedAccount.collectAsState()
    val customInstructions by viewModel.customInstructions.collectAsState()
    val featureToggles by viewModel.featureToggles.collectAsState()
    val screenControlEnabled by viewModel.screenControlEnabled.collectAsState()
    val voiceModeEnabled by viewModel.voiceModeEnabled.collectAsState()
    val isLightMode by viewModel.isLightMode.collectAsState()
    val pendingAttachment by viewModel.pendingAttachment.collectAsState()

    BackHandler(enabled = drawerState.isOpen || selectedTab != 0) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (selectedTab != 0) {
            selectedTab = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            OmniSidebarDrawer(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onNewChat = { viewModel.resetChat(); selectedTab = 0 },
                isLightMode = isLightMode,
                onToggleLightMode = { viewModel.toggleLightMode() },
                activeProvider = activeProvider,
                activeModel = activeModel,
                availableModels = availableModels,
                keyEntries = keyEntries,
                activeKeyIndex = activeKeyIndex,
                onSelectKey = { viewModel.selectActiveKey(it) },
                onSelectModel = { viewModel.selectModel(it) },
                onRefreshModels = { viewModel.refreshModelsFromApi() },
                agentMode = agentMode,
                onSetMode = { viewModel.setAgentMode(it) },
                voiceModeEnabled = voiceModeEnabled,
                onToggleVoiceMode = { viewModel.toggleVoiceMode() },
                workspaceCount = files.size,
                artifactCount = artifacts.size,
                pendingTaskCount = tasks.count { !it.isCompleted },
                memoryCount = memories.size,
                linkedAccount = linkedAccount,
                onOpenSettings = { showSettingsDialog = true },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
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
                    0 -> ChatTab(
                        messages = messages,
                        isLoading = isLoading,
                        streamingText = streamingText,
                        providerDisplayName = activeProvider.displayName,
                        hasApiKey = keyEntries.isNotEmpty(),
                        agentMode = agentMode,
                        onSetMode = viewModel::setAgentMode,
                        pendingAttachment = pendingAttachment,
                        onAttachUri = viewModel::attachUri,
                        onClearAttachment = viewModel::clearAttachment,
                        onOpenSettings = { showSettingsDialog = true },
                        onSend = viewModel::sendMessage
                    )
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

            // Floating Frosted-Glass Top Bar
            FloatingGlassSurface(
                shape = RoundedCornerShape(26.dp),
                fillAlpha = 0.78f,
                elevated = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_sidebar_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.refreshModelsFromApi()
                                showModelSelector = true
                            }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                            .testTag("open_model_selector_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GeminiSparkleLogo(modifier = Modifier.size(18.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> activeProvider.displayName
                                    1 -> "Build"
                                    2 -> "Artifacts"
                                    3 -> "Tasks"
                                    else -> "Memory"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Model",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (keyEntries.isEmpty()) "Tap Settings to add API Key" else "$activeModel • Key ${activeKeyIndex + 1}/${keyEntries.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (keyEntries.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleLightMode() },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = if (isLightMode) "Switch to Dark Mode" else "Switch to Light Mode",
                            tint = if (isLightMode) MaterialTheme.colorScheme.primary else LocalOmniColors.current.accentHighlight
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleVoiceMode() },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_voice_toggle")
                    ) {
                        Icon(
                            imageVector = if (voiceModeEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Voice Mode",
                            tint = if (voiceModeEnabled) LocalOmniColors.current.accentHighlight else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_settings_button")
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Settings")
                    }
                }
            }
        }
    }

    if (linkedAccount == null) {
        AccountLinkDialog(onLink = { name, email -> viewModel.linkAccount(name, email) })
    }

    if (showModelSelector) {
        ModelSelectorDialog(
            activeProvider = activeProvider,
            currentModel = activeModel,
            models = availableModels,
            keyEntries = keyEntries,
            activeKeyIndex = activeKeyIndex,
            onSelectKey = { viewModel.selectActiveKey(it) },
            onRefresh = { viewModel.refreshModelsFromApi() },
            onSelect = { viewModel.selectModel(it); showModelSelector = false },
            onDismiss = { showModelSelector = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            initialEntries = keyEntries,
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
            isLightMode = isLightMode,
            onToggleLightMode = { viewModel.toggleLightMode() },
            isDetectingKey = isDetectingKey,
            keyDetectionStatus = keyDetectionStatus,
            onDetectAndAddKey = { raw, overrideProv, onDone ->
                viewModel.detectAndAddKey(raw, overrideProv, onDone)
            },
            onClearDetectionStatus = { viewModel.clearKeyDetectionStatus() },
            onSaveApproval = { skip, allowed -> viewModel.saveApproval(skip, allowed) },
            linkedAccount = linkedAccount,
            isAccessibilityConnected = viewModel.isAccessibilityConnected(),
            onUnlinkAccount = { viewModel.unlinkAccount(); showSettingsDialog = false },
            onDismiss = { showSettingsDialog = false },
            onSaveWaContacts = { viewModel.saveWhatsAppContacts(it) },
            onSaveMcpServers = { viewModel.saveMcpServers(it) },
            onSave = { entries, activeIdx, model, instr, gh, sc, tg ->
                viewModel.saveSettings(entries, activeIdx, model, instr, gh, sc, tg)
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
    providerDisplayName: String,
    hasApiKey: Boolean,
    agentMode: AgentMode,
    onSetMode: (AgentMode) -> Unit,
    pendingAttachment: AttachmentData?,
    onAttachUri: (Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var composerMenuExpanded by remember { mutableStateOf(false) }
    var quickPromptsExpanded by remember { mutableStateOf(messages.size <= 1) }
    var modeBarExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            composerMenuExpanded = false
            onAttachUri(uri)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            composerMenuExpanded = false
            onAttachUri(uri)
        }
    }
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
            contentPadding = PaddingValues(top = 110.dp, bottom = 180.dp)
        ) {
            if (messages.size <= 1 && !hasApiKey) {
                item(key = "onboarding_banner") {
                    FloatingGlassSurface(shape = RoundedCornerShape(22.dp), elevated = true, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Add an AI Key to Start", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Supports Gemini, Claude, Kimi & ChatGPT with automatic key detection.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                                Text("Setup")
                            }
                        }
                    }
                }
            }

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .fillMaxWidth()
        ) {
            // Smoothly expandable Quick Prompts Strip
            AnimatedVisibility(
                visible = quickPromptsExpanded,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 360f)) + fadeIn(tween(200)),
                exit = shrinkVertically(spring(dampingRatio = 0.90f, stiffness = 420f)) + fadeOut(tween(150))
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        FloatingGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            fillAlpha = 0.78f,
                            onClick = { quickPromptsExpanded = false; onSend(prompt) }
                        ) {
                            Text(
                                prompt,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Smoothly expandable Tools & Attachments Menu (replaces default DropdownMenu)
            AnimatedVisibility(
                visible = composerMenuExpanded,
                enter = expandVertically(spring(dampingRatio = 0.80f, stiffness = 360f)) + fadeIn(tween(200)),
                exit = shrinkVertically(spring(dampingRatio = 0.90f, stiffness = 420f)) + fadeOut(tween(150))
            ) {
                FloatingGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    elevated = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Tools & Attachments",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComposerMenuTile(
                                icon = Icons.Default.Image,
                                label = "Image",
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            )
                            ComposerMenuTile(
                                icon = Icons.Default.AttachFile,
                                label = "File",
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f)
                            )
                            ComposerMenuTile(
                                icon = Icons.Default.AutoAwesome,
                                label = if (quickPromptsExpanded) "Hide Ideas" else "Ideas",
                                onClick = {
                                    quickPromptsExpanded = !quickPromptsExpanded
                                    composerMenuExpanded = false
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Smoothly expandable Mode Bar
            AnimatedVisibility(
                visible = modeBarExpanded,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 380f)) + fadeIn(tween(180)),
                exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) + fadeOut(tween(140))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
            }

            AnimatedVisibility(visible = pendingAttachment != null) {
                pendingAttachment?.let { att ->
                    FloatingGlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        fillAlpha = 0.85f,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (att.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.AttachFile,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(att.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            // Compact Mode + Disclaimer Pill Row (tap to expand/collapse modes)
            val modeChevron by animateFloatAsState(if (modeBarExpanded) 180f else 0f, spring(dampingRatio = 0.78f), label = "modeChevron")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { modeBarExpanded = !modeBarExpanded },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.testTag("toggle_mode_bar_button")
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${agentMode.name} Mode • $providerDisplayName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Toggle Modes",
                            modifier = Modifier.size(14.dp).rotate(modeChevron),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Floating Composer Pill
            FloatingGlassSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(30.dp),
                elevated = true
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val plusRotation by animateFloatAsState(if (composerMenuExpanded) 45f else 0f, spring(dampingRatio = 0.75f), label = "plusRot")
                    IconButton(
                        onClick = { composerMenuExpanded = !composerMenuExpanded },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("composer_plus_button")
                    ) {
                        Icon(Icons.Default.Add, "Expand Tools Menu", modifier = Modifier.rotate(plusRotation))
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f).testTag("chat_prompt_input"),
                        placeholder = { Text("Ask $providerDisplayName anything...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            runCatching {
                                voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "What's next?")
                                })
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Icon(Icons.Default.Mic, "Voice") }

                    val canSend = (inputText.isNotBlank() || pendingAttachment != null) && !isLoading
                    val sendScale by animateFloatAsState(if (canSend) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "send")
                    FilledIconButton(
                        onClick = {
                            val s = inputText.trim()
                            inputText = ""
                            composerMenuExpanded = false
                            onSend(s)
                        },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.padding(end = 4.dp).size(42.dp).scale(sendScale).testTag("send_message_button")
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ComposerMenuTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
            FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.72f, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        MessageSender.ERROR -> Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(message.text, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
        MessageSender.USER -> Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (!message.attachmentName.isNullOrBlank()) {
                FloatingGlassSurface(shape = RoundedCornerShape(12.dp), fillAlpha = 0.72f, modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(message.attachmentName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.80f, elevated = true, modifier = Modifier.widthIn(max = 310.dp)) {
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
                IconButton(
                    onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OmniStudio", message.text)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, message.text); type = "text/plain" }, "Share")) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FloatingGlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(24.dp).widthIn(max = 380.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
    var actionsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(82.dp))
        ExpandableSectionCard(
            title = "Build Workspace",
            subtitle = "${files.size} file(s) • TypeScript default",
            icon = Icons.Default.Build,
            expanded = actionsExpanded,
            onToggle = { actionsExpanded = !actionsExpanded },
            badge = "${files.size}"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (files.any { it.relativePath.endsWith(".html") }) {
                    Button(
                        onClick = {
                            val f = files.first { it.relativePath.endsWith(".html") }
                            previewHtml = runCatching { onReadFile(f.relativePath) }.getOrNull()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run HTML")
                    }
                }
                if (files.isNotEmpty()) {
                    OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f)) {
                        Text("Clear All")
                    }
                } else {
                    Text(
                        "Ask the AI in Chat to generate code, web apps, PDFs, or spreadsheets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.FolderOpen,
                title = "Workspace is Empty",
                subtitle = "Ask the assistant to build an HTML/TypeScript app, PDF, spreadsheet, Word doc, or slide deck."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(files, key = { it.relativePath }) { f ->
                    FloatingGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        fillAlpha = 0.74f,
                        onClick = { editingFile = f.relativePath to runCatching { onReadFile(f.relativePath) }.getOrDefault("") },
                        modifier = Modifier.fillMaxWidth().animateItem()
                    ) {
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
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
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
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(82.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.76f, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tasks (${tasks.count { !it.isCompleted }} pending)", style = MaterialTheme.typography.titleMedium)
                if (tasks.any { it.isCompleted }) TextButton(onClick = onClearCompleted) { Text("Clear done") }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.TaskAlt,
                title = "No Tasks Yet",
                subtitle = "Ask the assistant to add reminders, meeting follow-ups, or daily checklists."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(tasks, key = { it.id }) { task ->
                    FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.74f, modifier = Modifier.fillMaxWidth().animateItem()) {
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
    var controlsExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(82.dp))
        ExpandableSectionCard(
            title = "AI Memory",
            subtitle = "${memories.size} saved context item(s)",
            icon = Icons.Default.Memory,
            expanded = controlsExpanded,
            onToggle = { controlsExpanded = !controlsExpanded },
            badge = "${memories.size}"
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGenerateFromChat, modifier = Modifier.weight(1f)) {
                    Text("Extract from Chat", maxLines = 1)
                }
                if (memories.isNotEmpty()) {
                    OutlinedButton(onClick = onClearAll) { Text("Clear") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = newFact, onValueChange = { newFact = it }, label = { Text("Add context") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (newFact.isNotBlank()) { onAddMemory(newFact, "Personal"); newFact = "" } }) { Icon(Icons.Default.Add, "Add") }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (memories.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Memory,
                title = "No Memories Saved",
                subtitle = "Save preferences or tap Extract from Chat so your AI remembers your stack and habits."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(memories, key = { it.id }) { m ->
                    FloatingGlassSurface(shape = RoundedCornerShape(16.dp), fillAlpha = 0.74f, modifier = Modifier.fillMaxWidth()) {
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
private fun ArtifactsTab(
    artifacts: List<ArtifactItem>,
    onApprove: (ArtifactItem) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.statusBarsPadding()); Spacer(Modifier.height(82.dp))
        FloatingGlassSurface(shape = RoundedCornerShape(20.dp), fillAlpha = 0.76f, modifier = Modifier.fillMaxWidth()) {
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
            EmptyStateCard(
                icon = Icons.Default.Description,
                title = "No Artifacts Yet",
                subtitle = "Switch to PLAN mode to draft an implementation plan for approval, or capture a WebView screenshot."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(artifacts, key = { it.id }) { art ->
                    var expanded by remember(art.id) { mutableStateOf(true) }
                    ExpandableSectionCard(
                        title = art.title,
                        subtitle = art.type.name,
                        icon = Icons.Default.Description,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        badge = if (art.isApproved) "Approved" else null
                    ) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (art.type == ArtifactType.PLAN && !art.isApproved) {
                                Button(onClick = { onApprove(art) }, modifier = Modifier.weight(1f)) {
                                    Text("Approve & Execute (Max)")
                                }
                            }
                            OutlinedButton(onClick = { onDelete(art.id) }) {
                                Icon(Icons.Default.DeleteOutline, "Delete", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
