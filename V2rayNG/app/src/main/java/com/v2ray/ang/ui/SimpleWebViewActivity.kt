package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RawRes
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivitySimpleWebviewBinding
import java.nio.charset.StandardCharsets

class SimpleWebViewActivity : AzatNetBrandedBaseActivity() {

    private lateinit var binding: ActivitySimpleWebviewBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleWebviewBinding.inflate(layoutInflater)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.title_faq)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = title)

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = WebViewClient()

        val rawId = intent.getIntExtra(EXTRA_RAW_RES, 0)
        if (rawId != 0) {
            loadRawHtml(rawId)
        } else {
            val url = intent.getStringExtra(EXTRA_URL) ?: run {
                finish()
                return
            }
            binding.webView.loadUrl(url)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun loadRawHtml(@RawRes rawId: Int) {
        resources.openRawResource(rawId).use { input ->
            val html = input.bufferedReader(StandardCharsets.UTF_8).readText()
            binding.webView.loadDataWithBaseURL(null, html, "text/html", StandardCharsets.UTF_8.name(), null)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_RAW_RES = "extra_raw_res"
    }
}
