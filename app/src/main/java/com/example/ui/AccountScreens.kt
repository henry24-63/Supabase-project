package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppConstants
import com.example.data.SupabaseAccount

// Color palette
val DarkBg = Color(0xFF0B0F19)
val CardBg = Color(0xFF131B2E)
val CardBorder = Color(0xFF1E293B)
val AccentCyan = Color(0xFF06B6D4)
val AccentIndigo = Color(0xFF6366F1)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFEF4444)
val AccentAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    viewModel: AccountViewModel,
    onUnlocked: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val authError by viewModel.authError.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("lock_screen_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Lock",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Security Verification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter Master Access Code to unlock the OTT Router & Database Gateway.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Access Code") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (viewModel.verifyPassword(password)) onUnlocked()
                    }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle code visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("access_code_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                if (authError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = authError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (viewModel.verifyPassword(password)) {
                            onUnlocked()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("unlock_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text(
                        text = "Unlock Dashboard",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDashboardScreen(
    viewModel: AccountViewModel,
    onNavigateToAddAccount: () -> Unit,
    onLockClicked: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val masterKeys by viewModel.masterKeys.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var accountToDelete by remember { mutableStateOf<com.example.data.SupabaseAccount?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var newlyCreatedKey by remember { mutableStateOf<com.example.data.MasterKey?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var accountForRead by remember { mutableStateOf<com.example.data.SupabaseAccount?>(null) }
    var accountForWrite by remember { mutableStateOf<com.example.data.SupabaseAccount?>(null) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = "Infinity DB",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OTT Router",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (selectedTab == 0) "Unlimited Supabase Manager" else "Universal Master Bearer Keys",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshAccounts()
                        snackbarMessage = "🔄 Syncing accounts from Master Supabase backend..."
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Accounts",
                            tint = AccentCyan
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(AppConstants.PROXY_URL))
                        snackbarMessage = "Proxy Endpoint copied to clipboard!"
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy Proxy Endpoint",
                            tint = Color.LightGray
                        )
                    }
                    IconButton(onClick = onLockClicked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Dashboard",
                            tint = Color.LightGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        BadgedBox(badge = {
                            if (accounts.isNotEmpty()) {
                                Badge(containerColor = AccentIndigo) { Text("${accounts.size}") }
                            }
                        }) {
                            Icon(Icons.Default.Storage, contentDescription = "Accounts")
                        }
                    },
                    label = { Text("Accounts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = AccentCyan,
                        indicatorColor = AccentIndigo,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (masterKeys.isNotEmpty()) {
                                Badge(containerColor = AccentEmerald) { Text("${masterKeys.size}") }
                            }
                        }) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Master Keys")
                        }
                    },
                    label = { Text("Master Keys") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = AccentCyan,
                        indicatorColor = AccentIndigo,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToAddAccount,
                    containerColor = AccentIndigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_account_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Connect Account")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Account", fontWeight = FontWeight.Bold)
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showCreateKeyDialog = true },
                    containerColor = AccentIndigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("create_master_key_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Generate Master Key")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Master Key", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (snackbarMessage != null) {
                Surface(
                    color = AccentEmerald.copy(alpha = 0.2f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentEmerald, AccentCyan))),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(snackbarMessage ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        }
                        IconButton(
                            onClick = { snackbarMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // DEDICATED ACCOUNTS PAGE / TAB
                val filteredAccounts = remember(searchQuery, selectedFilter, accounts) {
                    accounts.filter { acc ->
                        val matchesFilter = when (selectedFilter) {
                            "ACTIVE" -> acc.isActive
                            "DEACTIVATED" -> !acc.isActive
                            else -> true
                        }
                        val q = searchQuery.trim().lowercase()
                        val matchesQuery = if (q.isBlank()) true else {
                            acc.name.lowercase().contains(q) ||
                            acc.effectiveTag.lowercase().contains(q) ||
                            acc.projectUrl.lowercase().contains(q) ||
                            acc.apiKey.lowercase().contains(q)
                        }
                        matchesFilter && matchesQuery
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Stats Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(title = "Connected DBs", value = "${accounts.size}", subtitle = "${accounts.count { it.isActive }} Active", modifier = Modifier.weight(1f))
                        StatCard(title = "Keep-Alive", value = "24h Pulse", subtitle = "Anti-Pause", modifier = Modifier.weight(1f))
                        StatCard(title = "Proxy Router", value = "Online", subtitle = "Zero-Scan", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Live Search Box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accounts_search_bar"),
                        placeholder = { Text("Search by name, #tag, or URL...", fontSize = 13.sp, color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg,
                            unfocusedContainerColor = CardBg,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Quick Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("All (${accounts.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentIndigo,
                                selectedLabelColor = Color.White,
                                containerColor = CardBg,
                                labelColor = Color.LightGray
                            )
                        )
                        val activeCount = accounts.count { it.isActive }
                        FilterChip(
                            selected = selectedFilter == "ACTIVE",
                            onClick = { selectedFilter = "ACTIVE" },
                            label = { Text("Active ($activeCount)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentEmerald,
                                selectedLabelColor = Color.White,
                                containerColor = CardBg,
                                labelColor = Color.LightGray
                            )
                        )
                        val deactivatedCount = accounts.count { !it.isActive }
                        FilterChip(
                            selected = selectedFilter == "DEACTIVATED",
                            onClick = { selectedFilter = "DEACTIVATED" },
                            label = { Text("Deactivated ($deactivatedCount)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentAmber,
                                selectedLabelColor = Color.White,
                                containerColor = CardBg,
                                labelColor = Color.LightGray
                            )
                        )
                    }

                    if (accounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = "No accounts",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Supabase Accounts Connected Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Connect multiple Supabase databases. All accounts sync live across the app and website.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onNavigateToAddAccount,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                                ) {
                                    Text("Connect First Account")
                                }
                            }
                        }
                    } else if (filteredAccounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = "No search matches",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Accounts Match Search",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No accounts found matching '$searchQuery'. Try checking spelling or clear search filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { searchQuery = ""; selectedFilter = "ALL" },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBg)
                                ) {
                                    Text("Clear Search Filter", color = AccentCyan)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filteredAccounts, key = { it.id }) { account ->
                                AccountItemCard(
                                    account = account,
                                    onDelete = { accountToDelete = account },
                                    onToggleStatus = {
                                        viewModel.toggleAccountStatus(account.id)
                                        snackbarMessage = if (account.isActive) "Account '${account.name}' deactivated" else "Account '${account.name}' activated!"
                                    },
                                    onDirectRead = { accountForRead = account },
                                    onDirectWrite = { accountForWrite = account },
                                    onCopyMasterKey = {
                                        val details = viewModel.buildAccountSpecificMasterKeyDetails(account, masterKeys.firstOrNull())
                                        clipboardManager.setText(AnnotatedString(details))
                                        snackbarMessage = "🔑 Master Key & integration config for '${account.name}' copied!"
                                    },
                                    onCopyTag = { tag ->
                                        clipboardManager.setText(AnnotatedString(tag))
                                        snackbarMessage = "String Tag '$tag' copied to clipboard!"
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // MASTER KEYS TAB CONTENT
                MasterKeysSection(
                    viewModel = viewModel,
                    onShowSnackbar = { snackbarMessage = it }
                )
            }
        }
    }

    if (showCreateKeyDialog) {
        CreateMasterKeyDialog(
            accounts = accounts,
            onDismiss = { showCreateKeyDialog = false },
            onConfirm = { name, selectedIds ->
                viewModel.createMasterKey(name, selectedIds) { created ->
                    newlyCreatedKey = created
                    snackbarMessage = "🔑 Nayi Master Key create ho gayi!"
                }
                showCreateKeyDialog = false
            }
        )
    }

    newlyCreatedKey?.let { keyObj ->
        MasterKeyCreatedDialog(
            masterKey = keyObj,
            accounts = accounts,
            onDismiss = { newlyCreatedKey = null },
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(keyObj.key))
                snackbarMessage = "Master Key Token copied to clipboard!"
            },
            onCopyFullDetails = {
                val full = viewModel.buildFullMasterKeyIntegrationText(keyObj, accounts)
                clipboardManager.setText(AnnotatedString(full))
                snackbarMessage = "📋 Complete integration guide & endpoints copied to clipboard!"
            }
        )
    }

    // Direct Account Read Dialog
    accountForRead?.let { acc ->
        DirectAccountReadDialog(
            account = acc,
            viewModel = viewModel,
            onDismiss = { accountForRead = null }
        )
    }

    // Direct Account Write Dialog
    accountForWrite?.let { acc ->
        DirectAccountWriteDialog(
            account = acc,
            viewModel = viewModel,
            onDismiss = { accountForWrite = null }
        )
    }

    // Account Deletion Confirmation Dialog
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            containerColor = CardBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRose)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account?", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Aap '${acc.name}' (#${acc.effectiveTag}) account ko delete karne lage hain.",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Yeh account app ki local storage aur cloud database dono se permanently delete ho jayega.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val accId = acc.id
                        val accName = acc.name
                        val accUrl = acc.projectUrl
                        viewModel.removeAccount(accId, accName, accUrl)
                        accountToDelete = null
                        snackbarMessage = "🗑️ Account '$accName' permanently delete ho gaya!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_account_dialog_btn")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CardBorder, CardBorder)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = AccentEmerald, fontSize = 9.sp)
        }
    }
}

@Composable
fun AccountItemCard(
    account: SupabaseAccount,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit,
    onDirectRead: () -> Unit,
    onDirectWrite: () -> Unit,
    onCopyMasterKey: () -> Unit,
    onCopyTag: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id.takeLast(6)}"),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                if (account.isActive) listOf(CardBorder, AccentIndigo.copy(alpha = 0.4f))
                else listOf(CardBorder, AccentAmber.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Icon, Name & URL, Status Badge
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
                            .background(
                                if (account.isActive) AccentEmerald.copy(alpha = 0.15f)
                                else AccentAmber.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (account.isActive) AccentEmerald else AccentAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = account.projectUrl,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge Chip
                Surface(
                    color = if (account.isActive) AccentEmerald.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            if (account.isActive) listOf(AccentEmerald, AccentCyan)
                            else listOf(AccentAmber, Color.Gray)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (account.isActive) AccentEmerald else AccentAmber)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (account.isActive) "ACTIVE" else "DEACTIVATED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (account.isActive) AccentEmerald else AccentAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct Routing String Tag Surface
            Surface(
                onClick = { onCopyTag(account.effectiveTag) },
                color = AccentIndigo.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan.copy(alpha = 0.4f)))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = "String Tag:", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${account.effectiveTag}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Tap to copy", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Tag",
                            tint = AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key info bar
            Surface(
                color = DarkBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "API Key:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    val masked = if (account.apiKey.length > 12) {
                        account.apiKey.take(6) + "••••••••" + account.apiKey.takeLast(4)
                    } else "••••••"
                    Text(text = masked, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = AccentCyan)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ================= DEDICATED ACTION BUTTONS FOR THIS ACCOUNT =================
            // Row 1: Direct Read, Direct Write, Copy Master Key
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Direct Read Button
                FilledTonalButton(
                    onClick = onDirectRead,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("direct_read_btn_${account.id.takeLast(6)}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentIndigo.copy(alpha = 0.2f),
                        contentColor = AccentCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Direct Write Button
                FilledTonalButton(
                    onClick = onDirectWrite,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("direct_write_btn_${account.id.takeLast(6)}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentEmerald.copy(alpha = 0.2f),
                        contentColor = AccentEmerald
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Write", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Copy Master Key Button
                Button(
                    onClick = onCopyMasterKey,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .testTag("copy_master_key_btn_${account.id.takeLast(6)}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Master Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Status Toggle & Permanent Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status Toggle Button
                OutlinedButton(
                    onClick = onToggleStatus,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("toggle_status_btn_${account.id.takeLast(6)}"),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            if (account.isActive) listOf(AccentAmber, AccentAmber)
                            else listOf(AccentEmerald, AccentCyan)
                        )
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (account.isActive) AccentAmber else AccentEmerald
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = if (account.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (account.isActive) "Deactivate" else "Activate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Delete Account Button
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("delete_account_btn_${account.id.takeLast(6)}"),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AccentRose, AccentRose))),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DirectAccountReadDialog(
    account: SupabaseAccount,
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    var endpointPath by remember { mutableStateOf("rest/v1/") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Direct Account Read", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("${account.name} (#${account.effectiveTag})", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                }
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
                    text = "Reads directly from this account without scanning other databases.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = endpointPath,
                    onValueChange = { endpointPath = it },
                    label = { Text("Path / Table") },
                    placeholder = { Text("e.g. auth/v1/settings or rest/v1/users") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = { endpointPath = "auth/v1/settings" },
                        label = { Text("Auth Settings", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { endpointPath = "rest/v1/" },
                        label = { Text("Root Schema", fontSize = 10.sp) }
                    )
                }

                if (isExecuting) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(24.dp))
                    }
                }

                if (resultText != null) {
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (latencyMs != null) {
                                Text("Response Latency: ${latencyMs}ms", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = resultText ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExecuting = true
                    viewModel.executeDirectAccountRead(account, endpointPath) { res ->
                        isExecuting = false
                        latencyMs = res.latencyMs
                        resultText = if (res.responseData.isNotBlank()) res.responseData else res.message
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                enabled = !isExecuting
            ) {
                Text("Execute Read", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray)
            }
        }
    )
}

@Composable
fun DirectAccountWriteDialog(
    account: SupabaseAccount,
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    var tableName by remember { mutableStateOf("notes") }
    var recordJson by remember { mutableStateOf("""{"title": "Test Record", "content": "Created via OTT Router"}""") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = AccentEmerald)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Direct Account Write", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text("${account.name} (#${account.effectiveTag})", style = MaterialTheme.typography.labelSmall, color = AccentEmerald)
                }
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
                    text = "Inserts a record directly into this specific Supabase account using its string tag.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("Table Name") },
                    placeholder = { Text("e.g. notes or users") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = recordJson,
                    onValueChange = { recordJson = it },
                    label = { Text("JSON Record") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )

                if (isExecuting) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentEmerald, modifier = Modifier.size(24.dp))
                    }
                }

                if (resultText != null) {
                    Surface(
                        color = DarkBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (latencyMs != null) {
                                Text("Execution Latency: ${latencyMs}ms", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = resultText ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExecuting = true
                    viewModel.executeDirectAccountWrite(account, tableName, recordJson) { res ->
                        isExecuting = false
                        latencyMs = res.latencyMs
                        resultText = if (res.responseData.isNotBlank()) res.responseData else res.message
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                enabled = !isExecuting
            ) {
                Text("Execute Write", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    viewModel: AccountViewModel,
    onNavigateBack: () -> Unit
) {
    var rawData by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var routingTag by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    val testStatus by viewModel.testStatus.collectAsState()

    BackHandler(onBack = onNavigateBack)

    DisposableEffect(Unit) {
        onDispose { viewModel.resetTestStatus() }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Connect Supabase Account", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help Guide",
                            tint = if (showHelp) AccentCyan else Color.LightGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Help / Instructions Card (Collapsible)
            if (showHelp) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentCyan, AccentIndigo)))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to find your Supabase credentials",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "1. Open supabase.com and go to your Project.\n" +
                                   "2. Navigate to Project Settings ⚙️ -> API.\n" +
                                   "3. Project URL: Copy the URL (e.g. https://xyz.supabase.co)\n" +
                                   "4. Project API Keys: Copy the 'anon public' key or 'publishable' key (starts with sb_publishable_... or eyJhbGciOi...).\n" +
                                   "5. Note: Both HTTP and HTTPS URLs are accepted and automatically tested.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Smart Auto-Paste Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🪄", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Auto-Paste Detector",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Paste your raw text from Supabase CLI or project dashboard (with project URL, publishable key, password). The detector extracts fields automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rawData,
                        onValueChange = { rawData = it },
                        placeholder = { Text("Paste raw project details or dashboard URL here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (rawData.isNotBlank()) {
                                // Smart regex parser
                                val dashMatch = Regex("supabase\\.com/dashboard/project/([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE).find(rawData)
                                val urlMatch = Regex("https?://[a-zA-Z0-9.-]+\\.supabase\\.co", RegexOption.IGNORE_CASE).find(rawData)
                                val pubKeyMatch = Regex("sb_publishable_[a-zA-Z0-9_-]+", RegexOption.IGNORE_CASE).find(rawData)
                                val secKeyMatch = Regex("sb_secret_[a-zA-Z0-9_-]+", RegexOption.IGNORE_CASE).find(rawData)
                                val jwtKeyMatch = Regex("eyJhbGciOi[a-zA-Z0-9._-]+").find(rawData)
                                val nameMatch = Regex("(?:project\\s*name\\s*[:\\s]*)(.+)", RegexOption.IGNORE_CASE).find(rawData)
                                val passMatch = Regex("(?:Database\\s*password\\s*[:\\s]*)(.+)", RegexOption.IGNORE_CASE).find(rawData)
                                    ?: Regex("(?:password\\s*[:\\s]*)(.+)", RegexOption.IGNORE_CASE).find(rawData)

                                if (dashMatch != null) {
                                    url = "https://${dashMatch.groupValues[1]}.supabase.co"
                                } else if (urlMatch != null) {
                                    url = urlMatch.value.trim()
                                }

                                if (pubKeyMatch != null) {
                                    key = pubKeyMatch.value.trim()
                                } else if (secKeyMatch != null) {
                                    key = secKeyMatch.value.trim()
                                } else if (jwtKeyMatch != null) {
                                    key = jwtKeyMatch.value.trim()
                                }

                                if (nameMatch != null) {
                                    name = nameMatch.groupValues[1].lines().first().trim()
                                } else if (url.isNotBlank()) {
                                    val sub = url.replace("https://", "").replace("http://", "").split(".").first()
                                    name = "Project $sub"
                                }

                                if (passMatch != null) pass = passMatch.groupValues[1].lines().first().trim()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Extract Details", color = DarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Input Fields Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CardBorder, CardBorder)))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Account Credentials",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project Name / Alias") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Project URL (http:// or https://)") },
                        singleLine = true,
                        placeholder = { Text("https://xyz.supabase.co") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API Key (Publishable / Anon or Service)") },
                        singleLine = true,
                        placeholder = { Text("sb_publishable_... or eyJhbGciOi...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Database Password (Optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = routingTag,
                        onValueChange = { routingTag = it },
                        label = { Text("Direct Routing String Tag (Optional)") },
                        singleLine = true,
                        placeholder = { Text("e.g. ott-main, users-db (Default: from name)") },
                        modifier = Modifier.fillMaxWidth().testTag("account_routing_tag_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Status / Error Banner
            AnimatedVisibility(visible = testStatus !is TestStatus.Idle) {
                when (val status = testStatus) {
                    is TestStatus.Testing -> {
                        Surface(
                            color = AccentIndigo.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentCyan)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Smart Testing Supabase Connection...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Running Auth & PostgREST handshake probes...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                    is TestStatus.Success -> {
                        Surface(
                            color = AccentEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentEmerald, AccentCyan))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = status.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Key Verified: ${status.keyType} | Latency: ${status.latencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentEmerald
                                )
                            }
                        }
                    }
                    is TestStatus.Error -> {
                        Surface(
                            color = AccentRose.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AccentRose, AccentAmber))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRose)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = "Validation Notice", fontWeight = FontWeight.Bold, color = AccentRose, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = status.error, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                if (status.hint != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "💡 Hint: ${status.hint}",
                                        color = AccentAmber,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.saveAccountDirectly(name, url, key, pass, routingTag) {
                                            onNavigateBack()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("save_anyway_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = DarkBg, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Account Anyway (Bypass Test)", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Action Buttons: Standalone Test Button & Save Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated Test Connection Button
                OutlinedButton(
                    onClick = {
                        viewModel.testConnectionOnly(url, key)
                    },
                    enabled = testStatus !is TestStatus.Testing,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("test_connection_button")
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection", fontWeight = FontWeight.Bold)
                }

                // Test & Save Button
                Button(
                    onClick = {
                        viewModel.testAndSaveAccount(name, url, key, pass, routingTag) {
                            onNavigateBack()
                        }
                    },
                    enabled = testStatus !is TestStatus.Testing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("verify_and_save_button")
                ) {
                    if (testStatus is TestStatus.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
