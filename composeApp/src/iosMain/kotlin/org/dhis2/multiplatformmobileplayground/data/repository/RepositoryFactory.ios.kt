package org.dhis2.multiplatformmobileplayground.data.repository

actual object RepositoryFactory {
    actual fun createLoginRepository(): LoginRepository {
        return LoginRepositoryImpl()
    }
}
