package org.dhis2.multiplatformmobileplayground.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.dhis2.multiplatformmobileplayground.model.HomeUiState
import org.dhis2.multiplatformmobileplayground.model.Program
import org.dhis2.multiplatformmobileplayground.viewmodel.HomeViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.NotebookViewModel
import org.koin.compose.viewmodel.koinViewModel

private enum class HomeTab {
    HOME, NOTEBOOK
}

@Composable
fun ProgramCard(program: Program) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = program.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            program.description?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit,
    sessionKey: Int,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    // Scope the Notebook VM to the session too, so logout + new login resets its history.
    val notebookViewModel: NotebookViewModel = koinViewModel(key = "notebook-$sessionKey")
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("DHIS2 Playground") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log out"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.HOME,
                    onClick = { selectedTab = HomeTab.HOME },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.NOTEBOOK,
                    onClick = { selectedTab = HomeTab.NOTEBOOK },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Create,
                            contentDescription = "Notebook"
                        )
                    },
                    label = { Text("Notebook") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            HomeTab.HOME -> HomeContent(
                uiState = uiState,
                modifier = Modifier.padding(paddingValues)
            )
            HomeTab.NOTEBOOK -> NotebookScreen(
                viewModel = notebookViewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val userInfo = uiState.userInfo
    val error = uiState.error
    // Single scrollable list for the whole content (the Scaffold keeps the top bar and nav bar
    // fixed). Each section is an item so the entire screen scrolls together, not just the programs.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(modifier = Modifier.height(32.dp)) }

        item {
            Text(
                text = "Welcome!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        if (userInfo != null) {
            item {
                Text(
                    text = "Hello, ${userInfo.firstName}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "User Information",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Username",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userInfo.username,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Server URL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userInfo.serverUrl,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // Show loading indicator if programs are being loaded
            if (uiState.isLoading || uiState.isSyncing) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.isSyncing) {
                            Text(
                                text = "Syncing metadata...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Show error message if there was an error loading programs
            if (error != null) {
                item {
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Show programs list
            if (uiState.programs.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Programs",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                items(uiState.programs) { program ->
                    ProgramCard(program = program)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
