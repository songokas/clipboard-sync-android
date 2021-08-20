package com.clipboard.sync

import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.nio.ByteBuffer

class SyncViewModel : ViewModel() {

    private val helper: MessageHelper = MessageHelper()
    private val sync: ClipboardSync = ClipboardSync()

    private val lastText = MutableLiveData<String>()
    val textChanges: LiveData<String> get() = lastText

    private val fileCount = MutableLiveData<Int>()
    val fileCountChanges: LiveData<Int> get() = fileCount

    private var serviceRunning: Boolean = false
    private var lastHash: String = ""
    private var isChecked: Boolean = false

    fun updateText(text: String) {
        lastText.value = text
    }

    fun updateFileCount(count: Int) {
        fileCount.value = count
    }

    fun isRunning(): Boolean {
        return sync.isRunning()
    }

    fun stopSync() {
        sync.stopSync()
    }

    fun isServiceRunning(): Boolean {
        return serviceRunning
    }

    fun sendClipboard(
        context: Context,
        textToUse: String,
    ) {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (textToUse.isNotEmpty()) {
            val clip = ClipData.newPlainText("simple text", textToUse)
            clipboard.setPrimaryClip(clip)
        }
        val clipItem = clipboard.primaryClip?.getItemAt(0)
        val clipItemText = clipItem?.coerceToText(context)
        if (clipItem == null || clipItemText.isNullOrEmpty()) {
            val status = context.resources.getString(R.string.empty_clipboard)
            updateText(status)
            return
        }

        val clipText = clipItemText.toString()
        if (textToUse.isEmpty() && isHashMatching(clipText)) {
            return
        }

        clipItem.uri?.let {
            helper.getFileName(context.contentResolver, it)?.let { fileName ->
                viewModelScope.launch {
                    sendFile(context, context.contentResolver, it, fileName)
                }
                val clip = ClipData.newPlainText("simple text", fileName)
                clipboard.setPrimaryClip(clip)
                updateHash(fileName)
            }
            return
        }

        if (sync.isRunning()) {
            viewModelScope.launch {
                val status = queueTextBuffer(clipText)
                updateText(status)
                updateHash(clipText)
            }
            return
        }

        viewModelScope.launch {
            val status = sendTextBuffer(createJson(context), clipText)
            updateText(status)
            updateHash(clipText)
        }
    }

    fun changeState(context: Context, start: Boolean): Boolean {
        Log.d("change state", start.toString())
        isChecked = if (start) {
            startSync(context)
        } else {
            stopSync(context)
        }
        return isChecked
    }


    fun handleSendFileAsync(context: Context, resolver: ContentResolver, it: Uri): Deferred<Unit> {
        Log.d("send file", it.toString())
        val fileName = helper.getFileName(resolver, it)!!
        updateText("Preparing to send file $fileName")
        return viewModelScope.async {
            val status = sendFile(context, resolver, it, fileName)
            updateText(status)
        }
    }


    fun moveAllFiles(context: AppCompatActivity) {
        val fileUris = getAllFiles(context, context.filesDir)
        if (fileUris.count() > 0) {
            saveFilesTo(context, fileUris)
        }
    }

    fun processStatus(context: Context) {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val statusStr = sync.status()
        val jsonResult = try {
            JSONObject(statusStr)
        } catch (e: JSONException) {
            updateText(e.toString())
            return
        }
        if (!jsonResult.getBoolean("state")) {
            return
        }

        updateText(jsonResult.optString("message"))

        val received = sync.receive()
        if (received.isEmpty()) {
            sendClipboard(context, "")
            return
        }

        var clip = ClipData.newPlainText("simple text", received)
        if (!received.startsWith("file://")) {
            clipboard.setPrimaryClip(clip)
            updateHash(received)
            return
        }

        var uriCreated = false
        val permissions = context.contentResolver.persistedUriPermissions
        val sharedDir = try {
            permissions.last().uri?.let {
                val prefs = PreferenceManager.getDefaultSharedPreferences(
                    context.applicationContext
                )
                if (prefs.getBoolean("useSharedDirectory", false)) DocumentFile.fromTreeUri(
                    context,
                    it
                ) else null
            }
        } catch (e: NoSuchElementException) {
            null
        }
        for (line in received.lines()) {
            val file = File(URI.create(line))
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".file_provider",
                file
            )
            if (sharedDir != null) {
                val sharedFile =
                    sharedDir.createFile(
                        context.contentResolver.getType(uri).orEmpty(),
                        file.name
                    )

                val to = sharedFile?.uri?.let { sharedUri ->
                    context.contentResolver.openOutputStream(
                        sharedUri
                    )
                }
                val from = context.contentResolver.openInputStream(uri)
                try {
                    from!!.copyTo(to!!)
                    file.delete()
                } catch (_: NullPointerException) {
                    updateText("Failed to create file ${file.name}")
                    continue
                }
            }
            if (!uriCreated) {
                clip = ClipData.newUri(context.contentResolver, "URI", uri)
                uriCreated = true
            } else {
                clip.addItem(ClipData.Item(uri))
            }
        }
        clipboard.setPrimaryClip(clip)
        updateHash(received)
        updateFileCount(
            if (sharedDir != null) {
                clip.itemCount
            } else {
                getAllFiles(
                    context,
                    context.filesDir
                ).count()
            }
        )
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

    private fun startSync(context: Context): Boolean {
        val statusStr = sync.startSync(createJson(context))
        val jsonResult = try {
            JSONObject(statusStr)
        } catch (e: JSONException) {
            updateText(e.toString())
            return false
        }

        val status = jsonResult.optString("message")
        updateText(status)

        val prefs = PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext
        )

        if (!jsonResult.optBoolean("state")) {
            return false
        } else if (prefs.getBoolean("notification", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                markServiceRunning(true)
                context.startForegroundService(
                    Intent(
                        context,
                        SyncClipboardService::class.java
                    )
                )
            }
        }
        return true
    }

    private fun stopSync(context: Context): Boolean {
        val status = sync.stopSync()
        val jsonResult = try {
            JSONObject(status)
        } catch (e: JSONException) {
            updateText(e.toString())
            return false
        }
        val message = jsonResult.optString("message")
        updateText(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.stopService(
                Intent(
                    context.applicationContext,
                    SyncClipboardService::class.java
                )
            )
            markServiceRunning(false)
        }
        return false
    }

    private suspend fun queueTextBuffer(text: String): String = withContext(Dispatchers.IO) {
        val arr = text.toByteArray()
        val buffer = ByteBuffer.allocateDirect(arr.size)
        buffer.put(arr)
        sync.queue(buffer, "text")
    }

    private suspend fun sendTextBuffer(
        json: String,
        text: String,
    ): String = withContext(Dispatchers.IO) {
        val arr = text.toByteArray()
        val buffer = ByteBuffer.allocateDirect(arr.size)
        buffer.put(arr)
        sync.send(json, buffer, "text")
    }

    private suspend fun sendFile(
        context: Context,
        resolver: ContentResolver,
        it: Uri,
        fileName: String
    ): String = withContext(Dispatchers.IO)
    {
        val inputStream = resolver.openInputStream(it) ?: return@withContext "Unable to send file"
        val arr = inputStream.readBytes()
        inputStream.close()

        val buffer = ByteBuffer.allocateDirect(arr.size)
        buffer.put(arr)
        if (sync.isRunning()) {
            sync.queue(buffer, fileName)
        } else {
            sync.send(createJson(context), buffer, fileName)
        }
    }

    private fun createJson(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext
        )
        val json = helper.prefsToJson(prefs, context.filesDir)
        return json.toString()
    }

    private fun saveFilesTo(context: AppCompatActivity, fileUris: ArrayList<Uri>) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            type = "*/*"
            flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        viewModelScope.launch {
            context.startActivityForResult(
                Intent.createChooser(shareIntent, "Save files to.."),
                Config.MOVE_FILES_INTENT,
            )
        }
    }

    private fun updateHash(text: String) {
        lastHash = helper.hashString(text)
    }

    private fun isHashMatching(text: String): Boolean {
        return lastHash == helper.hashString(text)
    }

    private fun markServiceRunning(running: Boolean) {
        serviceRunning = running
    }

}