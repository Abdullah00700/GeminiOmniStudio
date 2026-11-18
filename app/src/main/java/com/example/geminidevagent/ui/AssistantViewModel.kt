package com.example.geminidevagent.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geminidevagent.accessibility.AssistantAccessibilityService
import com.example.geminidevagent.data.AgentMode
import com.example.geminidevagent.data.ArtifactItem
import com.example.geminidevagent.data.ArtifactRepository
import com.example.geminidevagent.data.GeminiApiClient
import com.example.geminidevagent.data.MemoryItem
import com.example.geminidevagent.data.MemoryRepository
import com.example.geminidevagent.data.SettingsRepository
import com.example.geminidevagent.data.TaskRepository
import com.example.geminidevagent.data.WorkspaceFile
import com.example.geminidevagent.data.WorkspaceRepository
import com.example.geminidevagent.model.AttachmentData
import com.example.geminidevagent.model.ChatMessage
import com.example.geminidevagent.model.MessageSender
import com.example.geminidevagent.model.TaskItem
import com.example.geminidevagent.tools.AndroidTaskExecutor
import com.example.geminidevagent.tools.McpClient
import com.example.geminidevagent.tools.McpServerConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    val workspaceRepository = WorkspaceRepository(application)
    val taskRepository = TaskRepository(application)
    val memoryRepository = MemoryRepository(application)
    val artifactRepository = ArtifactRepository(application)
    private val executor = AndroidTaskExecutor(
        application, workspaceRepository, taskRepository, memoryRepository, settingsRepository, artifactRepository
    )
    private val geminiClient = GeminiApiClient(executor, settingsRepository, memoryRepository)

    data class PendingConfirm(val tool: String, val details: String, val decision: CompletableDeferred<Boolean>)

    private val _pendingConfirm = MutableStateFlow<PendingConfirm?>(null)
    val pendingConfirm: StateFlow<PendingConfirm?> = _pendingConfirm.asStateFlow()
    private val confirmMutex = Mutex()

    init {
        geminiClient.confirmHandler = { tool, args ->
            confirmMutex.withLock {
                val d = CompletableDeferred<Boolean>()
                _pendingConfirm.value = PendingConfirm(tool, args.toString(2).take(1200), d)
                try { d.await() } finally { _pendingConfirm.value = null }
            }
        }
    }

    fun resolveConfirm(approved: Boolean) {
        _pendingConfirm.value?.decision?.complete(approved)
    }

    val workspaceFiles: StateFlow<List<WorkspaceFile>> = workspaceRepository.files
    val tasks: StateFlow<List<TaskItem>> = taskRepository.tasks
    val memories: StateFlow<List<MemoryItem>> = memoryRepository.memories
    val artifacts: StateFlow<List<ArtifactItem>> = artifactRepository.artifacts

    private val _agentMode = MutableStateFlow(AgentMode.FAST)
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    private val _apiKeys = MutableStateFlow(settingsRepository.getApiKeys())
    val apiKeys: StateFlow<List<String>> = _apiKeys.asStateFlow()

    private val _activeKeyIndex = MutableStateFlow(settingsRepository.getActiveKeyIndex())
    val activeKeyIndex: StateFlow<Int> = _activeKeyIndex.asStateFlow()

    private val _activeModel = MutableStateFlow(settingsRepository.getModelName())
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _availableModels = MutableStateFlow(
        listOf(
            "gemini-4-hydrogen",
            "gemini-4-carbon",
            "gemini-4-argon",
            "gemini-flash-latest",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-image"
        )
    )
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _linkedAccount = MutableStateFlow(settingsRepository.getLinkedAccount())
    val linkedAccount: StateFlow<Pair<String, String>?> = _linkedAccount.asStateFlow()

    private val _customInstructions = MutableStateFlow(settingsRepository.getCustomInstructions())
    val customInstructions: StateFlow<String> = _customInstructions.asStateFlow()

    private val _featureToggles = MutableStateFlow(settingsRepository.getFeatureToggles())
    val featureToggles: StateFlow<Map<String, Boolean>> = _featureToggles.asStateFlow()

    private val _screenControlEnabled = MutableStateFlow(settingsRepository.isScreenControlEnabled())
    val screenControlEnabled: StateFlow<Boolean> = _screenControlEnabled.asStateFlow()

    private val _voiceModeEnabled = MutableStateFlow(settingsRepository.isVoiceModeEnabled())
    val voiceModeEnabled: StateFlow<Boolean> = _voiceModeEnabled.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<AttachmentData?>(null)
    val pendingAttachment: StateFlow<AttachmentData?> = _pendingAttachment.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Hi! I'm Gemini Omni Studio on Android. Ask me anything, control your phone, manage tasks, or build apps in the Build workspace—with TypeScript by default and automatic API-key failover."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private var ttsReady = false
    private val tts = TextToSpeech(application) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
        }
    }.apply {
        language = Locale.getDefault()
    }

    override fun onCleared() {
        tts.stop()
        tts.shutdown()
        super.onCleared()
    }

    fun isAccessibilityConnected(): Boolean = AssistantAccessibilityService.isConnected()
    fun getApprovalSkipAll(): Boolean = settingsRepository.getApprovalSkipAll()
    fun getAlwaysAllowedTools(): Set<String> = settingsRepository.getAlwaysAllowedTools()
    fun saveApproval(skipAll: Boolean, allowed: Set<String>) = settingsRepository.saveApproval(skipAll, allowed)
    fun alwaysAllow(tool: String) {
        settingsRepository.addAlwaysAllowed(if (tool.startsWith("mcp_")) "mcp_*" else tool)
        resolveConfirm(true)
    }

    fun getGithubToken(): String = settingsRepository.getGithubToken()
    fun getWhatsAppContacts(): Map<String, String> = settingsRepository.getWhatsAppContacts()
    fun saveWhatsAppContacts(c: Map<String, String>) = settingsRepository.saveWhatsAppContacts(c)
    fun getMcpServers(): List<McpServerConfig> = settingsRepository.getMcpServers()
    fun saveMcpServers(s: List<McpServerConfig>) {
        settingsRepository.saveMcpServers(s)
        viewModelScope.launch(Dispatchers.IO) { McpClient.discoverAll(s) }
    }

    fun setAgentMode(mode: AgentMode) { _agentMode.value = mode }

    fun approvePlanAndExecute(artifact: ArtifactItem) {
        artifactRepository.approveArtifact(artifact.id)
        _agentMode.value = AgentMode.MAX
        sendMessage("Plan '${artifact.title}' is approved. Execute it completely now.")
    }

    fun toggleVoiceMode() {
        val next = !_voiceModeEnabled.value
        settingsRepository.setVoiceModeEnabled(next)
        _voiceModeEnabled.value = next
        if (!next) tts.stop()
    }

    fun linkAccount(name: String, email: String) {
        if (email.isBlank() || !email.contains("@")) return
        settingsRepository.saveLinkedAccount(name, email)
        _linkedAccount.value = settingsRepository.getLinkedAccount()
    }

    fun unlinkAccount() {
        settingsRepository.clearLinkedAccount()
        _linkedAccount.value = null
    }

    fun selectModel(modelName: String) {
        val cleaned = modelName.trim().ifEmpty { SettingsRepository.DEFAULT_MODEL }
        settingsRepository.saveModelName(cleaned)
        _activeModel.value = settingsRepository.getModelName()
    }

    fun refreshModelsFromApi() {
        viewModelScope.launch {
            geminiClient.fetchAvailableModels().onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    _availableModels.value = (fetched + _availableModels.value).distinct()
                    _activeKeyIndex.value = settingsRepository.getActiveKeyIndex()
                }
            }
        }
    }

    fun attachUri(uri: Uri) {
        viewModelScope.launch {
            val attachment = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                var fileName = "attachment"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) fileName = cursor.getString(idx) ?: fileName
                }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                AttachmentData(name = fileName, mimeType = mimeType, base64 = Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            if (attachment != null) _pendingAttachment.value = attachment
        }
    }

    fun clearAttachment() { _pendingAttachment.value = null }

    fun saveSettings(
        keys: List<String>,
        modelName: String,
        customInstr: String,
        githubToken: String,
        screenControl: Boolean,
        toggles: Map<String, Boolean>
    ) {
        settingsRepository.saveApiKeys(keys)
        settingsRepository.saveModelName(modelName)
        settingsRepository.saveCustomInstructions(customInstr)
        settingsRepository.saveGithubToken(githubToken)
        settingsRepository.setScreenControlEnabled(screenControl)
        settingsRepository.saveFeatureToggles(toggles)

        _apiKeys.value = settingsRepository.getApiKeys()
        _activeKeyIndex.value = settingsRepository.getActiveKeyIndex()
        _activeModel.value = settingsRepository.getModelName()
        _customInstructions.value = settingsRepository.getCustomInstructions()
        _screenControlEnabled.value = screenControl
        _featureToggles.value = toggles
    }

    fun generateMemoryFromChat() {
        sendMessage("Review our conversation and call save_memory for key preferences, stack choices, or facts to remember. Then summarize what you saved.")
    }

    fun sendMessage(prompt: String) {
        if (_linkedAccount.value == null) return
        val trimmed = prompt.trim()
        val attachment = _pendingAttachment.value
        if ((trimmed.isEmpty() && attachment == null) || _isLoading.value) return

        val displayPrompt = trimmed.ifEmpty { "Analyze attached file: ${attachment?.name}" }
        _pendingAttachment.value = null

        _messages.value = _messages.value + ChatMessage(
            sender = MessageSender.USER,
            text = displayPrompt,
            attachmentName = attachment?.name
        )
        _isLoading.value = true

        viewModelScope.launch {
            val result = geminiClient.sendUserPrompt(
                modelName = settingsRepository.getModelName(),
                userText = displayPrompt,
                attachment = attachment,
                mode = _agentMode.value,
                screenControlToggleOn = settingsRepository.isScreenControlEnabled(),
                onStreamDelta = { _streamingText.value = it }
            )

            result.onSuccess { step ->
                _activeKeyIndex.value = step.activeKeyIndex
                val extras = mutableListOf<ChatMessage>()
                step.keySwapMessage?.let {
                    extras.add(ChatMessage(sender = MessageSender.TOOL_EVENT, text = it, toolName = "key_failover"))
                }
                step.executedTools.forEach { (toolName, summary) ->
                    extras.add(ChatMessage(sender = MessageSender.TOOL_EVENT, text = summary, toolName = toolName))
                }
                extras.add(ChatMessage(sender = MessageSender.ASSISTANT, text = step.finalText, generatedImageBase64 = step.generatedImageBase64))
                _messages.value = _messages.value + extras

                if (_voiceModeEnabled.value && ttsReady) {
                    tts.speak(step.finalText.take(500), TextToSpeech.QUEUE_FLUSH, null, "gemini_reply")
                }
            }.onFailure { error ->
                _messages.value = _messages.value + ChatMessage(sender = MessageSender.ERROR, text = error.message ?: "API Error")
            }
            _streamingText.value = ""
            _isLoading.value = false
        }
    }

    fun resetChat() {
        geminiClient.clearHistory()
        _pendingAttachment.value = null
        _messages.value = listOf(ChatMessage(sender = MessageSender.ASSISTANT, text = "Started a fresh chat. What's next?"))
    }
}
