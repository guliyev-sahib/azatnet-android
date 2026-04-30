package com.v2ray.ang.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import com.v2ray.ang.R

class VpnSettingsActivity : AzatNetBrandedBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            R.layout.activity_settings,
            showHomeAsUp = true,
            title = getString(R.string.title_vpn_settings)
        )
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_settings,
                    SettingsActivity.SettingsFragment().apply {
                        arguments = bundleOf(
                            SettingsActivity.SettingsFragment.ARG_MODE to SettingsActivity.SettingsFragment.MODE_FULL
                        )
                    }
                )
                .commit()
        }
    }
}
