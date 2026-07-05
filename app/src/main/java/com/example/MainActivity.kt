package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.PasswordRepository
import com.example.security.BiometricHelper
import com.example.ui.MainApp
import com.example.ui.PasswordViewModel
import com.example.ui.PasswordViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: PasswordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Initialize Database, Dao and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = PasswordRepository(database.passwordDao())
        
        // Instantiate ViewModel
        viewModel = ViewModelProvider(this, PasswordViewModelFactory(repository))[PasswordViewModel::class.java]
        viewModel.initSettings(this)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val useDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            val secureMode by viewModel.secureModeEnabled.collectAsStateWithLifecycle()
            
            LaunchedEffect(secureMode) {
                if (secureMode) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyApplicationTheme(darkTheme = useDarkTheme, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MainApp(
                            viewModel = viewModel,
                            activity = this@MainActivity,
                            triggerBiometric = { onSuccess, onError ->
                                viewModel.setIgnoreNextPause(true)
                                BiometricHelper.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    title = "تایید هویت زیست‌سنجی",
                                    subtitle = "جهت باز کردن قفل صندوقچه، اثر انگشت یا چهره خود را تایید کنید",
                                    onAuthSuccess = onSuccess,
                                    onAuthError = onError
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.onAppResumed()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.onAppPaused()
        }
    }
}
