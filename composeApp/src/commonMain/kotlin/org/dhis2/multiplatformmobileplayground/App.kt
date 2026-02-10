package org.dhis2.multiplatformmobileplayground

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        var isLoggedIn by remember { mutableStateOf(false) }
        
        if (!isLoggedIn) {
            val loginViewModel: LoginViewModel = viewModel {
                LoginViewModel(RepositoryFactory.createLoginRepository())
            }
            
            val loginUiState by loginViewModel.uiState.collectAsState()
            
            LaunchedEffect(loginUiState.isLoginSuccessful) {
                if (loginUiState.isLoginSuccessful) {
                    isLoggedIn = true
                }
            }
            
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = { }
            )
        } else {
            val homeViewModel: HomeViewModel = viewModel {
                HomeViewModel(
                    RepositoryFactory.createUserRepository(),
                    RepositoryFactory.createProgramRepository()
                )
            }
            
            HomeScreen(
                viewModel = homeViewModel
            )
        }
    }
}