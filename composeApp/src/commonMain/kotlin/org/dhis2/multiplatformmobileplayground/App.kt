package org.dhis2.multiplatformmobileplayground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.dhis2.multiplatformmobileplayground.ui.screens.HomeScreen
import org.dhis2.multiplatformmobileplayground.ui.screens.LoginScreen
import org.dhis2.multiplatformmobileplayground.viewmodel.HomeViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    KoinContext {
        MaterialTheme {
            val loginViewModel: LoginViewModel = koinViewModel()
            val loginUiState by loginViewModel.uiState.collectAsState()

            when {
                loginUiState.isCheckingAuth -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                loginUiState.isLoginSuccessful -> {
                    val homeViewModel: HomeViewModel = koinViewModel()
                    HomeScreen(viewModel = homeViewModel)
                }
                else -> {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = { }
                    )
                }
            }
        }
    }
}
