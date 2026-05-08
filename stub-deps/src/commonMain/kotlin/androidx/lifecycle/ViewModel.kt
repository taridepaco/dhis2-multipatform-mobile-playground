package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class ViewModel {
    open fun onCleared() {}
}

private val viewModelScopes = HashMap<ViewModel, CoroutineScope>()

val ViewModel.viewModelScope: CoroutineScope
    get() = viewModelScopes.getOrPut(this) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
