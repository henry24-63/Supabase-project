package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDashboardScreen(
    viewModel: AccountViewModel,
    onNavigateToAddAccount: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage Supabase Accounts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddAccount) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(accounts) { account ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = { Text(account.projectUrl) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeAccount(account.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    )
                }
            }
        }
    }
}

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

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = rawData, 
            onValueChange = { 
                rawData = it
                viewModel.parseAndAddAccount(it) // Smart parsing
            }, 
            label = { Text("Paste Raw Data Here") }, 
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )
        
        HorizontalDivider()

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Project URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("DB Password") }, modifier = Modifier.fillMaxWidth())
        
        Button(onClick = {
            viewModel.addAccount(name, url, key, pass)
            onNavigateBack()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Account")
        }
    }
}
