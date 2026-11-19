package com.example.geminidevagent.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geminidevagent.data.AiProvider
import com.example.geminidevagent.data.ApiKeyDetector
import com.example.geminidevagent.data.ApiKeyEntry
import com.example.geminidevagent.tools.McpServerConfig

@Composable
internal fun AccountLinkDialog(onLink: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to Omni Studio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Link your profile to personalize your workspace and persistent memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("link_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (e.g. you@gmail.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("link_email_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLink(name, email) },
                enabled = email.contains("@"),
                modifier = Modifier.testTag("link_account_button")
            ) { Text("Continue") }
        }
    )
}

@Composable
internal fun ModelSelectorDialog(
    activeProvider: AiProvider,
    currentModel: String,
    models: List<String>,
    keyEntries: List<ApiKeyEntry>,
    activeKeyIndex: Int,
    onSelectKey: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val omni = LocalOmniColors.current
    var custom by remember { mutableStateOf("") }
    var keysExpanded by remember { mutableStateOf(keyEntries.size > 1) }
    var customExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Models • ${activeProvider.displayName}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (keyEntries.isEmpty()) "No key active • Default ${activeProvider.displayName} list"
                        else "Active key: ${activeProvider.displayName} #${activeKeyIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = omni.accentHighlight
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh_models_button")) {
                    Icon(Icons.Default.Refresh, "Fetch Models")
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (keyEntries.size > 1) {
                    ExpandableSectionCard(
                        title = "Switch Provider Key",
                        subtitle = "${activeProvider.displayName} #${activeKeyIndex + 1} active",
                        icon = Icons.Default.Key,
                        expanded = keysExpanded,
                        onToggle = { keysExpanded = !keysExpanded },
                        badge = "${keyEntries.size}"
                    ) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            keyEntries.forEachIndexed { idx, entry ->
                                FilterChip(
                                    selected = idx == activeKeyIndex,
                                    onClick = { onSelectKey(idx) },
                                    label = { Text("${entry.provider.displayName} #${idx + 1}") }
                                )
                            }
                        }
                    }
                }

                ExpandableSectionCard(
                    title = "Custom Model ID",
                    subtitle = "Enter any ${activeProvider.displayName} model string",
                    icon = Icons.Default.Tune,
                    expanded = customExpanded,
                    onToggle = { customExpanded = !customExpanded }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            label = { Text("Model ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("custom_model_input")
                        )
                        Button(
                            onClick = { if (custom.isNotBlank()) onSelect(custom) },
                            enabled = custom.isNotBlank(),
                            modifier = Modifier.testTag("use_custom_model_button")
                        ) { Text("Use") }
                    }
                }

                models.forEach { m ->
                    val selected = m == currentModel
                    Surface(
                        onClick = { onSelect(m) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) Icon(Icons.Default.Check, "Selected", Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
internal fun SettingsDialog(
    initialEntries: List<ApiKeyEntry>,
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
    isLightMode: Boolean,
    onToggleLightMode: () -> Unit,
    isDetectingKey: Boolean,
    keyDetectionStatus: String?,
    onDetectAndAddKey: (String, AiProvider?, (ApiKeyEntry, List<String>) -> Unit) -> Unit,
    onClearDetectionStatus: () -> Unit,
    onSaveApproval: (Boolean, Set<String>) -> Unit,
    linkedAccount: Pair<String, String>?,
    isAccessibilityConnected: Boolean,
    onSaveWaContacts: (Map<String, String>) -> Unit,
    onSaveMcpServers: (List<McpServerConfig>) -> Unit,
    onUnlinkAccount: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<ApiKeyEntry>, Int, String, String, String, Boolean, Map<String, Boolean>) -> Unit
) {
    val omni = LocalOmniColors.current
    val entries = remember { mutableStateListOf<ApiKeyEntry>().apply { addAll(initialEntries) } }
    var selectedKeyIdx by remember { mutableIntStateOf(activeKeyIndex) }
    var newKey by remember { mutableStateOf("") }
    var providerOverride by remember { mutableStateOf<AiProvider?>(null) }
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

    // Expandable accordion section states
    var keysExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var modelInstrExpanded by remember { mutableStateOf(false) }
    var modulesExpanded by remember { mutableStateOf(false) }
    var safetyExpanded by remember { mutableStateOf(false) }
    var contactsExpanded by remember { mutableStateOf(false) }
    var mcpExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(initialEntries, activeKeyIndex) {
        entries.clear()
        entries.addAll(initialEntries)
        if (entries.isNotEmpty()) {
            selectedKeyIdx = activeKeyIndex.coerceIn(0, entries.lastIndex)
            val activeProv = entries[selectedKeyIdx].provider
            if (!ApiKeyDetector.isModelCompatible(model, activeProv)) {
                model = activeProv.defaultModel
            }
        }
    }

    val clipText = remember {
        runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }.getOrDefault("")
    }
    val clipboardKey = remember(clipText) { ApiKeyDetector.extractKeyFromText(clipText) }
    val clipboardProvider = remember(clipboardKey) { clipboardKey?.let { ApiKeyDetector.detectStatic(it) } }

    val typedTrimmed = newKey.trim()
    val typedStatic = remember(typedTrimmed) { ApiKeyDetector.detectStatic(typedTrimmed) }
    val typedAmbiguous = remember(typedTrimmed) { ApiKeyDetector.isAmbiguousSkKey(typedTrimmed) }

    AlertDialog(
        onDismissRequest = {
            onClearDetectionStatus()
            onDismiss()
        },
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Omni Settings", style = MaterialTheme.typography.titleLarge)
                FilledTonalIconButton(onClick = onToggleLightMode, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = if (isLightMode) "Dark Mode" else "Light Mode",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Expandable API Keys & Key Detection
                ExpandableSectionCard(
                    title = "AI Keys & Auto-Detection",
                    subtitle = if (entries.isEmpty()) "Add Gemini, Claude, Kimi or ChatGPT key" else "${entries.size} key(s) • ${entries.getOrNull(selectedKeyIdx)?.provider?.displayName ?: "Gemini"}",
                    icon = Icons.Default.Key,
                    expanded = keysExpanded,
                    onToggle = { keysExpanded = !keysExpanded },
                    badge = "${entries.size}",
                    testTag = "settings_keys_section"
                ) {
                    Text("Get API Keys", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        AiProvider.entries.forEach { p ->
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyPortalUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            ) { Text(p.displayName, maxLines = 1) }
                        }
                    }

                    if (clipboardKey != null && entries.none { it.key == clipboardKey }) {
                        val badge = clipboardProvider?.displayName ?: "sk- (Auto-Detect)"
                        Button(
                            onClick = {
                                onDetectAndAddKey(clipboardKey, null) { added, models ->
                                    model = models.firstOrNull { it == added.provider.defaultModel } ?: added.provider.defaultModel
                                }
                            },
                            enabled = !isDetectingKey,
                            modifier = Modifier.fillMaxWidth().testTag("import_clipboard_key_button")
                        ) {
                            Icon(Icons.Default.Key, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import Clipboard Key ($badge)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    entries.forEachIndexed { idx, entry ->
                        val masked = if (entry.key.length > 12) "${entry.key.take(7)}•••${entry.key.takeLast(4)}" else "••••••"
                        val isSelected = idx == selectedKeyIdx
                        Surface(
                            onClick = {
                                selectedKeyIdx = idx
                                if (!ApiKeyDetector.isModelCompatible(model, entry.provider)) {
                                    model = entry.provider.defaultModel
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${entry.provider.displayName} #${idx + 1}" + if (isSelected) " (Active)" else "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else omni.accentHighlight
                                    )
                                    Text(masked, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                }
                                TextButton(
                                    onClick = {
                                        val all = AiProvider.entries
                                        val next = all[(all.indexOf(entry.provider) + 1) % all.size]
                                        entries[idx] = entry.copy(provider = next, baseUrlOverride = null)
                                        if (idx == selectedKeyIdx && !ApiKeyDetector.isModelCompatible(model, next)) {
                                            model = next.defaultModel
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) { Text("Type", style = MaterialTheme.typography.labelSmall) }
                                IconButton(
                                    onClick = {
                                        entries.removeAt(idx)
                                        selectedKeyIdx = if (entries.isNotEmpty()) selectedKeyIdx.coerceIn(0, entries.lastIndex) else 0
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) { Icon(Icons.Default.DeleteOutline, "Delete key", Modifier.size(16.dp)) }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            label = { Text("Paste Gemini / Claude / Kimi / ChatGPT Key") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("add_api_key_input")
                        )
                        IconButton(
                            onClick = {
                                val t = newKey.trim()
                                if (t.isNotEmpty()) {
                                    onDetectAndAddKey(t, providerOverride) { added, models ->
                                        newKey = ""
                                        providerOverride = null
                                        model = models.firstOrNull { it == added.provider.defaultModel } ?: added.provider.defaultModel
                                    }
                                }
                            },
                            enabled = typedTrimmed.isNotEmpty() && !isDetectingKey,
                            modifier = Modifier.testTag("detect_add_key_button")
                        ) {
                            if (isDetectingKey) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Add, "Detect & Add Key")
                        }
                    }

                    if (typedTrimmed.isNotEmpty()) {
                        val hint = when {
                            providerOverride != null -> "Selected provider: ${providerOverride!!.displayName}"
                            typedStatic != null -> "Detected format: ${typedStatic.displayName}"
                            typedAmbiguous -> "Detected sk- key (ChatGPT or Kimi) — tap + to auto-verify"
                            else -> "Unknown prefix — choose provider or tap + to detect"
                        }
                        Text(hint, style = MaterialTheme.typography.labelSmall, color = omni.accentHighlight)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(selected = providerOverride == null, onClick = { providerOverride = null }, label = { Text("Auto") })
                            AiProvider.entries.forEach { p ->
                                FilterChip(selected = providerOverride == p, onClick = { providerOverride = p }, label = { Text(p.displayName) })
                            }
                        }
                    }

                    if (!keyDetectionStatus.isNullOrBlank()) {
                        Text(keyDetectionStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                // 2. Account & Theme
                ExpandableSectionCard(
                    title = "Account & Appearance",
                    subtitle = "${if (isLightMode) "Light Mode" else "Dark Mode"} • ${linkedAccount?.first ?: "Not linked"}",
                    icon = Icons.Default.AccountCircle,
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    testTag = "settings_appearance_section"
                ) {
                    Row(
                        Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Light Mode", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isLightMode, onCheckedChange = { onToggleLightMode() })
                    }
                    linkedAccount?.let { (n, e) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("$n ($e)", style = MaterialTheme.typography.bodySmall, color = omni.accentHighlight, modifier = Modifier.weight(1f))
                            TextButton(onClick = onUnlinkAccount) { Text("Switch") }
                        }
                    }
                }

                // 3. Model & Custom Instructions
                ExpandableSectionCard(
                    title = "Model & Instructions",
                    subtitle = model,
                    icon = Icons.Default.Tune,
                    expanded = modelInstrExpanded,
                    onToggle = { modelInstrExpanded = !modelInstrExpanded },
                    testTag = "settings_model_section"
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("model_id_input")
                    )
                    OutlinedTextField(
                        value = instr,
                        onValueChange = { instr = it },
                        label = { Text("Custom Instructions") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ghToken,
                        onValueChange = { ghToken = it },
                        label = { Text("GitHub Token (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Modules & Screen Control
                ExpandableSectionCard(
                    title = "Modules & Screen Control",
                    subtitle = "${toggles.count { it.value }}/${toggles.size} active",
                    icon = Icons.Default.Build,
                    expanded = modulesExpanded,
                    onToggle = { modulesExpanded = !modulesExpanded },
                    testTag = "settings_modules_section"
                ) {
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
                    if (screenControl && !isAccessibilityConnected) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable in Android Settings") }
                    }
                }

                // 5. Safety & Approvals
                ExpandableSectionCard(
                    title = "Safety & Approvals",
                    subtitle = if (approvalSkipAll) "Auto-approve all tools" else "${alwaysAllowed.size} auto-allowed",
                    icon = Icons.Default.Security,
                    expanded = safetyExpanded,
                    onToggle = { safetyExpanded = !safetyExpanded },
                    testTag = "settings_safety_section"
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Skip ALL approvals (risky)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = approvalSkipAll, onCheckedChange = { approvalSkipAll = it })
                    }
                    if (!approvalSkipAll) {
                        Text("Auto-allow without asking:", style = MaterialTheme.typography.labelSmall)
                        listOf("run_shell_command", "send_sms", "send_whatsapp_message", "send_whatsapp_file", "workspace_delete_file", "http_request", "mcp_*").forEach { t ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                                Switch(
                                    checked = t in alwaysAllowed,
                                    onCheckedChange = { on -> if (on) { if (t !in alwaysAllowed) alwaysAllowed.add(t) } else alwaysAllowed.remove(t) }
                                )
                            }
                        }
                    }
                }

                // 6. WhatsApp Linked Contacts
                ExpandableSectionCard(
                    title = "WhatsApp Contacts",
                    subtitle = "${waContacts.size} linked",
                    icon = Icons.Default.Contacts,
                    expanded = contactsExpanded,
                    onToggle = { contactsExpanded = !contactsExpanded },
                    badge = "${waContacts.size}",
                    testTag = "settings_contacts_section"
                ) {
                    waContacts.forEach { (name, phone) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("$name: $phone", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { waContacts.remove(name) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, "Remove contact", Modifier.size(15.dp))
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
                }

                // 7. MCP Servers
                ExpandableSectionCard(
                    title = "MCP Servers",
                    subtitle = "${mcpServers.size} configured",
                    icon = Icons.Default.Hub,
                    expanded = mcpExpanded,
                    onToggle = { mcpExpanded = !mcpExpanded },
                    badge = "${mcpServers.size}",
                    testTag = "settings_mcp_section"
                ) {
                    mcpServers.forEachIndexed { idx, srv ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${srv.name}: ${srv.url}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { mcpServers.removeAt(idx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, "Remove MCP", Modifier.size(15.dp))
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalEntries = entries.toMutableList()
                    var finalIdx = selectedKeyIdx
                    val typed = newKey.trim()
                    if (typed.isNotEmpty() && finalEntries.none { it.key == typed }) {
                        val prov = providerOverride ?: ApiKeyDetector.inferProviderOrFallback(typed)
                        finalEntries.add(ApiKeyEntry(typed, prov))
                        finalIdx = finalEntries.lastIndex
                    }
                    onClearDetectionStatus()
                    onSaveApproval(approvalSkipAll, alwaysAllowed.toSet())
                    onSaveWaContacts(waContacts.toMap())
                    onSaveMcpServers(mcpServers.toList())
                    onSave(finalEntries, finalIdx, model, instr, ghToken, screenControl, toggles.toMap())
                },
                modifier = Modifier.testTag("save_settings_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearDetectionStatus()
                onDismiss()
            }) { Text("Cancel") }
        }
    )
}
