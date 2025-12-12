package org.dhis2.multiplatformmobileplayground.data.repository

import android.content.Context

actual object RepositoryFactory {
    private lateinit var applicationContext: Context
    
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
    
    actual fun createLoginRepository(): LoginRepository {
        return LoginRepositoryImpl(applicationContext)
    }
}
