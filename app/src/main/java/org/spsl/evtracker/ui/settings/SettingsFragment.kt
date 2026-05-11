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

    /** Status text inside the live "Electricity Maps API key" dialog, when
     *  one is showing. Null at all other times. The events collector writes
     *  to it on [SettingsEvent.ApiKeyTestStarted] / [SettingsEvent.ApiKeyTestFinished]. */
    private var apiKeyTestStatusView: android.widget.TextView? = null

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
        binding.rowCo2ApiKey.setOnClickListener { showElectricityMapsApiKeyDialog() }
        binding.rowCo2Zone.setOnClickListener { showElectricityMapsZoneDialog() }
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
        binding.buttonUpdateEvDb.setOnClickListener { viewModel.onUpdateEvDatabase() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // lazy first-attach of the Drive switch
                    // listener. The first emission unconditionally syncs
                    // isChecked to the persisted state with NO listener
                    // attached, then attaches the listener for the first time.
                    // Subsequent transitions go through the same detach/set/
                    // reattach rebind block as before. The CO₂ switch follows
                    // the same lazy-attach pattern for the same reason: view-state
                    // restoration calls setChecked() between onCreateView and
                    // onStart; binding the listener at that point would fire a
                    // spurious onCo2EnabledToggled with the restored value.
                    var driveListenerAttached = false
                    var co2ListenerAttached = false
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
                        if (!co2ListenerAttached) {
                            binding.switchCo2Enabled.isChecked = state.co2Enabled
                            binding.switchCo2Enabled.setOnCheckedChangeListener { _, isChecked ->
                                onCo2SwitchUserToggled(isChecked, state.electricityMapsApiKey)
                            }
                            co2ListenerAttached = true
                        } else if (binding.switchCo2Enabled.isChecked != state.co2Enabled) {
                            binding.switchCo2Enabled.setOnCheckedChangeListener(null)
                            binding.switchCo2Enabled.isChecked = state.co2Enabled
                            binding.switchCo2Enabled.setOnCheckedChangeListener { _, isChecked ->
                                onCo2SwitchUserToggled(isChecked, state.electricityMapsApiKey)
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
                            SettingsEvent.ApiKeyTestStarted ->
                                apiKeyTestStatusView?.text = getString(R.string.settings_co2_api_key_test_in_progress)
                            is SettingsEvent.ApiKeyTestFinished -> {
                                val text = if (ev.zone != null && ev.intensity != null) {
                                    getString(ev.resultStringRes, ev.zone, "%.0f".format(ev.intensity))
                                } else {
                                    getString(ev.resultStringRes)
                                }
                                apiKeyTestStatusView?.text = text
                            }
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
        // CO₂ row summaries. The petrol baseline is formatted in the
        // user's default locale (Greek / Russian readers see comma
        // decimals); the unit stays non-localised (L/100km). The grid
        // intensity has no static surface — it's captured per-event from
        // the Electricity Maps live feed, or not at all.
        binding.summaryCo2IceBaseline.text =
            String.format(java.util.Locale.getDefault(), "%.1f L/100km", state.iceBaselineLPer100km)
        // Electricity Maps rows only render when CO₂ tracking is opted in.
        // The ICE baseline row stays visible — it powers the counterfactual
        // and is independent of grid data.
        val emVisibility = if (state.co2Enabled) View.VISIBLE else View.GONE
        binding.rowCo2ApiKey.visibility = emVisibility
        binding.rowCo2Zone.visibility = emVisibility
        binding.summaryCo2ApiKey.setText(
            if (state.electricityMapsApiKey.isBlank()) {
                R.string.settings_co2_api_key_summary_unset
            } else {
                R.string.settings_co2_api_key_summary_set
            },
        )
        binding.summaryCo2Zone.text = state.electricityMapsZone
        binding.summaryManageLocations.text =
            if (state.customLocationCount == 0) {
                ""
            } else {
                getString(R.string.settings_manage_locations_summary, state.customLocationCount)
            }
        renderEvDbRow(state)

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
     * Render the Settings → "EV Database" row (TASK-91): summary line,
     * progress indicator, and Snackbar emission for the one-shot
     * update outcome. Called from [renderPreferenceRows] on every
     * state emission.
     */
    private fun renderEvDbRow(state: SettingsUiState) {
        binding.summaryEvDbStatus.text = when {
            state.evDbLastUpdatedAt > 0L && state.evDbVehicleCount > 0 -> {
                val dateText = formatLastBackup(state.evDbLastUpdatedAt)
                getString(
                    R.string.settings_ev_db_last_updated_summary,
                    dateText,
                    state.evDbVehicleCount,
                )
            }
            else -> getString(R.string.settings_ev_db_using_bundled)
        }
        val loading = state.evDbState is org.spsl.evtracker.core.model.EvDbUpdateState.Loading
        binding.buttonUpdateEvDb.isEnabled = !loading
        binding.progressUpdateEvDb.visibility = if (loading) View.VISIBLE else View.GONE

        when (val s = state.evDbState) {
            is org.spsl.evtracker.core.model.EvDbUpdateState.Success -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.settings_ev_db_update_success, s.vehicleCount),
                    Snackbar.LENGTH_LONG,
                ).show()
                viewModel.onEvDbStateConsumed()
            }
            is org.spsl.evtracker.core.model.EvDbUpdateState.Failure -> {
                Snackbar.make(binding.root, s.reasonRes, Snackbar.LENGTH_LONG).show()
                viewModel.onEvDbStateConsumed()
            }
            else -> Unit
        }
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

    /**
     * Handle a user-driven toggle of the CO₂ switch. Persisting the flag
     * happens in the VM; if the user is turning the feature ON for the
     * first time with a blank API key, immediately open the key dialog —
     * the static fallback works fine without a key, so this is a nudge
     * rather than a hard gate.
     */
    private fun onCo2SwitchUserToggled(isChecked: Boolean, currentApiKey: String) {
        viewModel.onCo2EnabledToggled(isChecked)
        if (isChecked && currentApiKey.isBlank()) {
            showElectricityMapsApiKeyDialog()
        }
    }

    /**
     * `MaterialAlertDialog` with a password-masked text input for the
     * Electricity Maps API key. Helper text links to the free-tier page;
     * tapping it opens the URL in the default browser. The "Test" button
     * (TASK-90) probes the candidate key against the user's current zone
     * and shows the outcome inline so users can validate before saving.
     */
    private fun showElectricityMapsApiKeyDialog() {
        val ctx = requireContext()
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(viewModel.uiState.value.electricityMapsApiKey)
            setSelectAllOnFocus(true)
        }
        val textLayout = com.google.android.material.textfield.TextInputLayout(
            ctx,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle,
        ).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            helperText = getString(R.string.settings_co2_api_key_dialog_helper)
            isHelperTextEnabled = true
            addView(input)
        }
        val testButton = com.google.android.material.button.MaterialButton(
            ctx,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = getString(R.string.settings_co2_api_key_test)
            setOnClickListener {
                viewModel.onTestElectricityMapsApiKey(input.text?.toString().orEmpty())
            }
        }
        val statusView = android.widget.TextView(ctx).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            text = ""
        }
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(textLayout)
            addView(testButton)
            addView(statusView)
        }
        apiKeyTestStatusView = statusView
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_co2_api_key_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                viewModel.onElectricityMapsApiKeySet(input.text?.toString().orEmpty())
            }
            .setNeutralButton(R.string.settings_co2_api_key_open_link) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://electricitymaps.com/free-tier"),
                        ),
                    )
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .setOnDismissListener { apiKeyTestStatusView = null }
            .show()
    }

    /**
     * Zone-code dialog. Plain text input, capitalised to uppercase via
     * keyboard hint; the VM also normalises before persisting.
     * Validation: non-blank letters only.
     */
    private fun showElectricityMapsZoneDialog() {
        val ctx = requireContext()
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(viewModel.uiState.value.electricityMapsZone)
            setSelectAllOnFocus(true)
        }
        val container = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            setPadding(48, 16, 48, 0)
            helperText = getString(R.string.settings_co2_zone_dialog_helper)
            isHelperTextEnabled = true
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_co2_zone_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.common_confirm, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.trim().orEmpty()
                if (raw.isBlank() || !raw.all { it.isLetter() }) {
                    Snackbar.make(binding.root, R.string.settings_co2_zone_dialog_invalid, Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.onElectricityMapsZoneSet(raw)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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
