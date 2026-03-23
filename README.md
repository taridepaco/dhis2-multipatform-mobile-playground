# DHIS2 Multiplatform Mobile Playground

A Kotlin Multiplatform playground application targeting Android, iOS, and Desktop (JVM) that demonstrates
DHIS2 authentication and program synchronization using the MVVM + Repository architecture pattern.

## Features

- **Automatic session persistence**: On startup the app checks whether a user is already logged in and skips
  the login screen if a valid session exists.
- **DHIS2 login**: Authenticates against any DHIS2 server using server URL, username, and password.
- **Program synchronization**: After login, metadata is downloaded from the server and the user's assigned
  programs are displayed.
- **User info display**: Shows the logged-in user's name and the server they are connected to.

> **Platform status**: The Android target has a full DHIS2 SDK integration. iOS and Desktop (JVM) targets
> contain stub implementations that return empty/error results — real implementations are planned for a
> future iteration.

---

## Tech Stack

| Component | Library / Version |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Compose Multiplatform 1.9.1 |
| Design system | DHIS2 Mobile UI 0.6.0 |
| DHIS2 data layer | DHIS2 Android SDK 1.13.0 (Android only) |
| Async | Kotlin Coroutines 1.10.2 |
| State management | AndroidX Lifecycle / ViewModel 2.9.5 |

---

## Architecture

The project follows **MVVM (Model-View-ViewModel) + Repository Pattern**:

```
UI Layer (Composables)
    |  observes StateFlow
    v
ViewModel Layer  (LoginViewModel, HomeViewModel)
    |  calls suspend functions
    v
Repository Layer (LoginRepository, UserRepository, ProgramRepository)
    |  platform-specific actual implementations
    v
Data Source (DHIS2 Android SDK on Android, stubs on iOS/JVM)
```

State flows **downward** (ViewModel to Composable via `StateFlow`) and events flow **upward**
(Composable to ViewModel via lambda callbacks), following Unidirectional Data Flow (UDF).

---

## Project Structure

```
composeApp/src/
├── commonMain/          # Shared code for all platforms
│   ├── App.kt           # Root composable – navigation between Login and Home
│   ├── model/           # Immutable data classes and sealed classes
│   ├── ui/screens/      # LoginScreen and HomeScreen composables
│   ├── viewmodel/       # LoginViewModel and HomeViewModel
│   └── data/repository/ # Repository interfaces + expect RepositoryFactory
├── androidMain/         # Android: DHIS2 SDK implementations
├── iosMain/             # iOS: stub implementations
├── jvmMain/             # Desktop: stub implementations
└── commonTest/          # Shared unit tests for ViewModels
```

### Key Components

#### Models (`commonMain/model/`)

| File | Description |
|---|---|
| `LoginCredentials.kt` | Holds server URL, username, and password entered by the user |
| `LoginResult.kt` | Sealed class: `Success(userInfo)` or `Error(message)` |
| `LoginUiState.kt` | UI state for the login screen (input values, loading, error flags) |
| `UserInfo.kt` | Logged-in user data: username, first name, server URL |
| `Program.kt` | DHIS2 program: id, name, displayName, optional description |
| `HomeUiState.kt` | UI state for the home screen (user info, programs list, sync flags) |

#### Repository Interfaces (`commonMain/data/repository/`)

```kotlin
interface LoginRepository {
    suspend fun login(credentials: LoginCredentials): LoginResult
    suspend fun isUserLoggedIn(): Boolean
}

interface UserRepository {
    suspend fun getCurrentUser(): UserInfo?
}

interface ProgramRepository {
    suspend fun getUserPrograms(): List<Program>
    suspend fun syncPrograms()
}
```

Platform-specific instances are created through the `expect`/`actual` `RepositoryFactory` object:

```kotlin
// commonMain
expect object RepositoryFactory {
    fun createLoginRepository(): LoginRepository
    fun createUserRepository(): UserRepository
    fun createProgramRepository(): ProgramRepository
}

// androidMain – must be initialised before first use
actual object RepositoryFactory {
    fun initialize(context: Context) { ... }
    actual fun createLoginRepository(): LoginRepository = LoginRepositoryImpl(applicationContext)
    actual fun createUserRepository(): UserRepository = UserRepositoryImpl(applicationContext)
    actual fun createProgramRepository(): ProgramRepository = ProgramRepositoryImpl(applicationContext)
}
```

#### ViewModels (`commonMain/viewmodel/`)

**`LoginViewModel`**
- On `init`, calls `loginRepository.isUserLoggedIn()` and updates `isCheckingAuth` / `isLoginSuccessful`.
- Validates that server URL, username, and password are non-blank before attempting login.
- Exposes `uiState: StateFlow<LoginUiState>` consumed by `LoginScreen`.

**`HomeViewModel`**
- On `init`, loads user info and triggers program sync followed by program fetch.
- Manages separate `isSyncing` and `isLoading` flags so the UI can show granular progress.
- Exposes `uiState: StateFlow<HomeUiState>` consumed by `HomeScreen`.

#### Screens (`commonMain/ui/screens/`)

**`LoginScreen`** – DHIS2 login form with three `InputText` fields (server URL, username, password),
a login button that shows a spinner while `isLoading` is true, and a `Snackbar` for error messages.
Default credentials pointing to the public DHIS2 demo instance are pre-filled for convenience.

**`HomeScreen`** – Displays the user's first name, their server URL, and a lazy list of assigned programs.
Shows a "Syncing metadata..." indicator while `isSyncing` is true and a loading spinner while
`isLoading` is true.

#### Navigation (`commonMain/App.kt`)

```kotlin
when {
    loginUiState.isCheckingAuth    -> CircularProgressIndicator()  // checking stored session
    loginUiState.isLoginSuccessful -> HomeScreen(...)              // already / just logged in
    else                           -> LoginScreen(...)             // needs login
}
```

---

## Default Demo Credentials

The login form is pre-filled with the public DHIS2 Android demo instance:

| Field | Value |
|---|---|
| Server URL | `https://android.im.dhis2.org/current` |
| Username | `android` |
| Password | `Android123` |

---

## Build and Run

### Android

`RepositoryFactory.initialize(applicationContext)` is called in `MainActivity.onCreate()` before
`setContent { App() }`, so no manual setup is needed.

Build from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Desktop (JVM)

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### iOS

Open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

## Testing

Unit tests for ViewModels live in `composeApp/src/commonTest/`:

```
commonTest/
├── ComposeAppCommonTest.kt
└── viewmodel/
    ├── LoginViewModelTest.kt
    └── HomeViewModelTest.kt
```

Run all tests:
```shell
./gradlew :composeApp:allTests
```

Tests use `kotlin-test` and `kotlinx-coroutines-test` for multiplatform compatibility.
Test method naming convention: `should[ExpectedBehavior]When[Condition]()`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
