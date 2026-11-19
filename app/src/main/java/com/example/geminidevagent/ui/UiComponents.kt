package com.example.geminidevagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geminidevagent.data.AgentMode
import com.example.geminidevagent.data.AiProvider
import com.example.geminidevagent.data.ApiKeyEntry

@Composable
internal fun GeminiSparkleLogo(modifier: Modifier = Modifier) {
    val omni = LocalOmniColors.current
    val startColor = if (omni.isDark) Color(0xFF7DD3FC) else MaterialTheme.colorScheme.primary
    val endColor = if (omni.isDark) Color.White else MaterialTheme.colorScheme.secondary
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
                colors = listOf(startColor, endColor),
                start = Offset(w, 0f),
                end = Offset(0f, h)
            )
        )
    }
}

@Composable
internal fun CleanGlassCanvas(content: @Composable BoxScope.() -> Unit) {
    val omni = LocalOmniColors.current
    val drift = rememberInfiniteTransition(label = "glassDrift")
    val shift by drift.animateFloat(
        initialValue = -0.03f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(tween(10000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift"
    )
    val baseColor by animateColorAsState(omni.canvasBase, tween(320), label = "canvasBase")
    val primaryGlowAlpha = if (omni.isDark) 0.18f else 0.12f
    val secondaryGlowAlpha = if (omni.isDark) 0.12f else 0.09f

    Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topGlow = Offset(size.width * (0.22f + shift), size.height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(omni.auroraPrimary.copy(alpha = primaryGlowAlpha), Color.Transparent),
                    topGlow,
                    size.width * 0.78f
                ),
                center = topGlow,
                radius = size.width * 0.78f
            )
            val botGlow = Offset(size.width * (0.82f - shift), size.height * 0.90f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(omni.auroraSecondary.copy(alpha = secondaryGlowAlpha), Color.Transparent),
                    botGlow,
                    size.width * 0.70f
                ),
                center = botGlow,
                radius = size.width * 0.70f
            )
        }
        content()
    }
}

@Composable
internal fun FloatingGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    fillAlpha: Float = 0.80f,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val omni = LocalOmniColors.current
    val targetFill = (if (elevated) omni.glassElevatedFill else omni.glassFill).copy(
        alpha = if (omni.isDark) fillAlpha else (fillAlpha + 0.12f).coerceAtMost(0.96f)
    )
    val fill by animateColorAsState(targetFill, tween(250), label = "glassFill")
    val rim = Brush.linearGradient(listOf(omni.glassRimStart, omni.glassRimEnd))
    val elevation = if (omni.isDark) 10.dp else 4.dp

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = fill,
            border = BorderStroke(1.dp, rim),
            shadowElevation = elevation,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fill,
            border = BorderStroke(1.dp, rim),
            shadowElevation = elevation,
            content = content
        )
    }
}

@Composable
internal fun ExpandableSectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val omni = LocalOmniColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "chevronRotation"
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            if (omni.isDark) omni.glassElevatedFill.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.96f)
        } else {
            if (omni.isDark) omni.glassFill.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.84f)
        },
        animationSpec = tween(220),
        label = "expandCardBg"
    )

    Surface(
        modifier = modifier.fillMaxWidth().let { m -> if (testTag != null) m.testTag(testTag) else m },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(omni.glassRimStart, omni.glassRimEnd))
        ),
        shadowElevation = if (omni.isDark) 4.dp else 2.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f)
                ) + fadeIn(tween(220)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f)
                ) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = omni.subtleDivider)
                    content()
                }
            }
        }
    }
}

@Composable
internal fun OmniSidebarDrawer(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onNewChat: () -> Unit,
    isLightMode: Boolean,
    onToggleLightMode: () -> Unit,
    activeProvider: AiProvider,
    activeModel: String,
    availableModels: List<String>,
    keyEntries: List<ApiKeyEntry>,
    activeKeyIndex: Int,
    onSelectKey: (Int) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    agentMode: AgentMode,
    onSetMode: (AgentMode) -> Unit,
    voiceModeEnabled: Boolean,
    onToggleVoiceMode: () -> Unit,
    workspaceCount: Int,
    artifactCount: Int,
    pendingTaskCount: Int,
    memoryCount: Int,
    linkedAccount: Pair<String, String>?,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val omni = LocalOmniColors.current
    var navExpanded by remember { mutableStateOf(true) }
    var keysExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var controlsExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        drawerContainerColor = omni.drawerFill,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.widthIn(min = 290.dp, max = 336.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sidebar Brand Header + Theme Toggle
            FloatingGlassSurface(
                shape = RoundedCornerShape(22.dp),
                elevated = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            GeminiSparkleLogo(Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Omni Studio", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${activeProvider.displayName} • $activeModel",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = omni.accentHighlight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onToggleLightMode,
                            modifier = Modifier.size(42.dp).testTag("sidebar_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = if (isLightMode) "Switch to Dark Mode" else "Switch to Light Mode",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onNewChat()
                                onCloseDrawer()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("sidebar_new_chat_button")
                        ) {
                            Icon(Icons.Default.AddComment, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Chat")
                        }
                        OutlinedButton(
                            onClick = {
                                onCloseDrawer()
                                onOpenSettings()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp).testTag("sidebar_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, "Settings", Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Expandable Navigation Menu
            ExpandableSectionCard(
                title = "Workspaces",
                subtitle = "Chat, code, artifacts, tasks & memory",
                icon = Icons.Default.AutoAwesome,
                expanded = navExpanded,
                onToggle = { navExpanded = !navExpanded },
                badge = "5",
                testTag = "sidebar_workspaces_section"
            ) {
                val navItems = listOf(
                    Triple(0, "AI Chat", Pair(Icons.Filled.Psychology, Icons.Outlined.Psychology)) to null,
                    Triple(1, "Build Workspace", Pair(Icons.Filled.Build, Icons.Outlined.Build)) to workspaceCount.takeIf { it > 0 }?.toString(),
                    Triple(2, "Artifacts", Pair(Icons.Filled.Description, Icons.Outlined.Description)) to artifactCount.takeIf { it > 0 }?.toString(),
                    Triple(3, "Tasks", Pair(Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle)) to pendingTaskCount.takeIf { it > 0 }?.toString(),
                    Triple(4, "Memory", Pair(Icons.Filled.Memory, Icons.Outlined.Memory)) to memoryCount.takeIf { it > 0 }?.toString()
                )
                navItems.forEach { (meta, badgeCount) ->
                    val (idx, label, icons) = meta
                    val selected = selectedTab == idx
                    Surface(
                        onClick = {
                            onSelectTab(idx)
                            onCloseDrawer()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) icons.first else icons.second,
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (badgeCount != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = badgeCount,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Provider & Keys Switcher
            ExpandableSectionCard(
                title = "Providers & API Keys",
                subtitle = if (keyEntries.isEmpty()) "No keys added yet" else "Active: ${activeProvider.displayName} #${activeKeyIndex + 1}",
                icon = Icons.Default.Key,
                expanded = keysExpanded,
                onToggle = { keysExpanded = !keysExpanded },
                badge = "${keyEntries.size}",
                testTag = "sidebar_keys_section"
            ) {
                if (keyEntries.isEmpty()) {
                    Text(
                        "Add a Gemini, Claude, Kimi, or ChatGPT key to start chatting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    keyEntries.forEachIndexed { idx, entry ->
                        val isActive = idx == activeKeyIndex
                        val masked = if (entry.key.length > 10) "${entry.key.take(6)}•••${entry.key.takeLast(4)}" else "••••••"
                        Surface(
                            onClick = { onSelectKey(idx) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${entry.provider.displayName} Key #${idx + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        masked,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isActive) {
                                    Icon(Icons.Default.Check, "Active", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        onCloseDrawer()
                        onOpenSettings()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Keys & Providers")
                }
            }

            // Expandable Quick Model Picker
            ExpandableSectionCard(
                title = "Quick Models",
                subtitle = activeModel,
                icon = Icons.Default.Tune,
                expanded = modelsExpanded,
                onToggle = { modelsExpanded = !modelsExpanded },
                badge = activeProvider.displayName,
                testTag = "sidebar_models_section"
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${activeProvider.displayName} models",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRefreshModels) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                availableModels.take(8).forEach { m ->
                    val selected = m == activeModel
                    Surface(
                        onClick = { onSelectModel(m) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selected) Icon(Icons.Default.Check, "Selected", Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Expandable Agent & Appearance Controls
            ExpandableSectionCard(
                title = "Controls & Theme",
                subtitle = "${agentMode.name} • ${if (isLightMode) "Light" else "Dark"} Mode",
                icon = Icons.Default.Settings,
                expanded = controlsExpanded,
                onToggle = { controlsExpanded = !controlsExpanded },
                testTag = "sidebar_controls_section"
            ) {
                Text("Agent Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    AgentMode.entries.forEach { m ->
                        FilterChip(
                            selected = agentMode == m,
                            onClick = { onSetMode(m) },
                            label = { Text(m.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (isLightMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, Modifier.size(18.dp))
                        Text("Light Mode", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = isLightMode, onCheckedChange = { onToggleLightMode() })
                }
                Row(
                    Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VolumeUp, null, Modifier.size(18.dp))
                        Text("Voice Replies (TTS)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = voiceModeEnabled, onCheckedChange = { onToggleVoiceMode() })
                }
            }

            linkedAccount?.let { (name, email) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(name, style = MaterialTheme.typography.labelLarge)
                        Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
