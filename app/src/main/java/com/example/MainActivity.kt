package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.SupabaseManager
import com.example.ui.AccountDashboardScreen
import com.example.ui.AccountViewModel
import com.example.ui.AddAccountScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.workers.SupabaseKeepAliveWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                return AccountViewModel(supabaseManager) as T
            }
        })[AccountViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") { 
                        AccountDashboardScreen(viewModel) { navController.navigate("add") } 
                    }
                    composable("add") { 
                        AddAccountScreen(viewModel) { navController.popBackStack() } 
                    }
                }
            }
        }
    }
}
