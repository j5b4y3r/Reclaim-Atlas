package com.example.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.BlockCategory
import com.example.data.model.BlockRule
import com.example.data.model.RuleType
import com.example.domain.engine.AccountabilityHelper
import com.example.domain.engine.LockedActionTarget
import com.example.ui.components.EmailAlreadySentBadge
import com.example.ui.components.InfoHelpIconButton
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.PrimaryGreenPill
import com.example.ui.theme.SubtleText
import com.example.ui.theme.reclaimTextFieldColors
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Apps", "Websites", "Keywords")

    val appsList by viewModel.installedAppRules.collectAsState()
    val websitesList by viewModel.domainRules.collectAsState()
    val keywordsList by viewModel.keywordRules.collectAsState()

    val accountabilityEnabled by viewModel.accountabilityEnabled.collectAsState()
    val activePartnerRequests by viewModel.activePartnerRequests.collectAsState()

    var showAddWebsiteDialog by remember { mutableStateOf(false) }
    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var showAddAppSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkText
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Blocklist",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.4).sp
                    )
                }

                InfoHelpIconButton(
                    title = "How Blocklist Locking Works",
                    stepsOrBullets = listOf(
                        "Adding Rules is Always Open: Tap the '+' button anytime to block an app, website, or keyword.",
                        "Individual Rule Unlocks: When Accountability Mode is locked, turning off or deleting a rule requires waiting for your daily window OR requesting a 1-time 6-digit code from your partner.",
                        "1 Code = 1 Rule: A partner code for a specific rule only unlocks or deletes that single rule.",
                        "Anti-Spam Protection: Only 1 email can be sent per rule until it is unlocked and enabled again."
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTabIndex) {
                        0 -> showAddAppSheet = true
                        1 -> showAddWebsiteDialog = true
                        2 -> showAddKeywordDialog = true
                    }
                },
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_rule_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = AppBackground,
                contentColor = PrimaryGreen
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) PrimaryGreen else MutedText
                            )
                        }
                    )
                }
            }

            if (accountabilityEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFBEB))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Partner Accountability Active: Modifying or deleting rules requires a 1-time partner email code",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentList = when (selectedTabIndex) {
                    0 -> appsList
                    1 -> websitesList
                    else -> keywordsList
                }

                if (currentList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No items added yet. Adding items is always allowed.", color = SubtleText, fontSize = 14.sp)
                        }
                    }
                }

                items(currentList, key = { it.id }) { rule ->
                    val disableKey = "RULE_DISABLE_${rule.id}"
                    val deleteKey = "RULE_DELETE_${rule.id}"
                    val ruleLabel = rule.name.ifBlank { rule.target }
                    val hasPendingDisable = activePartnerRequests.containsKey(disableKey)
                    val hasPendingDelete = activePartnerRequests.containsKey(deleteKey)

                    BlockRuleItem(
                        rule = rule,
                        hasPendingEmailCode = hasPendingDisable || hasPendingDelete,
                        onOpenPendingCode = {
                            if (hasPendingDelete) {
                                viewModel.triggerLockedItemDialog(
                                    LockedActionTarget(
                                        actionKey = deleteKey,
                                        actionTitle = "Delete Block: $ruleLabel",
                                        targetType = "DELETE_RULE",
                                        ruleId = rule.id
                                    )
                                )
                            } else {
                                viewModel.triggerLockedItemDialog(
                                    LockedActionTarget(
                                        actionKey = disableKey,
                                        actionTitle = "Disable Block: $ruleLabel",
                                        targetType = "DISABLE_RULE",
                                        ruleId = rule.id
                                    )
                                )
                            }
                        },
                        onToggleEnabled = { isEnabled ->
                            viewModel.toggleRule(rule.id, isEnabled)
                        },
                        onDelete = {
                            viewModel.deleteRule(rule.id)
                        }
                    )
                }
            }
        }
    }

    if (showAddWebsiteDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddWebsiteDialog = false },
            onAdd = { url ->
                viewModel.addCustomDomain(url)
                showAddWebsiteDialog = false
            }
        )
    }

    if (showAddKeywordDialog) {
        AddKeywordDialog(
            onDismiss = { showAddKeywordDialog = false },
            onAdd = { kw ->
                viewModel.addKeywordRule(kw)
                showAddKeywordDialog = false
            }
        )
    }

    if (showAddAppSheet) {
        InstalledAppsBottomSheet(
            onDismiss = { showAddAppSheet = false },
            onAppSelected = { pkg, name ->
                viewModel.addAppRule(pkg, name)
                showAddAppSheet = false
            }
        )
    }
}

@Composable
fun BlockRuleItem(
    rule: BlockRule,
    hasPendingEmailCode: Boolean = false,
    onOpenPendingCode: () -> Unit = {},
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (rule.enabled) PrimaryGreenLight else Color(0xFFF3F4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (rule.type) {
                            RuleType.DOMAIN -> Icons.Default.Language
                            RuleType.APP -> Icons.Default.Apps
                            RuleType.KEYWORD -> Icons.Default.TextFields
                            else -> Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = if (rule.enabled) PrimaryGreen else SubtleText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = rule.name.ifBlank { rule.target },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                    Text(
                        text = rule.target,
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    if (hasPendingEmailCode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EmailAlreadySentBadge(onClick = onOpenPendingCode)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = SubtleText,
                        uncheckedTrackColor = PrimaryGreenPill
                    )
                )

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Website", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Domain or URL") },
                    placeholder = { Text("e.g. reddit.com", color = SubtleText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )
                Text(
                    text = "Adding domains is ALWAYS allowed at any time to keep you focused.",
                    fontSize = 11.sp,
                    color = PrimaryGreen
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onAdd(urlText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = urlText.isNotBlank()
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        }
    )
}

@Composable
fun AddKeywordDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var keywordText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Keyword / Search", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("Trigger Keyword") },
                    placeholder = { Text("e.g. casino, reels", color = SubtleText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )
                Text(
                    text = "Adding keywords will block browser, YouTube, and Facebook searches and content containing this text.",
                    fontSize = 11.sp,
                    color = MutedText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keywordText.isNotBlank()) {
                        onAdd(keywordText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                enabled = keywordText.isNotBlank()
            ) {
                Text("Add Keyword")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsBottomSheet(
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, appName: String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val installedApps = remember {
        val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        list.filter { app ->
            // Filter out purely system packages without launcher intent
            pm.getLaunchIntentForPackage(app.packageName) != null && app.packageName != context.packageName
        }.map { app ->
            val label = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm)
            Triple(app.packageName, label, icon)
        }.sortedBy { it.second.lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Select App to Block",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed apps") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = reclaimTextFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps) { (pkg, name, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(pkg, name) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = icon.toBitmap(width = 96, height = 96).asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Text(
                                text = pkg,
                                fontSize = 11.sp,
                                color = SubtleText
                            )
                        }
                    }
                }
            }
        }
    }
}
