package org.dhis2.multiplatformmobileplayground.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModel
import org.hisp.dhis.mobile.ui.designsystem.component.Button
import org.hisp.dhis.mobile.ui.designsystem.component.ButtonStyle
import org.hisp.dhis.mobile.ui.designsystem.component.InputShellState
import org.hisp.dhis.mobile.ui.designsystem.component.InputText

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) {
            onLoginSuccess()
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DHIS2 Login",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            InputText(
                title = "Server URL",
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = TextFieldValue(uiState.serverUrl),
                onValueChanged = { viewModel.onServerUrlChanged(it?.text ?: "") },
                modifier = Modifier.fillMaxWidth(),
                isRequiredField = true,
                imeAction = ImeAction.Next
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InputText(
                title = "Username",
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = TextFieldValue(uiState.username),
                onValueChanged = { viewModel.onUsernameChanged(it?.text ?: "") },
                modifier = Modifier.fillMaxWidth(),
                isRequiredField = true,
                imeAction = ImeAction.Next
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InputText(
                title = "Password",
                state = InputShellState.UNFOCUSED,
                inputTextFieldValue = TextFieldValue(uiState.password),
                onValueChanged = { viewModel.onPasswordChanged(it?.text ?: "") },
                modifier = Modifier.fillMaxWidth(),
                isRequiredField = true,
                imeAction = ImeAction.Done
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                text = if (uiState.isLoading) "Logging in..." else "Login",
                style = ButtonStyle.FILLED,
                enabled = !uiState.isLoading,
                onClick = { viewModel.onLoginClicked() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
