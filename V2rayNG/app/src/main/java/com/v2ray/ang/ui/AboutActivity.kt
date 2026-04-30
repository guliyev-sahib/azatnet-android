package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.util.Utils

class AboutActivity : AzatNetBrandedBaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_about))

        binding.tvVersion.text = getString(R.string.about_version_label, BuildConfig.VERSION_NAME)

        binding.cardFaq.setOnClickListener {
            startActivity(
                Intent(this, SimpleWebViewActivity::class.java).apply {
                    putExtra(SimpleWebViewActivity.EXTRA_TITLE, getString(R.string.title_faq_short))
                    putExtra(SimpleWebViewActivity.EXTRA_RAW_RES, R.raw.faq)
                }
            )
        }

        binding.cardPrivacy.setOnClickListener {
            startActivity(
                Intent(this, SimpleWebViewActivity::class.java).apply {
                    putExtra(SimpleWebViewActivity.EXTRA_TITLE, getString(R.string.title_privacy_policy))
                    putExtra(SimpleWebViewActivity.EXTRA_RAW_RES, R.raw.privacy_policy)
                }
            )
        }

        binding.cardContact.setOnClickListener {
            Utils.openUri(this, getString(R.string.url_telegram))
        }
    }
}
