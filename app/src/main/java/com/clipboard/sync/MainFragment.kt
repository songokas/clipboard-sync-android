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
import androidx.preference.PreferenceManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

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

    private val showFiles = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.main_fragment, container, false)
        val textView: TextView = view.findViewById(R.id.text_view)
        val countView: TextView = view.findViewById(R.id.send_received_view)
        val toggleButton: SwitchCompat = view.findViewById(R.id.toggle_button)
        val saveButton: Button = view.findViewById(R.id.save_button)
        val sendButton: Button = view.findViewById(R.id.send_button)
        val sendFilesButton: Button = view.findViewById(R.id.send_files_button)
        val editInput: EditText = view.findViewById(R.id.copy_input)
        val sendCertificateButton: Button = view.findViewById(R.id.send_certificate_button)
        val receiveCertificateButton: Button = view.findViewById(R.id.receive_certificate_button)

        val prefs = PreferenceManager.getDefaultSharedPreferences(
            requireContext()
        )

        viewModel.textChanges.observe(viewLifecycleOwner) { message ->
            textView.text = "$message\n${textView.text}"
        }

        viewModel.clipboardStateChanges.observe(viewLifecycleOwner) { message ->
            if (message == ClipboardState.CertificateReceived) {
                toggleButton.isChecked = false
            }
        }

        saveButton.text = getString(R.string.save_files_button_text, 0)
        viewModel.fileCountChanges.observe(viewLifecycleOwner) { count ->
            saveButton.text = getString(R.string.save_files_button_text, count)
        }

        countView.text = getString(R.string.sent_received_text, 0, 0)
        viewModel.statusCountChanges.observe(viewLifecycleOwner) { count ->
            countView.text = getString(R.string.sent_received_text, count.sent, count.received)
        }

        if (viewModel.isRunning()) {
            toggleButton.isChecked = true
            textView.text = "Started"
        }


        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            activity?.let {
                val act = it as MainActivity
                val success = viewModel.changeState(act, isChecked)
                act.userStarted = success
                if (success) {
                    viewModel.updateText("Started")
                    act.processUpdates(act)
                } else {
                    viewModel.updateText("Stopped")
                }
            }
        }

        saveButton.setOnClickListener {
            context?.let {
                val dataDir = it.getExternalFilesDir("data") ?: File(it.filesDir, "data")
                showFiles.launch(Uri.fromFile(dataDir))
            }
        }

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
                viewModel.updateText(getString(R.string.no_certificate_specified))
            }
        }

        receiveCertificateButton.setOnClickListener {
            activity?.let {
                viewModel.waitForCertificate()
                toggleButton.isChecked = true
            }
        }

        sendFilesButton.setOnClickListener {
            selectFiles.launch("*/*")
        }
        return view
    }
}