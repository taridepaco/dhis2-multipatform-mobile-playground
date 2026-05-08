package org.dhis2.multiplatformmobileplayground.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.dhis2.multiplatformmobileplayground.data.repository.DslExecutorFactory
import org.dhis2.multiplatformmobileplayground.data.repository.LoginRepository
import org.dhis2.multiplatformmobileplayground.data.repository.ProgramRepository
import org.dhis2.multiplatformmobileplayground.data.repository.RepositoryFactory
import org.dhis2.multiplatformmobileplayground.data.repository.UserRepository
import org.dhis2.multiplatformmobileplayground.dsl.executor.DslExecutor
import org.dhis2.multiplatformmobileplayground.viewmodel.HomeViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.LoginViewModel
import org.dhis2.multiplatformmobileplayground.viewmodel.NotebookViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    // Dispatchers
    single<CoroutineDispatcher> { Dispatchers.IO }

    // Repositories
    single<LoginRepository> { RepositoryFactory.createLoginRepository() }
    single<UserRepository> { RepositoryFactory.createUserRepository() }
    single<ProgramRepository> { RepositoryFactory.createProgramRepository() }

    // DSL executor (platform-specific via DslExecutorFactory)
    single<DslExecutor> { DslExecutorFactory.create() }

    // ViewModels
    factory { LoginViewModel(get(), get()) }
    factory { HomeViewModel(get(), get()) }
    factory { NotebookViewModel(get()) }
}
