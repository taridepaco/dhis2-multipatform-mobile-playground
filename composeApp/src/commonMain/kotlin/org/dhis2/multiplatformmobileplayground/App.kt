package org.dhis2.multiplatformmobileplayground

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.dhis2.multiplatformmobileplayground.data.repository.RepositoryFactory
import org.dhis2.multiplatformmobileplayground.model.UserInfo
import org.dhis2.multiplatformmobileplayground.ui.screens.HomeScreen
import org.dhis2.multiplatformmobileplayground.ui.screens.LoginScreen
import org.dhis2.multiplatformmobileplayground.viewmodel.HomeViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import dhis2multiplatformmobileplayground.composeapp.generated.resources.Res
import dhis2multiplatformmobileplayground.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    MaterialTheme {
        var userInfo by remember { mutableStateOf<UserInfo?>(null) }
        
        if (userInfo == null) {
            val loginViewModel: LoginViewModel = viewModel {
                LoginViewModel(RepositoryFactory.createLoginRepository())
            }
            
            val loginUiState by loginViewModel.uiState.collectAsState()
            
            LaunchedEffect(loginUiState.userInfo) {
                loginUiState.userInfo?.let {
                    userInfo = it
                }
            }
            
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = { }
            )
        } else {
            val homeViewModel: HomeViewModel = viewModel {
                HomeViewModel(userInfo!!)
            }
            
            HomeScreen(
                viewModel = homeViewModel
            )
        }
    }
}