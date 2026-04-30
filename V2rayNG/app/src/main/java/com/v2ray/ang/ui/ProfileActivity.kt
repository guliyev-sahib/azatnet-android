package com.v2ray.ang.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityProfileBinding
import com.v2ray.ang.handler.ActivationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ProfileActivity : AzatNetBrandedBaseActivity() {

    private val binding by lazy { ActivityProfileBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_profile))

        binding.btnActivatePremium.setOnClickListener {
            showActivationDialog()
        }

        refreshProfileUi()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileUi()
    }

    private fun refreshProfileUi() {
        val premium = ActivationManager.isActivated(this)
        val prefs = getSharedPreferences("azatnet_prefs", MODE_PRIVATE)
        val expiresAt = prefs.getString("expires_at", null)

        if (premium && !expiresAt.isNullOrEmpty()) {
            binding.tvProfileStatus.text = getString(R.string.status_premium)
            binding.btnActivatePremium.isVisible = false
            val expiresText = formatDate(expiresAt)
            val daysLeft = daysLeft(expiresAt)
            binding.tvPremiumCountdown.isVisible = true
            binding.tvPremiumCountdown.text = "Действует до: $expiresText\nОсталось $daysLeft дней"
        } else {
            binding.tvProfileStatus.text = getString(R.string.status_free)
            binding.btnActivatePremium.isVisible = true
            binding.tvPremiumCountdown.isVisible = false
        }
    }

    private fun showActivationDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "AZAT-XXXX-XXXX-XXXX"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Введите код активации")
            .setView(input)
            .setPositiveButton("Активировать") { _, _ ->
                val code = input.text?.toString()?.trim().orEmpty()
                lifecycleScope.launch(Dispatchers.Main) {
                    val result = ActivationManager.activateCode(this@ProfileActivity, code)
                    if (result.isSuccess) {
                        val expiresAt = result.getOrNull().orEmpty()
                        AlertDialog.Builder(this@ProfileActivity)
                            .setMessage(
                                "Активировано!\n" +
                                    "Код: $code\n" +
                                    "Действует до: ${formatDate(expiresAt)}"
                            )
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        refreshProfileUi()
                    } else {
                        android.widget.Toast.makeText(
                            this@ProfileActivity,
                            "Неверный код",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDate(isoDate: String): String {
        return try {
            LocalDateTime.parse(isoDate).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun daysLeft(isoDate: String): Long {
        return try {
            val now = LocalDateTime.now()
            val expires = LocalDateTime.parse(isoDate)
            val days = ChronoUnit.DAYS.between(now.toLocalDate(), expires.toLocalDate())
            if (days < 0) 0 else days
        } catch (_: Exception) {
            0
        }
    }
}
