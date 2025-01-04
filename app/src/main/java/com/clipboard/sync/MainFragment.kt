package com.clipboard.sync

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File


class MainFragment : Fragment() {
    private val viewModel: SyncViewModel by activityViewModels()
    private val mainScope = MainScope()

    private val selectFiles =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { list: List<Uri?> ->
            val files = list.filterNotNull()
            if (files.isNotEmpty()) {
                activity?.let {
                    mainScope.launch {
                        val act = it as MainActivity
                        viewModel.handleSendFilesAsync(act, act.contentResolver, files)
                            .await()
                    }
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
        val countView: TextView = view.findViewById(R.id.send_received_view)
        val toggleButton: SwitchCompat = view.findViewById(R.id.toggle_button)
        val saveButton: Button = view.findViewById(R.id.save_button)
        val sendButton: Button = view.findViewById(R.id.send_button)
        val sendFilesButton: Button = view.findViewById(R.id.send_files_button)
        val removeFilesButton: Button = view.findViewById(R.id.remove_files_button)
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
            viewModel.updateText("Started")
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

        removeFilesButton.setOnClickListener {
            context?.let {
                val dataDir = it.getExternalFilesDir("data") ?: File(it.filesDir, "data")
                val files = dataDir.listFiles()
                if (!files.isNullOrEmpty()) {
                    AlertDialog.Builder(it)
                        .setMessage("Do you really want to remove all files?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, { _, _ -> removeAllFiles(files) })
                        .setNegativeButton(android.R.string.no, null).show()
                } else {
                    viewModel.updateText("Directory is empty")
                }
            }
        }

        saveButton.setOnClickListener {
            context?.let {
                val dataDir = it.getExternalFilesDir("data") ?: File(it.filesDir, "data")
                val uris = getAllFiles(it, dataDir)
                Log.d("uris", "all files: $uris");
                if (uris.isNotEmpty()) {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        type = "*/*"
                    }
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(shareIntent, "Copy files"))
                } else {
                    viewModel.updateText("Directory is empty")
                }
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

fun removeAllFiles(files: Array<File>) {
    for (file in files) {
        file.deleteRecursively()
    }
}

fun getAllFiles(context: Context, directory: File): ArrayList<Uri> {
    val fileUris: ArrayList<Uri> = arrayListOf()
    for (filePath in directory.listFiles().orEmpty()) {
        if (filePath.isDirectory) {
            val newFiles = getAllFiles(context, filePath)
            fileUris.addAll(newFiles)
        } else {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".file_provider",
                    filePath
                )
                fileUris.add(uri)
            } catch (e: IllegalArgumentException) {
                Log.e("error adding", "${e.message} $filePath")
            } catch (e: StringIndexOutOfBoundsException) {
                Log.e("error adding", "${e.message} $filePath")
            }
        }
    }
    return fileUris
}

//fun openDefaultFileManager(dataDir: File) {
//    val intent = Intent(Intent.ACTION_VIEW)
//    intent.setComponent(
//        ComponentName(
//            "com.marc.files",
//            "nl.marc_apps.files.MainActivity"
//        )
//    )
//    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("file:///$dataDir"));  // Replace with the path to your folder
//    startActivity(intent)
//}