package org.fossify.phone.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.SIDELOADING_FALSE

class SplashActivity : BaseSplashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Custom-signed builds trip Fossify Commons' sideloading detection: it probes for a
        // Commons drawable that resource shrinking can strip, then persists a "sideloaded"
        // verdict and shows a blocking dialog on every launch. Mark the app as not sideloaded
        // before BaseSplashActivity.onCreate reads the stored status, neutralising the check
        // (and clearing any previously persisted verdict).
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        super.onCreate(savedInstanceState)
    }

    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
