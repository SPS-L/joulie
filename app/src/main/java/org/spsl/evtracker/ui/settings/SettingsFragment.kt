package org.spsl.evtracker.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.core.model.SettingsUiState
import org.spsl.evtracker.databinding.FragmentSettingsBinding
import org.spsl.evtracker.domain.backup.DriveAuthManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var auth: DriveAuthManager
    private val viewModel: SettingsViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = auth.authorize()) {
                    is DriveAuthManager.AuthResult.Success -> viewModel.onDriveAuthGranted()
                    else -> viewModel.onDriveAuthFailed(R.string.drive_auth_failed)
                }
            }
        } else {
            viewModel.onDriveAuthFailed(R.string.drive_consent_cancelled)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
        }

        // F1 row clicks
        binding.rowPrimaryMetric.setOnClickListener { showPrimaryMetricDialog() }
        binding.rowDistanceUnit.setOnClickListener { showDistanceUnitDialog() }
        binding.rowCurrency.setOnClickListener { showCurrencyDialog() }
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowManageLocations.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_manage_locations)
        }
        binding.rowExportCsv.setOnClickListener { viewModel.onExportCsv() }
        binding.rowResetPreferences.setOnClickListener { showResetPreferencesDialog() }
        binding.rowResetActiveCar.setOnClickListener { showResetActiveCarDialog() }
        binding.rowResetAll.setOnClickListener { showResetAllDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // E (Drive) — preserve listener-rebind pattern to avoid re-firing
                        if (binding.switchDrive.isChecked != state.driveEnabled) {
                            binding.switchDrive.setOnCheckedChangeListener(null)
                            binding.switchDrive.isChecked = state.driveEnabled
                            binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
                            }
                        }
                        binding.switchDrive.isEnabled = !state.isAuthInFlight
                        binding.textLastBackup.text = formatLastBackup(state.lastBackupAt)

                        // F1 — render row summaries + enabled/disabled state
                        renderF1Rows(state)
                    }
                }
                launch {
                    viewModel.events.collect { ev ->
                        when (ev) {
                            is SettingsEvent.ShowRestorePrompt -> showRestoreDialog(ev.backupDateLabel)
                            is SettingsEvent.RestoreSucceeded ->
                                Snackbar.make(binding.root, R.string.drive_restore_succeeded, Snackbar.LENGTH_LONG).show()
                            is SettingsEvent.ShowError ->
                                Snackbar.make(binding.root, ev.msgRes, Snackbar.LENGTH_LONG).show()
                            is SettingsEvent.AutoFlipped ->
                                Snackbar.make(binding.root, ev.msgRes, Snackbar.LENGTH_SHORT).show()
                            is SettingsEvent.LaunchCsvShareIntent -> launchCsvShareIntent(ev.uri)
                            SettingsEvent.NavigateToWizard ->
                                findNavController().navigate(R.id.action_settings_to_wizard)
                        }
                    }
                }
            }
        }
    }

    private fun renderF1Rows(state: SettingsUiState) {
        binding.summaryPrimaryMetric.setText(
            when (state.primaryMetric) {
                "kwh_per_100km" -> R.string.wizard_metric_kwh_per_100km
                "mi_per_kwh" -> R.string.wizard_metric_mi_per_kwh
                else -> R.string.wizard_metric_km_per_kwh
            },
        )
        binding.summaryDistanceUnit.setText(
            if (state.distanceUnit == "miles") R.string.wizard_unit_miles else R.string.wizard_unit_km,
        )
        binding.summaryCurrency.text = state.currency
        binding.summaryTheme.setText(
            when (state.theme) {
                "light" -> R.string.settings_theme_light
                "dark" -> R.string.settings_theme_dark
                else -> R.string.settings_theme_system
            },
        )
        binding.summaryManageLocations.text =
            if (state.customLocationCount == 0) {
                ""
            } else {
                getString(R.string.settings_manage_locations_summary, state.customLocationCount)
            }

        val activeName = state.activeCarName
        binding.titleResetActiveCar.text =
            if (activeName == null) {
                getString(R.string.settings_reset_active_car_default)
            } else {
                getString(R.string.settings_reset_active_car, activeName)
            }

        val activeCarMissing = state.activeCarId == -1
        binding.rowResetActiveCar.alpha = if (activeCarMissing) 0.5f else 1f
        binding.rowResetActiveCar.isClickable = !activeCarMissing
        binding.rowResetActiveCar.isFocusable = !activeCarMissing
        binding.rowExportCsv.alpha = if (activeCarMissing) 0.5f else 1f
        binding.rowExportCsv.isClickable = !activeCarMissing
        binding.rowExportCsv.isFocusable = !activeCarMissing
    }

    private fun launchCsvShareIntent(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun showPrimaryMetricDialog() {
        val labels = arrayOf(
            getString(R.string.wizard_metric_km_per_kwh),
            getString(R.string.wizard_metric_kwh_per_100km),
            getString(R.string.wizard_metric_mi_per_kwh),
        )
        val tokens = arrayOf("km_per_kwh", "kwh_per_100km", "mi_per_kwh")
        val current = viewModel.uiState.value.primaryMetric
        val checked = tokens.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_primary_metric)
            .setSingleChoiceItems(labels, checked) { d, which ->
                viewModel.onPrimaryMetricSelected(tokens[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showDistanceUnitDialog() {
        val labels = arrayOf(getString(R.string.wizard_unit_km), getString(R.string.wizard_unit_miles))
        val tokens = arrayOf("km", "miles")
        val current = viewModel.uiState.value.distanceUnit
        val checked = tokens.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_distance_unit)
            .setSingleChoiceItems(labels, checked) { d, which ->
                viewModel.onDistanceUnitSelected(tokens[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showCurrencyDialog() {
        val codes = resources.getStringArray(R.array.supported_currencies)
        val current = viewModel.uiState.value.currency
        val checked = codes.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_currency)
            .setSingleChoiceItems(codes, checked) { d, which ->
                viewModel.onCurrencySelected(codes[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val labels = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark),
        )
        val tokens = arrayOf("system", "light", "dark")
        val current = viewModel.uiState.value.theme
        val checked = tokens.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, checked) { d, which ->
                val token = tokens[which]
                viewModel.onThemeSelected(token)
                applyThemeImmediately(token)
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun applyThemeImmediately(token: String) {
        val mode = when (token) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showResetPreferencesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_preferences)
            .setMessage(R.string.settings_reset_preferences_summary)
            .setPositiveButton(R.string.common_confirm) { _, _ -> viewModel.onResetPreferences() }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showResetActiveCarDialog() {
        val name = viewModel.uiState.value.activeCarName ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_active_car_default)
            .setMessage(getString(R.string.settings_reset_active_car_confirm, name))
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                viewModel.onResetActiveCarData()
                Snackbar.make(binding.root, R.string.settings_reset_active_car_done, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showResetAllDialog() {
        val msgRes = if (viewModel.uiState.value.driveEnabled) {
            R.string.settings_reset_all_confirm_drive_on
        } else {
            R.string.settings_reset_all_confirm
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_all)
            .setMessage(msgRes)
            .setPositiveButton(R.string.common_confirm) { _, _ -> viewModel.onResetAllData() }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun onUserToggledOn() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = auth.authorize()) {
                is DriveAuthManager.AuthResult.Success -> viewModel.onDriveAuthGranted()
                is DriveAuthManager.AuthResult.NeedsResolution ->
                    consentLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                is DriveAuthManager.AuthResult.Failed ->
                    viewModel.onDriveAuthFailed(R.string.drive_auth_failed)
            }
        }
    }

    private fun showRestoreDialog(label: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_restore_dialog_title)
            .setMessage(getString(R.string.drive_restore_dialog_body, label))
            .setPositiveButton(R.string.drive_restore_dialog_confirm) { _, _ -> viewModel.onConfirmRestore() }
            .setNegativeButton(R.string.drive_restore_dialog_skip) { _, _ -> viewModel.onSkipRestore() }
            .setOnDismissListener { viewModel.onRestorePromptDismissed() }
            .setCancelable(true)
            .show()
    }

    private fun formatLastBackup(epochMs: Long?): String {
        if (epochMs == null) return getString(R.string.settings_last_backup_label, getString(R.string.settings_last_backup_never))
        val label = DATE_FORMAT.format(Date(epochMs))
        return getString(R.string.settings_last_backup_label, label)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
