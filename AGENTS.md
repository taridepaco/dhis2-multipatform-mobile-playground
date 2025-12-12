# Agent Rules for DHIS2 Multiplatform Mobile Playground

## Language Policy
- **All code, documentation, comments, and commit messages MUST be written in English**
- Agent conversations can be in Spanish or any other language, but all generated artifacts must be in English

## Architecture and Patterns

### Core Architecture
- **Pattern**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Reference**: https://developer.android.com/topic/architecture
- **Platform**: Kotlin Multiplatform (KMP) with Compose Multiplatform

### Layer Structure
- **UI Layer**: Composables + ViewModels (state holders)
- **Domain Layer** (optional): Use Cases for complex or reusable business logic
- **Data Layer**: Repositories + Data Sources (local/remote)

## Architectural Principles

### Separation of Concerns
- No business logic in Composables
- Activities/Fragments only host UI
- ViewModels manage UI state and business logic
- Repositories handle data operations

### Single Source of Truth (SSOT)
- Each data type has a single owner
- The SSOT exposes immutable data
- Only the SSOT can modify its data

### Unidirectional Data Flow (UDF)
- State flows downward (parent to child)
- Events flow upward (child to parent)
- Maintains data consistency and reduces errors

### Drive UI from Data Models
- UI is reactive to persistent data models
- Data models are independent from UI lifecycle
- Ensures data persistence across configuration changes

## Code Organization (KMP)

```
composeApp/src/
├── commonMain/       # Shared code (UI, ViewModels, Repositories, Domain)
├── commonTest/       # Shared tests (PRIORITY LOCATION)
├── androidMain/      # Android-specific code
├── iosMain/          # iOS-specific code
└── jvmMain/          # JVM/Desktop-specific code
```

### Maximize Shared Code
- Place as much code as possible in `commonMain`
- Use `expect/actual` only when platform-specific implementation is required
- Abstract platform-specific APIs behind common interfaces

## Testing Requirements

### Test Location
- **Primary location**: `composeApp/src/commonTest/`
- All shared code must have tests in `commonTest`
- Platform-specific tests go in respective test folders

### Test Coverage
- **MANDATORY**: All new code MUST include corresponding tests
- Unit tests for ViewModels, Repositories, and Use Cases
- Use `kotlin-test` for multiplatform compatibility
- Test business logic, not implementation details

### Test Naming
- Test classes: `[ClassName]Test.kt`
- Test methods: `should[ExpectedBehavior]When[Condition]()`

## Naming Conventions

### Classes
- **ViewModels**: `[Feature]ViewModel.kt` (e.g., `UserProfileViewModel.kt`)
- **Repositories**: `[Entity]Repository.kt` (e.g., `UserRepository.kt`)
- **Use Cases**: `[Action][Entity]UseCase.kt` (e.g., `GetUserDataUseCase.kt`)
- **Data Sources**: `[Entity][Type]DataSource.kt` (e.g., `UserRemoteDataSource.kt`)
- **Composables**: `[Feature]Screen.kt` or `[Component]Composable.kt`

### Packages
```
org.dhis2.multiplatformmobileplayground/
├── ui/
│   ├── screens/
│   ├── components/
│   └── theme/
├── viewmodel/
├── domain/
│   └── usecase/
├── data/
│   ├── repository/
│   └── datasource/
└── model/
```

## State Management

### ViewModels
- Use `StateFlow` for exposing UI state
- Use `MutableStateFlow` internally, expose as `StateFlow`
- Combine multiple flows when needed using `combine()`

### Composables
- Prefer stateless Composables
- Use state hoisting to share state
- Use `remember` for local UI state
- Use `LaunchedEffect` for side effects
- Use `collectAsState()` to observe StateFlow

## Dependencies and Libraries

### Current Stack
- Kotlin: 2.2.20
- Compose Multiplatform: 1.9.1
- Coroutines: 1.10.2
- Lifecycle: 2.9.5
- **DHIS2 Mobile UI**: 0.6.0
- **DHIS2 Android SDK**: 1.13.0

### Dependency Management
- Use version catalog (`libs.versions.toml`)
- Keep dependencies up to date
- Prefer multiplatform-compatible libraries

## Compose Best Practices

### Composable Design
- Keep Composables small and focused
- Extract reusable components
- Use `Modifier` parameter for flexibility
- Follow Material 3 design guidelines

### Performance
- Avoid unnecessary recompositions
- Use `remember` and `derivedStateOf` appropriately
- Use `key()` for list items
- Lazy layouts for long lists

## DHIS2 Implementation Guidelines

### UI Components & Design
- **MANDATORY**: All UI components and design elements MUST use the **DHIS2 Mobile UI Library** (`org.hisp.dhis.mobile:designsystem`).
- Do not create custom UI components if a suitable one exists in the DHIS2 Mobile UI library.
- Adhere strictly to the design system provided by the library.
 - **Documentation**: https://developers.dhis2.org/docs/mobile/mobile-ui/overview/

### SDK Usage & Multiplatform Strategy
- **Android Target**: MUST use the **DHIS2 Android SDK** (`org.hisp.dhis:android-core`) for all data and business logic implementations.
 - **Documentation**: https://github.com/dhis2/dhis2-android-sdk/tree/master/docs/content/developer
- **Other Targets (iOS, Desktop)**:
    - Since the DHIS2 Android SDK is Android-only, other targets are allowed to have **empty or stub implementations** for now.
    - Use `expect/actual` mechanisms where the Android `actual` implementation uses the SDK, and others are empty/no-op.

## Code Generation Rules

### Quality Standards
- Generated code MUST be immediately compilable
- Include all necessary imports
- Follow existing Kotlin code style (indentation, naming)
- No hardcoded sensitive values (API keys, URLs, credentials)
- Use dependency injection for external dependencies

### Documentation
- Add KDoc comments for public APIs
- Document complex business logic
- Include usage examples for non-trivial functions

## Agent Roles & Responsibilities

### UI Designer Expert
- **Focus**: User Interface, User Experience, DHIS2 Design System.
- **Responsibilities**:
    - Design and implement UI using **DHIS2 Mobile UI Library**.
    - Ensure strict adherence to DHIS2 design guidelines.
    - Create beautiful, responsive, and accessible Composables.
    - Manage UI state and interactions within Composables.
    - **Tools**: Compose Multiplatform, DHIS2 Mobile UI.

### Software Engineer / Architecture Developer
- **Focus**: System Architecture, Business Logic, Data Layer, Integration.
- **Responsibilities**:
    - Define and maintain the MVVM + Clean Architecture structure.
    - Implement Repositories, Use Cases, and ViewModels.
    - Integrate **DHIS2 Android SDK** for Android target.
    - Manage multiplatform dependencies and `expect/actual` implementations.
    - Ensure code quality, scalability, and performance.
    - **Tools**: Kotlin Multiplatform, Coroutines, Koin (if used), SQLDelight (if used).

### QA Testing Expert
- **Focus**: Quality Assurance, Testing Strategy, Bug Detection.
- **Responsibilities**:
    - Write and maintain **Unit Tests** for business logic (ViewModels, Use Cases).
    - Create **UI Tests** to verify screen behavior and appearance.
    - Perform manual verification and exploratory testing.
    - Identify edge cases and potential bugs.
    - Ensure high test coverage across all layers.
    - **Tools**: Kotlin Test, Compose Test, JUnit.

### How to Engage
- **Direct Invocation**: Start your request with "Act as [Role]..." to enforce a specific perspective (e.g., "As the UI Designer, critique this screen").
- **Task-Based**: The system will automatically select the best role for the task (e.g., creating a new screen -> UI Designer, then Architect).
- **Cross-Functional**: For complex features, ask for a "Team Review" to get input from Design, Architecture, and QA.

## Collaborative Agent Workflow

### Communication
- Document changes to public interfaces
- Maintain clear contracts between layers
- Coordinate before modifying code from other layers
- Use atomic commits per feature/layer

### Code Ownership
- Each agent should focus on specific layers
- Respect layer boundaries
- Don't bypass layers (e.g., UI directly accessing DataSource)

### Version Control
- Write clear, descriptive commit messages in English
- One logical change per commit
- Reference issue/task numbers when applicable

## Error Handling

### Best Practices
- Use sealed classes for operation results
- Handle errors at appropriate layers
- Provide meaningful error messages
- Log errors for debugging

### Example Pattern
```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}
```

## Security

### Sensitive Data
- Never hardcode API keys, tokens, or credentials
- Use BuildConfig or environment variables
- Store sensitive data securely (encrypted preferences, keystore)
- Don't log sensitive information

## Additional Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Keep functions small and focused
- Prefer immutability

### Performance
- Avoid blocking the main thread
- Use coroutines for async operations
- Optimize database queries
- Cache when appropriate

### Accessibility
- Provide content descriptions for images
- Support screen readers
- Ensure sufficient color contrast
- Support different text sizes

---

**Last Updated**: November 28, 2024
**Project**: DHIS2 Multiplatform Mobile Playground
**Architecture Reference**: https://developer.android.com/topic/architecture
