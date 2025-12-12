package org.dhis2.multiplatformmobileplayground.data.repository

expect object RepositoryFactory {
    fun createLoginRepository(): LoginRepository
}
