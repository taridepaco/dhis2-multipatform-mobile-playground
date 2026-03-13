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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.dhis2.multiplatformmobileplayground.data.repository.RepositoryFactory
import org.dhis2.multiplatformmobileplayground.ui.screens.HomeScreen
import org.dhis2.multiplatformmobileplayground.ui.screens.LoginScreen
import org.dhis2.multiplatformmobileplayground.viewmodel.HomeViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val loginViewModel: LoginViewModel = viewModel {
            LoginViewModel(RepositoryFactory.createLoginRepository())
        }
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
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(
                        RepositoryFactory.createUserRepository(),
                        RepositoryFactory.createProgramRepository()
                    )
                }
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
