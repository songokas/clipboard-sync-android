package com.clipboard.sync

import android.R.string
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class ButtonValidation(
        val editText: EditText,
        val validation: (Editable) -> String?,
    ) : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence,
            start: Int,
            count: Int,
            after: Int
        ) {
        }

        override fun onTextChanged(
            s: CharSequence,
            start: Int,
            before: Int,
            count: Int
        ) {
        }

        override fun afterTextChanged(editable: Editable) {
            val validationError = validation(editable)
//                        editText.error = validationError
            editText.rootView.findViewById<View>(android.R.id.button1).isEnabled =
                validationError == null
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val key =
                preferenceScreen.findPreference<Preference>("key") as EditTextPreference?

            key!!.setOnBindEditTextListener { editText ->
                editText.filters = arrayOf<InputFilter>(LengthFilter(32))
                val validation = { editable: Editable ->
                    if (editable.count() != 32) {
                        "key must be 32 chars long"
                    } else {
                        null
                    }
                }
                editText.addTextChangedListener(ButtonValidation(editText, validation))
            }

            val socketValidation = { editable: Editable ->
                try {
                    val socket = editable.toString()
                    val uri = URI("cuckoo://$socket") // may throw URISyntaxException
                    if (uri.host == null || uri.port == -1) {
                        throw URISyntaxException(uri.toString(), "socket address is not valid")
                    }
                    null
                } catch (e: URISyntaxException) {
                    e.toString()
                }
            }

            val socketValidationOptional = { editable: Editable ->
                if (editable.count() == 0) {
                    null
                } else {
                    socketValidation(editable)
                }
            }

            val host1 =
                preferenceScreen.findPreference<Preference>("host1") as EditTextPreference?

            host1!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(ButtonValidation(editText, socketValidation))
            }

            val host2 =
                preferenceScreen.findPreference<Preference>("host2") as EditTextPreference?

            host2!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(ButtonValidation(editText, socketValidationOptional))
            }

            val host3 =
                preferenceScreen.findPreference<Preference>("host3") as EditTextPreference?

            host3!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(ButtonValidation(editText, socketValidationOptional))
            }

            val publicIp =
                preferenceScreen.findPreference<Preference>("visibleIp") as EditTextPreference?

            publicIp!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }

            val sendAddress1 =
                preferenceScreen.findPreference<Preference>("sendAddress1") as EditTextPreference?

            sendAddress1!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(ButtonValidation(editText, socketValidation))
            }

            val sendAddress2 =
                preferenceScreen.findPreference<Preference>("sendAddress2") as EditTextPreference?

            sendAddress2!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
                editText.addTextChangedListener(ButtonValidation(editText, socketValidationOptional))
            }

            val heartbeat =
                preferenceScreen.findPreference<Preference>("heartbeat") as EditTextPreference?

            heartbeat!!.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }
    }
}