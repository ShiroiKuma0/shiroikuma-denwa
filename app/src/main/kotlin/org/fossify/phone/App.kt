package org.fossify.phone

import org.fossify.commons.FossifyApp
import org.fossify.phone.extensions.seedBlackYellowThemeIfNeeded

class App : FossifyApp() {
    override fun onCreate() {
        super.onCreate()
        // Apply the default black/yellow look once, before any activity themes itself.
        seedBlackYellowThemeIfNeeded()
    }
}
