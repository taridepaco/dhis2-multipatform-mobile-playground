package org.dhis2.multiplatformmobileplayground

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform