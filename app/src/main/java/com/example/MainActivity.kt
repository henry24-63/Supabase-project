package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SupabaseManager
import com.example.ui.AccountDashboardScreen
import com.example.ui.AccountViewModel
import com.example.ui.AddAccountScreen
import com.example.ui.AppLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.workers.SupabaseKeepAliveWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep-Alive Worker running 24h background pulse
        val workRequest = PeriodicWorkRequestBuilder<SupabaseKeepAliveWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SupabaseKeepAlive",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        val supabaseManager = SupabaseManager(this)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AccountViewModel(supabaseManager) as T
            }
        })[AccountViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val isAuthed by viewModel.isAuthenticated.collectAsState()
                val navController = rememberNavController()

                if (!isAuthed) {
                    AppLockScreen(viewModel = viewModel) {
                        // Unlocked
                    }
                } else {
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            AccountDashboardScreen(
                                viewModel = viewModel,
                                onNavigateToAddAccount = { navController.navigate("add") },
                                onLockClicked = { viewModel.lockApp() }
                            )
                        }
                        composable("add") {
                            AddAccountScreen(viewModel = viewModel) {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }
}
