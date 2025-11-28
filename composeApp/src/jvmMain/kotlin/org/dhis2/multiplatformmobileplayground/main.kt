package org.dhis2.multiplatformmobileplayground

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dhis2MultiplatformMobilePlayground",
    ) {
        App()
    }
}