package com.clipboard.sync

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


class MainFragment : Fragment() {
    private val viewModel: SyncViewModel by activityViewModels()
    private val mainScope = MainScope()

    private val selectFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { list: List<Uri?> ->
            activity?.let {
                mainScope.launch {
                    val act = it as MainActivity
                    viewModel.handleSendFilesAsync(act, act.contentResolver, list.filterNotNull()).await()
                }
            }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.main_fragment, container, false)
        val textView: TextView = view.findViewById(R.id.text_view)
        val toggleButton: SwitchCompat = view.findViewById(R.id.toggle_button)
//        val saveButton: Button = view.findViewById(R.id.save_button)
        val sendButton: Button = view.findViewById(R.id.send_button)
        val sendFilesButton: Button = view.findViewById(R.id.send_files_button)
        val editInput: EditText = view.findViewById(R.id.copy_input)
        val sendCertificateButton: Button = view.findViewById(R.id.send_certificate_button)
        val receiveCertificateButton: Button = view.findViewById(R.id.receive_certificate_button)

        val prefs = PreferenceManager.getDefaultSharedPreferences(
            requireContext()
        )
//        val fileCount = 0

//        val updateButton = { count: Int ->
//            val usingFileSaves = !prefs.getBoolean("useSharedDirectory", false);
//            if (usingFileSaves) {
//                saveButton.text = getString(R.string.save_files_button_text, count)
//                saveButton.isEnabled = true
//            } else {
//                saveButton.text = getString(R.string.received_files_button_text, count)
//                saveButton.isEnabled = false
//            }
//        }

//        updateButton(fileCount)

        viewModel.textChanges.observe(viewLifecycleOwner, Observer { message ->
            textView.text = message
        })

//        viewModel.fileCountChanges.observe(viewLifecycleOwner, Observer { count ->
//            updateButton(count)
//        })

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            activity?.let {
                val act = it as MainActivity
                val success = viewModel.changeState(act, isChecked)
                if (success != isChecked) {
                    toggleButton.isChecked = success
                }
                act.userStarted = success
                if (success) {
                    act.processUpdates(act)
                }
            }
        }

//        saveButton.setOnClickListener {
//            activity?.let {
//                if (!prefs.getBoolean("useSharedDirectory", false)) {
//                    viewModel.moveAllFiles(it as AppCompatActivity)
//                }
//            }
//        }

        sendButton.setOnClickListener {
            activity?.let {
                viewModel.sendClipboard(it, editInput.text.toString())
                editInput.setText("")
            }
        }

        sendCertificateButton.setOnClickListener {
            val certificate = prefs.getString("certificateChain", null)
            if (!certificate.isNullOrEmpty()) {
                activity?.let {
                    viewModel.sendCertificate(it, certificate)
                }
            } else {
                textView.text = getString(R.string.no_certificate_specified)
            }

        }

        receiveCertificateButton.setOnClickListener {
            activity?.let {
                val act = it as MainActivity
                viewModel.waitForCertificate()
                val success = viewModel.changeState(act, start = true)
                toggleButton.isChecked = success
                act.userStarted = success
                if (success) {
                    act.processUpdates(act)
                }
            }
        }

        sendFilesButton.setOnClickListener {
            selectFiles.launch("*/*")
        }
        return view
    }
}