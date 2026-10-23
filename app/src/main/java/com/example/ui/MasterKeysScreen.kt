package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppConstants
import com.example.data.MasterKey
import com.example.data.SupabaseAccount

@Composable
fun MasterKeysSection(
    viewModel: AccountViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val masterKeys by viewModel.masterKeys.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newlyCreatedKey by remember { mutableStateOf<MasterKey?>(null) }
    var testingKeyForDirectConsole by remember { mutableStateOf<MasterKey?>(null) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Quick Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Master Key",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Master API Keys",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "OpenRouter Direct & Universal Router",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan
                            )
                        }
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("generate_master_key_top_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Aap ki master key se direct read aur write possible hai. Account ka string tag attach karne se system bina dosray accounts scan kiye directly usi account par query chalata hai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                if (accounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Available String Tags: ${accounts.joinToString(", ") { "#${it.effectiveTag}" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (masterKeys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Master Keys Found",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate a master key to expose a single OpenRouter endpoint across your Supabase accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create First Master Key", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                // Usage Helper Card
                item {
                    MasterKeyUsageCard(
                        sampleKey = masterKeys.firstOrNull()?.key ?: "omni_live_master_default_2026",
                        sampleTag = accounts.firstOrNull()?.effectiveTag ?: "ott-main",
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(it))
                            onShowSnackbar("cURL snippet copied to clipboard!")
                        }
                    )
                }

                items(masterKeys, key = { it.key }) { masterKey ->
                    MasterKeyItemCard(
                        masterKey = masterKey,
                        accounts = accounts,
                        onCopyTokenOnly = {
                            clipboardManager.setText(AnnotatedString(masterKey.key))
                            onShowSnackbar("🔑 Token '${masterKey.key.take(14)}...' copied!")
                        },
                        onCopyFullDetails = {
                            val fullText = viewModel.buildFullMasterKeyIntegrationText(masterKey, accounts)
                            clipboardManager.setText(AnnotatedString(fullText))
                            onShowSnackbar("📋 Complete integration guide, endpoints & routing tags copied!")
                        },
                        onOpenDirectConsole = {
                            testingKeyForDirectConsole = masterKey
                        },
                        onToggleStatus = {
                            viewModel.toggleMasterKey(masterKey.key)
                            onShowSnackbar("Master key status updated.")
                        },
                        onDeleteKey = {
                            viewModel.deleteMasterKey(masterKey.key)
                            onShowSnackbar("Master key removed.")
                        }
                    )
                }
            }
        }
    }

    // Dialog for creating a new master key
    if (showCreateDialog) {
        CreateMasterKeyDialog(
            accounts = accounts,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, selectedAccountIds ->
                viewModel.createMasterKey(name, selectedAccountIds) { created ->
                    newlyCreatedKey = created
                    onShowSnackbar("🔑 Nayi Master Key kamyabi se create ho gayi!")
                }
                showCreateDialog = false
            }
        )
    }

    // Modal showing the generated key with instant copy
    newlyCreatedKey?.let { keyObj ->
        MasterKeyCreatedDialog(
            masterKey = keyObj,
            accounts = accounts,
            onDismiss = { newlyCreatedKey = null },
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(keyObj.key))
                onShowSnackbar("Master Key Token copied to clipboard!")
            },
            onCopyFullDetails = {
                val fullText = viewModel.buildFullMasterKeyIntegrationText(keyObj, accounts)
                clipboardManager.setText(AnnotatedString(fullText))
                onShowSnackbar("📋 Complete integration guide & endpoints copied!")
            }
        )
    }

    // Direct Read/Write Console Testing Dialog
    testingKeyForDirectConsole?.let { keyObj ->
        DirectReadWriteConsoleDialog(
            masterKey = keyObj,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { testingKeyForDirectConsole = null }
        )
    }
}

@Composable
fun MasterKeyItemCard(
    masterKey: MasterKey,
    accounts: List<SupabaseAccount>,
    onCopyTokenOnly: () -> Unit,
    onCopyFullDetails: () -> Unit,
    onOpenDirectConsole: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteKey: () -> Unit
) {
    val isActive = masterKey.status == "ACTIVE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_key_card_${masterKey.key.takeLast(6)}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                if (isActive) listOf(CardBorder, AccentIndigo.copy(alpha = 0.4f))
                else listOf(CardBorder, AccentRose.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (isActive) AccentEmerald else AccentRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = masterKey.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Created: ${masterKey.created} • Limit: ${masterKey.rateLimit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = if (isActive) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            if (isActive) listOf(AccentEmerald, AccentCyan)
                            else listOf(AccentRose, AccentAmber)
                        )
                    )
                ) {
                    Text(
                        text = masterKey.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) AccentEmerald else AccentRose,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Connected Databases & String Tags Section
            Surface(
                color = DarkBg,
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(CardBorder, CardBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (masterKey.isAllAccountsConnected) Icons.Default.Public else Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Routing Destination:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = masterKey.getConnectedSummary(accounts),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (masterKey.isAllAccountsConnected) AccentCyan else AccentEmerald,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Display clickable string tags for connected accounts
                    val displayedAccounts = if (masterKey.isAllAccountsConnected) accounts 
                    else accounts.filter { it.id in masterKey.connectedAccountIds }

                    if (displayedAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayedAccounts.take(3).forEach { acc ->
                                Surface(
                                    color = AccentIndigo.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "#${acc.effectiveTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (displayedAccounts.size > 3) {
                                Text(
                                    text = "+${displayedAccounts.size - 3} more tags",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Key Display Surface with quick token copy
            Surface(
                color = DarkBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bearer Token:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = masterKey.key,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCopyTokenOnly,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("copy_key_btn_${masterKey.key.takeLast(6)}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Token Only",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row 1: Copy Full Config & Details (Comprehensive integration info)
            Button(
                onClick = onCopyFullDetails,
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("copy_full_details_btn_${masterKey.key.takeLast(6)}")
            ) {
                Icon(Icons.Default.IntegrationInstructions, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Full Details & Endpoints", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row 2: Direct Read/Write Console + Revoke + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenDirectConsole,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).testTag("test_direct_query_btn_${masterKey.key.takeLast(6)}")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentEmerald)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Direct Read/Write", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isActive) AccentAmber else AccentEmerald
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (isActive) "Revoke" else "Activate", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                IconButton(
                    onClick = onDeleteKey,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Key",
                        tint = AccentRose,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MasterKeyUsageCard(
    sampleKey: String,
    sampleTag: String,
    onCopy: (String) -> Unit
) {
    val curlCode = """# Direct Read using Account String Tag (Zero scanning of other DBs)
curl -X POST ${AppConstants.PROXY_URL} \
  -H "Authorization: Bearer $sampleKey" \
  -H "x-target-account: $sampleTag" \
  -H "Content-Type: application/json" \
  -d '{"action": "read", "table": "users"}'

# Direct Write using Account String Tag
curl -X POST ${AppConstants.PROXY_URL} \
  -H "Authorization: Bearer $sampleKey" \
  -H "x-target-account: $sampleTag" \
  -H "Content-Type: application/json" \
  -d '{"action": "write", "table": "users", "data": {"name": "Ali"}}'""".trimIndent()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(CardBorder, AccentIndigo.copy(alpha = 0.3f)))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Direct Read & Write with String Tag",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = { onCopy(curlCode) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy cURL", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Kisi bhi account par direct query ke liye 'x-target-account: <tag>' pass karein. Router sara databases scan nahi karega balka direct target DB me read/write karega.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = Color(0xFF070B12),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = curlCode,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = AccentEmerald,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun CreateMasterKeyDialog(
    accounts: List<SupabaseAccount> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, selectedAccountIds: List<String>) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var connectToAll by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedAccountIds = remember { mutableStateListOf<String>() }

    // Initialize with all account IDs
    LaunchedEffect(accounts) {
        if (selectedAccountIds.isEmpty() && accounts.isNotEmpty()) {
            selectedAccountIds.addAll(accounts.map { it.id })
        }
    }

    // Filter accounts based on user search in the popup box
    val filteredAccounts = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) accounts
        else {
            val q = searchQuery.trim().lowercase()
            accounts.filter {
                it.name.lowercase().contains(q) ||
                it.effectiveTag.lowercase().contains(q) ||
                it.projectUrl.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Generate Master Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "OpenRouter Direct & Universal Router",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Master Key ka label aur routing targets set karein. Specific account select karne ke liye search box me account ka name type karein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Key Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Key Label / Client Name") },
                    placeholder = { Text("e.g. Flutter Mobile Client, Production Bot") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("master_key_label_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = Color.LightGray
                    )
                )

                // Account Selection Section
                Surface(
                    color = DarkBg,
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(CardBorder, AccentIndigo.copy(alpha = 0.3f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Target Supabase Accounts",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (accounts.isNotEmpty()) {
                                Surface(
                                    color = AccentIndigo.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${accounts.size} Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Routing Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mode A: All Accounts
                            Surface(
                                selected = connectToAll,
                                onClick = { connectToAll = true },
                                shape = RoundedCornerShape(10.dp),
                                color = if (connectToAll) AccentIndigo.copy(alpha = 0.25f) else Color.Transparent,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.linearGradient(
                                        if (connectToAll) listOf(AccentIndigo, AccentCyan) else listOf(CardBorder, CardBorder)
                                    )
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (connectToAll) AccentCyan else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "All Accounts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectToAll) Color.White else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Universal Load Balancer",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (connectToAll) AccentCyan else Color.DarkGray,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // Mode B: Specific Accounts
                            Surface(
                                selected = !connectToAll,
                                onClick = { connectToAll = false },
                                shape = RoundedCornerShape(10.dp),
                                color = if (!connectToAll) AccentIndigo.copy(alpha = 0.25f) else Color.Transparent,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.linearGradient(
                                        if (!connectToAll) listOf(AccentIndigo, AccentCyan) else listOf(CardBorder, CardBorder)
                                    )
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = if (!connectToAll) AccentCyan else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Specific Accounts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!connectToAll) Color.White else Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Search & Choose DBs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!connectToAll) AccentCyan else Color.DarkGray,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        // If Specific Mode is active: show the Search Box and filtered accounts
                        AnimatedVisibility(visible = !connectToAll) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (accounts.isEmpty()) {
                                    Surface(
                                        color = AccentAmber.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Filhal koi Supabase account connect nahi hai. Key create ho jayegi aur accounts add hote hi connect ho sakti hai.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                } else {
                                    // Search Box inside the popup dialog
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search account by name, tag or URL...", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentCyan,
                                            unfocusedBorderColor = CardBorder,
                                            focusedContainerColor = Color(0xFF070B12),
                                            unfocusedContainerColor = Color(0xFF070B12)
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("account_search_in_dialog")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (searchQuery.isBlank()) "Select Accounts (${selectedAccountIds.size}/${accounts.size}):"
                                                   else "Found ${filteredAccounts.size} account(s):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                        TextButton(
                                            onClick = {
                                                val allFilteredSelected = filteredAccounts.all { it.id in selectedAccountIds }
                                                if (allFilteredSelected) {
                                                    filteredAccounts.forEach { selectedAccountIds.remove(it.id) }
                                                } else {
                                                    filteredAccounts.forEach { 
                                                        if (it.id !in selectedAccountIds) selectedAccountIds.add(it.id) 
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            val allFilteredSelected = filteredAccounts.isNotEmpty() && filteredAccounts.all { it.id in selectedAccountIds }
                                            Text(
                                                text = if (allFilteredSelected) "Clear Filtered" else "Select All Filtered",
                                                color = AccentCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (filteredAccounts.isEmpty()) {
                                        Surface(
                                            color = Color(0xFF0B101B),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Koi account '$searchQuery' se match nahi hua.",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    filteredAccounts.forEach { acc ->
                                        val isChecked = selectedAccountIds.contains(acc.id)
                                        Surface(
                                            onClick = {
                                                if (isChecked) {
                                                    selectedAccountIds.remove(acc.id)
                                                } else {
                                                    selectedAccountIds.add(acc.id)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isChecked) AccentIndigo.copy(alpha = 0.15f) else Color.Transparent,
                                            border = CardDefaults.outlinedCardBorder().copy(
                                                brush = Brush.linearGradient(
                                                    if (isChecked) listOf(AccentEmerald, AccentCyan) else listOf(CardBorder, CardBorder)
                                                )
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        if (checked) selectedAccountIds.add(acc.id) else selectedAccountIds.remove(acc.id)
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = AccentCyan,
                                                        checkmarkColor = DarkBg,
                                                        uncheckedColor = Color.Gray
                                                    ),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = acc.name,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            color = AccentIndigo.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "#${acc.effectiveTag}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = AccentCyan,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = acc.projectUrl.removePrefix("https://").removePrefix("http://"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.LightGray,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Security & Token Format Info
                Surface(
                    color = AccentIndigo.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Token: omni_live_[128-bit hex] • Rate Limit: UNLIMITED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSelectedIds = if (connectToAll) {
                        emptyList() // Empty denotes all accounts
                    } else {
                        selectedAccountIds.toList()
                    }
                    onConfirm(label, finalSelectedIds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_generate_master_key_btn")
            ) {
                Text("Generate Key", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun MasterKeyCreatedDialog(
    masterKey: MasterKey,
    accounts: List<SupabaseAccount> = emptyList(),
    onDismiss: () -> Unit,
    onCopyToken: () -> Unit,
    onCopyFullDetails: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Key Created Successfully!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aap ki nayi Master API Key ready hai. Is key ko copy karein aur client apps ya scripts mein use karein:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                // Key display box
                Surface(
                    color = DarkBg,
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AccentCyan, AccentIndigo))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = masterKey.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = masterKey.key,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = AccentCyan
                        )
                    }
                }

                // Routing target badge
                Surface(
                    color = AccentEmerald.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (masterKey.isAllAccountsConnected) Icons.Default.Public else Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connected: ${masterKey.getConnectedSummary(accounts)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick copy buttons inside modal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyToken,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Token", fontSize = 11.sp, color = AccentCyan)
                    }

                    Button(
                        onClick = onCopyFullDetails,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.IntegrationInstructions, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy All Config", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold, color = DarkBg)
            }
        },
        dismissButton = null
    )
}

@Composable
fun DirectReadWriteConsoleDialog(
    masterKey: MasterKey,
    accounts: List<SupabaseAccount>,
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    var selectedTag by remember { 
        mutableStateOf(accounts.firstOrNull()?.effectiveTag ?: "all") 
    }
    var action by remember { mutableStateOf("READ") } // "READ" or "WRITE"
    var tableName by remember { mutableStateOf("movies") }
    var writePayload by remember { mutableStateOf("""{"title": "Interstellar", "genre": "Sci-Fi"}""") }
    var isExecuting by remember { mutableStateOf(false) }
    var queryResult by remember { mutableStateOf<AccountViewModel.DirectQueryResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Direct Read / Write Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Route directly via string tag (Zero multi-scan)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aap master key se kisi specific account ka string tag bhej kar direct usi account me read ya write test kar sakte hain:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                // Account String Tag Selector
                Text(text = "Target Account String Tag:", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                if (accounts.isEmpty()) {
                    Text(text = "No accounts configured yet.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accounts.forEach { acc ->
                            val isSel = selectedTag.equals(acc.effectiveTag, ignoreCase = true)
                            Surface(
                                selected = isSel,
                                onClick = { selectedTag = acc.effectiveTag },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) AccentIndigo else Color(0xFF070B12),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.linearGradient(if (isSel) listOf(AccentCyan, AccentEmerald) else listOf(CardBorder, CardBorder))
                                )
                            ) {
                                Text(
                                    text = "#${acc.effectiveTag}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSel) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Toggle: READ vs WRITE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        selected = action == "READ",
                        onClick = { action = "READ" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (action == "READ") AccentCyan.copy(alpha = 0.2f) else DarkBg,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(if (action == "READ") listOf(AccentCyan, AccentCyan) else listOf(CardBorder, CardBorder))
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "READ (GET)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (action == "READ") AccentCyan else Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Surface(
                        selected = action == "WRITE",
                        onClick = { action = "WRITE" },
                        shape = RoundedCornerShape(8.dp),
                        color = if (action == "WRITE") AccentEmerald.copy(alpha = 0.2f) else DarkBg,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(if (action == "WRITE") listOf(AccentEmerald, AccentEmerald) else listOf(CardBorder, CardBorder))
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "WRITE (POST)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (action == "WRITE") AccentEmerald else Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Table Name
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("Table Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Write Payload if action is WRITE
                if (action == "WRITE") {
                    OutlinedTextField(
                        value = writePayload,
                        onValueChange = { writePayload = it },
                        label = { Text("Write Payload (JSON)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }

                // Execute Button
                Button(
                    onClick = {
                        isExecuting = true
                        queryResult = null
                        viewModel.executeDirectRoutedOperation(
                            masterKey = masterKey,
                            targetAccountTag = selectedTag,
                            action = action,
                            tableName = tableName,
                            payloadJson = if (action == "WRITE") writePayload else null
                        ) { result ->
                            isExecuting = false
                            queryResult = result
                        }
                    },
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("execute_direct_query_action_btn")
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBg)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Direct Routing...", color = DarkBg, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = DarkBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Direct $action", color = DarkBg, fontWeight = FontWeight.Bold)
                    }
                }

                // Result Box
                queryResult?.let { res ->
                    Surface(
                        color = if (res.success) AccentEmerald.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(
                                if (res.success) listOf(AccentEmerald, AccentCyan) else listOf(AccentRose, AccentAmber)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (res.success) AccentEmerald else AccentRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = res.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Latency: ${res.latencyMs}ms | Target DB: ${res.targetAccountName} (#${res.targetTag})",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontSize = 10.sp
                            )
                            if (res.responseData.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = res.responseData,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close")
            }
        },
        dismissButton = null
    )
}
