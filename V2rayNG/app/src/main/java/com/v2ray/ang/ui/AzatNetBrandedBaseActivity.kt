package com.v2ray.ang.ui

import android.os.Bundle
import androidx.core.view.WindowCompat

/**
 * Dark (#0F1923) toolbar/screens: keep status bar icons light (visible on dark background)
 * regardless of app light/dark preference.
 */
abstract class AzatNetBrandedBaseActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
