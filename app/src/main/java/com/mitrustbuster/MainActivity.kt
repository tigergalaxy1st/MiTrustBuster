package com.mitrustbuster

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show warning on first launch
        val prefs = getSharedPreferences("mitrustbuster_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("warning_shown", false)) {
            showWarningDialog(prefs)
        }

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    private fun showWarningDialog(prefs: SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle(R.string.warning_title)
            .setMessage(R.string.warning_message)
            .setCancelable(false)
            .setPositiveButton(R.string.understand) { _, _ ->
                prefs.edit().putBoolean("warning_shown", true).apply()
            }
            .show()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val disableMrmPref = findPreference<SwitchPreferenceCompat>("disable_mrm")
            disableMrmPref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    Toast.makeText(
                        requireContext(),
                        R.string.toast_enabled,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.toast_disabled,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }
        }
    }
}
