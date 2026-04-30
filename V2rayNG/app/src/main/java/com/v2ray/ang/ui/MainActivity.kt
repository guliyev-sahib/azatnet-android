package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.ActivationManager
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addDefaultServer()
        setContentView(binding.root)

        binding.fab.setOnClickListener {
            try {
                handleFabAction()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                applyRunningState(false, mainViewModel.isRunning.value == true)
            }
        }
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.rowTelegram.setOnClickListener {
            Utils.openUri(this, getString(R.string.url_telegram))
        }
        binding.tvFooterSettings.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.tvFooterAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.btnActivation.setOnClickListener { showActivationDialog() }

        setupGroupTab()
        setupViewModel()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
        requestBatteryOptimizationExemptionIfNeeded()
    }

    /**
     * Recreates the built-in AzatNet profile on each launch to keep defaults up to date.
     */
    private fun addDefaultServer() {
        // Загружаем серверы с API
        lifecycleScope.launch {
            val servers = com.v2ray.ang.AzatNetServerManager.loadServers(this@MainActivity)

            val selectedBefore = MmkvManager.getSelectServer()
            var selectedWasDefault = false

            // Удаляем старые AzatNet серверы
            val allServers = MmkvManager.decodeAllServerList().distinct()
            allServers.forEach { guid ->
                val item = MmkvManager.decodeServerConfig(guid) ?: return@forEach
                if (item.password == "8ad39014-ff27-4172-8211-db3b8bb9fd19" ||
                    item.remarks?.startsWith("AzatNet") == true ||
                    item.remarks?.startsWith("Contabo") == true) {
                    if (selectedBefore == guid) selectedWasDefault = true
                    MmkvManager.removeServer(guid)
                }
            }

            // Добавляем все серверы с API
            servers.forEach { server ->
                val vlessLink = "vless://${server.uuid}@${server.address}:${server.port}" +
                    "?encryption=none&security=tls&sni=${server.sni}" +
                    "&fp=chrome&type=ws&host=${server.address}" +
                    "&path=${server.wsPath.replace("/", "%2F")}" +
                    "#${server.name}"
                AngConfigManager.importBatchConfig(vlessLink, "", false)
            }

            // Выбираем первый (главный) сервер
            val newList = MmkvManager.decodeAllServerList()
            val newGuid = newList.lastOrNull()
            if (selectedBefore.isNullOrEmpty() || selectedWasDefault) {
                newGuid?.let { MmkvManager.setSelectServer(it) }
            }

            mainViewModel.reloadServerList()
        }
    }

    /**
     * First-launch prompt to disable battery optimization so the VPN foreground service is not killed.
     */
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_BATTERY_OPTIMIZATION_PROMPTED, false)) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            MmkvManager.encodeSettings(AppConfig.PREF_BATTERY_OPTIMIZATION_PROMPTED, true)
            return
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            MmkvManager.encodeSettings(AppConfig.PREF_BATTERY_OPTIMIZATION_PROMPTED, true)
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(AppConfig.TAG, "Battery optimization request failed", e)
        }
        MmkvManager.encodeSettings(AppConfig.PREF_BATTERY_OPTIMIZATION_PROMPTED, true)
    }

    /* Auto server switch on ping failure removed — caused unstable VPN restarts. */

    private fun setupViewModel() {
        mainViewModel.trafficSpeed.observe(this) { speed ->
            binding.tvSpeedValue.text = speed ?: "—"
        }
        mainViewModel.updateTestResultAction.observe(this) { content ->
            if (!content.isNullOrBlank()) {
                binding.tvPingValue.text = content
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private suspend fun testCurrentServerRealDelay(): Long = withContext(Dispatchers.IO) {
        V2RayServiceManager.measureCurrentDelayMs()
    }

    private fun setupGroupTab() {}

    private fun handleFabAction() {
        if (!ActivationManager.isActivated(this)) {
            showActivationDialog()
            return
        }

        if (mainViewModel.isRunning.value == true) {
            applyRunningState(isLoading = true, isRunning = false)
            V2RayServiceManager.stopVService(this)
            return
        }

        ensureServerForConnection()
        applyRunningState(isLoading = true, isRunning = false)

        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun showActivationDialog() {
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val titleView = TextView(this).apply {
            text = "Введите код активации"
            textSize = 18f
        }
        val inputView = EditText(this).apply {
            hint = "AZAT-XXXX-XXXX-XXXX"
            setSingleLine(true)
        }
        val activateButton = Button(this).apply {
            text = "Активировать"
        }
        layout.addView(titleView)
        layout.addView(inputView)
        layout.addView(activateButton)
        dialog.setContentView(layout)

        activateButton.setOnClickListener {
            val code = inputView.text?.toString()?.trim().orEmpty()
            lifecycleScope.launch {
                val result = ActivationManager.activateCode(this@MainActivity, code)
                if (result.isSuccess) {
                    val expiresAt = result.getOrNull().orEmpty()
                    dialog.dismiss()
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(
                            "Активировано!\n" +
                                "Код: $code\n" +
                                "Действует до: ${formatDate(expiresAt)}"
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            handleFabAction()
                        }
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Неверный код", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val date = LocalDateTime.parse(isoDate)
            date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            isoDate
        }
    }

    /**
     * Picks the first server in the list, or injects the built-in profile if the list is empty.
     */
    private fun ensureServerForConnection() {
        val list = MmkvManager.decodeAllServerList().distinct()
        if (list.isNotEmpty()) {
            MmkvManager.setSelectServer(list.first())
            return
        }
        addDefaultServer()
        mainViewModel.reloadServerList()
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        V2RayServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.tvConnectLabel.text = "…"
            binding.fab.isEnabled = false
            binding.fab.setBackgroundResource(R.drawable.bg_connect_circle_loading)
            return
        }

        binding.fab.isEnabled = true
        val primary = ContextCompat.getColor(this, R.color.azatnet_text_primary)
        if (isRunning) {
            binding.fab.setBackgroundResource(R.drawable.bg_connect_circle_connected)
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
            binding.tvConnectLabel.text = getString(R.string.fab_disconnect)
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            binding.tvTestState.text = getString(R.string.connection_connected)
            binding.tvTestState.setTextColor(primary)
            lifecycleScope.launch {
                val ms = testCurrentServerRealDelay()
                if (mainViewModel.isRunning.value == true) {
                    binding.tvPingValue.text = if (ms >= 0) "${ms}мс" else "—"
                }
            }
        } else {
            binding.fab.setBackgroundResource(R.drawable.bg_connect_circle_disconnected)
            binding.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_red)
            binding.tvConnectLabel.text = getString(R.string.fab_connect)
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            binding.tvTestState.text = getString(R.string.connection_not_connected)
            binding.tvTestState.setTextColor(primary)
            binding.tvPingValue.text = "—"
        }
    }

    /**
     * Called from [GroupServerFragment] pull-to-refresh subscription update.
     */
    fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                }
                hideLoading()
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
