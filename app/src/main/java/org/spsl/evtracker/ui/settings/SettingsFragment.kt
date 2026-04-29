package org.spsl.evtracker.ui.settings

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.databinding.FragmentSettingsBinding
import org.spsl.evtracker.domain.backup.DriveAuthManager

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var auth: DriveAuthManager
    private val viewModel: SettingsViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Consent granted — re-run authorize() to pick up the cached silent token.
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
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // Avoid re-emitting the listener when we set checked from state.
                        if (binding.switchDrive.isChecked != state.driveEnabled) {
                            binding.switchDrive.setOnCheckedChangeListener(null)
                            binding.switchDrive.isChecked = state.driveEnabled
                            binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
                            }
                        }
                        binding.switchDrive.isEnabled = !state.isAuthInFlight
                        binding.textLastBackup.text = formatLastBackup(state.lastBackupAt)
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
                            // F1 events handled in Task 13's full Fragment rewrite.
                            is SettingsEvent.AutoFlipped,
                            is SettingsEvent.LaunchCsvShareIntent,
                            SettingsEvent.NavigateToWizard -> Unit
                        }
                    }
                }
            }
        }
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
