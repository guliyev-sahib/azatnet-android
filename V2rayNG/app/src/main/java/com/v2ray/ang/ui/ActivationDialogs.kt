package com.v2ray.ang.ui

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.ActivationHelper
import com.v2ray.ang.handler.MmkvManager

/**
 * Shows AzatNet activation bottom sheet. On success, extends Premium period (stub validation).
 */
fun AppCompatActivity.showAzatNetActivationDialog(onSuccess: () -> Unit = {}) {
    if (ActivationHelper.isLocked()) {
        toast(R.string.activation_locked_toast_hour)
        return
    }

    val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_AzatNet_BottomSheet)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_activation, null)
    dialog.setContentView(view)

    val input = view.findViewById<TextInputEditText>(R.id.input_activation_code)
    view.findViewById<android.view.View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
    view.findViewById<android.view.View>(R.id.btn_submit).setOnClickListener {
        val code = input.text?.toString().orEmpty().trim()
        if (!isStubValidActivationCode(code)) {
            ActivationHelper.recordFailedAttempt()
            if (ActivationHelper.isLocked()) {
                toast(R.string.activation_locked_toast_hour)
            } else {
                toast(R.string.activation_invalid_code)
            }
            dialog.dismiss()
            return@setOnClickListener
        }
        ActivationHelper.resetAttempts()
        val monthMs = 30L * 24 * 60 * 60 * 1000
        MmkvManager.encodeSettings(
            AppConfig.PREF_PREMIUM_UNTIL_MS,
            System.currentTimeMillis() + monthMs
        )
        toast(R.string.activation_success)
        dialog.dismiss()
        onSuccess()
    }

    dialog.show()
}

/** Replace with server-side validation when backend is ready. */
private fun isStubValidActivationCode(code: String): Boolean {
    return code.equals("AZATNET", ignoreCase = true)
}
