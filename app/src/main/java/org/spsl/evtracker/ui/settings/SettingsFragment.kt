// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

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
import com.google.android.material.datepicker.MaterialDatePicker
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
                when (auth.authorize()) {
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

        // Do NOT attach the Drive switch listener here. Android's view-state
        // restoration calls setChecked() between onCreateView and onStart;
        // a listener attached at this point would synchronously fire
        // onUserToggledOn() before the StateFlow collector below has synced
        // isChecked to the persisted DataStore value. The listener is
        // attached lazily by the collector, AFTER the first state-driven sync.

        // manual Drive controls
        binding.buttonBackupNow.setOnClickListener { viewModel.onPushBackupClicked() }
        binding.buttonWipeRemote.setOnClickListener { showWipeConfirmDialog() }

        // Reset/preferences row clicks
        binding.rowPrimaryMetric.setOnClickListener { showPrimaryMetricDialog() }
        binding.rowDistanceUnit.setOnClickListener { showDistanceUnitDialog() }
        binding.rowCurrency.setOnClickListener { showCurrencyDialog() }
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowLanguage.setOnClickListener { showLanguageDialog() }
        binding.rowCo2IceBaseline.setOnClickListener { showIceBaselineDialog() }
        binding.rowCo2GridIntensity.setOnClickListener { showGridIntensityDialog() }
        binding.rowManageLocations.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_manage_locations)
        }
        binding.rowExportCsv.setOnClickListener { viewModel.onExportCsv() }
        binding.rowExportCsvRange.setOnClickListener { showExportRangePicker() }
        binding.rowResetPreferences.setOnClickListener { showResetPreferencesDialog() }
        binding.rowResetActiveCar.setOnClickListener { showResetActiveCarDialog() }
        binding.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
        }
        binding.rowResetAll.setOnClickListener { showResetAllDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // lazy first-attach of the Drive switch
                    // listener. The first emission unconditionally syncs
                    // isChecked to the persisted state with NO listener
                    // attached, then attaches the listener for the first time.
                    // Subsequent transitions go through the same detach/set/
                    // reattach rebind block as before.
                    var driveListenerAttached = false
                    viewModel.uiState.collect { state ->
                        if (!driveListenerAttached) {
                            binding.switchDrive.isChecked = state.driveEnabled
                            binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
                            }
                            driveListenerAttached = true
                        } else if (binding.switchDrive.isChecked != state.driveEnabled) {
                            binding.switchDrive.setOnCheckedChangeListener(null)
                            binding.switchDrive.isChecked = state.driveEnabled
                            binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
                            }
                        }
                        binding.switchDrive.isEnabled = !state.isAuthInFlight
                        binding.textLastBackup.text = formatLastBackup(state.lastBackupAt)
                        renderManualDriveControls(state)

                        // Render row summaries + enabled/disabled state
                        renderPreferenceRows(state)
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
                            SettingsEvent.BackupNowSucceeded ->
                                Snackbar.make(binding.root, R.string.drive_backup_now_success, Snackbar.LENGTH_SHORT).show()
                            is SettingsEvent.BackupNowFailed ->
                                Snackbar.make(binding.root, ev.msgRes, Snackbar.LENGTH_LONG).show()
                            SettingsEvent.WipeSucceeded ->
                                Snackbar.make(binding.root, R.string.drive_wipe_success, Snackbar.LENGTH_SHORT).show()
                            is SettingsEvent.WipeFailed ->
                                Snackbar.make(binding.root, ev.msgRes, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderManualDriveControls(state: SettingsUiState) {
        val visibility = if (state.driveEnabled) View.VISIBLE else View.GONE
        binding.buttonBackupNow.visibility = visibility
        binding.buttonWipeRemote.visibility = visibility

        // Mutual exclusion: a running operation disables the *other* button so a
        // second tap can't stack a wipe on top of an in-flight push (or vice versa).
        // The running button itself shows the spinner+disabled state.
        val anyRunning = state.isManualBackupRunning || state.isManualWipeRunning
        binding.buttonBackupNow.isEnabled = !anyRunning
        binding.buttonWipeRemote.isEnabled = !anyRunning
    }

    private fun showWipeConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_wipe_confirm_title)
            .setMessage(R.string.drive_wipe_confirm_body)
            .setPositiveButton(R.string.drive_wipe_confirm_delete) { _, _ ->
                viewModel.onConfirmWipeClicked()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun renderPreferenceRows(state: SettingsUiState) {
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
        // language summary. Empty tag = follow system; otherwise the
        // autonym lookup. Autonym strings are translatable=false so they
        // always render in their own script regardless of the current locale.
        binding.summaryLanguage.text = languageLabelFor(state.languageTag)
        // CO₂ row summaries. Numbers are formatted in the
        // user's default locale via Locale-aware DecimalFormat — Greek
        // / Russian readers see comma decimals, English readers see
        // dot decimals. Units stay non-localised (L/100km, gCO₂/kWh).
        binding.summaryCo2IceBaseline.text =
            String.format(java.util.Locale.getDefault(), "%.1f L/100km", state.iceBaselineLPer100km)
        binding.summaryCo2GridIntensity.text =
            String.format(java.util.Locale.getDefault(), "%.0f gCO₂/kWh", state.gridIntensityGCo2PerKwh)
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

        val activeCarMissing = state.activeCarId == -1L
        binding.rowResetActiveCar.alpha = if (activeCarMissing) 0.5f else 1f
        binding.rowResetActiveCar.isClickable = !activeCarMissing
        binding.rowResetActiveCar.isFocusable = !activeCarMissing
        binding.rowExportCsv.alpha = if (activeCarMissing) 0.5f else 1f
        binding.rowExportCsv.isClickable = !activeCarMissing
        binding.rowExportCsv.isFocusable = !activeCarMissing
        binding.rowExportCsvRange.alpha = if (activeCarMissing) 0.5f else 1f
        binding.rowExportCsvRange.isClickable = !activeCarMissing
        binding.rowExportCsvRange.isFocusable = !activeCarMissing
    }

    /**
     * Opens a `MaterialDatePicker` date-range picker and forwards
     * the user's choice to [SettingsViewModel.onExportCsvRange]. The picker
     * uses the default UTC selection mode; the use case treats the
     * selected millis as inclusive on both ends.
     */
    private fun showExportRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.settings_export_csv_range_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection?.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: return@addOnPositiveButtonClickListener
            viewModel.onExportCsvRange(start, end)
        }
        picker.show(parentFragmentManager, "export_range_picker")
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

    /**
     * Language picker dialog. The five options are "Follow
     * system" + four autonyms — each language's name written in its own
     * script. Autonyms must NOT be localised: a Greek user looking for
     * their language needs to see "Ελληνικά" written in Greek script
     * regardless of the current app locale.
     */
    /**
     * Numeric-input dialog shared by the two CO₂ rows.
     * Material doesn't ship a built-in numeric picker for arbitrary
     * doubles, so we use an EditText inside a MaterialAlertDialog with
     * inputType=numberDecimal. The locale-aware parser accepts both
     * `7.0` and `7,0` so Greek/Russian users with comma decimals
     * don't get rejected.
     */
    private fun showCo2NumberDialog(
        @androidx.annotation.StringRes titleRes: Int,
        currentValue: Double,
        onAccept: (Double) -> Unit,
    ) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.getDefault(), "%.2f", currentValue))
            setSelectAllOnFocus(true)
        }
        val container = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(container)
            .setNegativeButton(R.string.common_cancel, null)
            // Positive-button click is handled in setOnShowListener below so
            // we can validate input without dismissing on bad values.
            .setPositiveButton(R.string.common_confirm, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.replace(',', '.')?.trim().orEmpty()
                val parsed = raw.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) {
                    Snackbar.make(binding.root, R.string.settings_co2_dialog_invalid, Snackbar.LENGTH_SHORT).show()
                } else {
                    onAccept(parsed)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showIceBaselineDialog() {
        showCo2NumberDialog(
            titleRes = R.string.settings_co2_ice_baseline,
            currentValue = viewModel.uiState.value.iceBaselineLPer100km,
            onAccept = { viewModel.onIceBaselineSelected(it) },
        )
    }

    private fun showGridIntensityDialog() {
        showCo2NumberDialog(
            titleRes = R.string.settings_co2_grid_intensity,
            currentValue = viewModel.uiState.value.gridIntensityGCo2PerKwh,
            onAccept = { viewModel.onGridIntensitySelected(it) },
        )
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(R.string.settings_language_follow_system),
            getString(R.string.language_name_en),
            getString(R.string.language_name_el),
            getString(R.string.language_name_tr),
            getString(R.string.language_name_ru),
        )
        val tokens = arrayOf("", "en", "el", "tr", "ru")
        val current = viewModel.uiState.value.languageTag
        val checked = tokens.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language_dialog_title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                viewModel.onLanguageSelected(tokens[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun languageLabelFor(tag: String): String = when (tag) {
        "en" -> getString(R.string.language_name_en)
        "el" -> getString(R.string.language_name_el)
        "tr" -> getString(R.string.language_name_tr)
        "ru" -> getString(R.string.language_name_ru)
        else -> getString(R.string.settings_language_follow_system)
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
