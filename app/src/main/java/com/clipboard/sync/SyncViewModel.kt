package com.clipboard.sync

import android.content.*
import android.content.Context.CLIPBOARD_SERVICE
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.HashMap

data class Certificates(val privateKey: String, val certificateChain: String, val subject: String?)

class SyncViewModel : ViewModel() {

    private val helper: MessageHelper = MessageHelper()
    private val sync: ClipboardSync = ClipboardSync()

    private val lastText = MutableLiveData<String>()
    val textChanges: LiveData<String> get() = lastText

    private val fileCount = MutableLiveData<Int>()
//    val fileCountChanges: LiveData<Int> get() = fileCount

    private var serviceRunning: Boolean = false
    private var lastHash: String = ""
    private var isChecked: Boolean = false
    private var receiveCertificate: Boolean = false

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

    fun generateCertificates(): Certificates? {
        val certificates = sync.generateCertificates()
        val jsonResult = try {
            JSONObject(certificates)
        } catch (e: JSONException) {
            Log.d("Failed to parse json", e.toString())
            return null
        }
        return Certificates(
            jsonResult.getString("private_key"),
            jsonResult.getString("certificate_chain"),
            jsonResult.optString("subject")
        )
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


    fun handleSendFileAsync(context: Context, resolver: ContentResolver, uri: Uri): Deferred<Unit> {
        Log.d("send file", uri.toString())
        val fileName = helper.getFileName(resolver, uri)!!
        updateText("Preparing to send file $fileName")
        return viewModelScope.async {
            val status = sendFile(context, resolver, uri, fileName)
            updateText(status)
        }
    }

    fun handleSendFilesAsync(context: Context, resolver: ContentResolver, uris: List<Uri>): Deferred<Unit> {
        return viewModelScope.async {
            val status = sendFiles(context, resolver, uris)
            updateText(status)
        }
    }

//    fun moveAllFiles(context: AppCompatActivity) {
//        val fileUris = getAllFiles(context, context.filesDir)
//        if (fileUris.isNotEmpty()) {
//            saveFilesTo(context, fileUris)
//        }
//    }

    fun processStatus(context: Context): Boolean {
        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val statusStr = sync.status()
        val jsonResult = try {
            JSONObject(statusStr)
        } catch (e: JSONException) {
            updateText(e.toString())
            return false
        }
        if (!jsonResult.getBoolean("state")) {
            return false
        }

        updateText(jsonResult.optString("message"))

//        val prefs = PreferenceManager.getDefaultSharedPreferences(
//            context.applicationContext
//        )

//        val dataDir = context.getExternalFilesDir("data")?.toString() ?: (context.filesDir.toString() + "/data")

        // If external storage is used there is no easy way to obtain
        // directory path from uri, hence we always write to private data
        // and then copy files
        val received = sync.receive();

        if (received.isEmpty()) {
            sendClipboard(context, "")
            return true
        }

        Log.d("clipboard", "Received clipboard $received");

        // handle certificate
        if (receiveCertificate && received.startsWith("-----BEGIN CERTIFICATE-----")) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                context.applicationContext
            )
            val remoteCertificates = prefs.getString("remoteCertificates", null)
            if (remoteCertificates.isNullOrEmpty()) {
                prefs.edit().putString("remoteCertificates", received)
                    .apply()
            } else {
                prefs.edit().putString("remoteCertificates", "$remoteCertificates\n$received")
                    .apply()
            }
            receiveCertificate = false
            return false
        }

        // files are handled by the receiver
        if (received.startsWith("file://")) {
            return true
        }

        val clip = ClipData.newPlainText("simple text", received)
        clipboard.setPrimaryClip(clip)
        updateHash(received)

        return true

//        var clip = ClipData.newPlainText("simple text", received)
//
//        var uriCreated = false
//        val permissions = context.contentResolver.persistedUriPermissions
//        val sharedDir = try {
//            permissions.last().uri?.let {
//
//                if (prefs.getBoolean("useSharedDirectory", false)) DocumentFile.fromTreeUri(
//                    context,
//                    it
//                ) else null
//            }
//        } catch (e: NoSuchElementException) {
//            null
//        }
//
//
//        for (line in received.lines()) {
//            val file = File(URI.create(line))
//            val uri = FileProvider.getUriForFile(
//                context,
//                BuildConfig.APPLICATION_ID + ".file_provider",
//                file
//            )
//            if (sharedDir != null) {
//                val sharedFile =
//                    sharedDir.createFile(
//                        context.contentResolver.getType(uri).orEmpty(),
//                        file.name
//                    )
//
//                val to = sharedFile?.uri?.let { sharedUri ->
//                    context.contentResolver.openOutputStream(
//                        sharedUri
//                    )
//                }
//                val from = context.contentResolver.openInputStream(uri)
//                try {
//                    from!!.copyTo(to!!)
//                    file.delete()
//                } catch (_: NullPointerException) {
//                    updateText("Failed to create file ${file.name}")
//                    continue
//                }
//            }
//            if (!uriCreated) {
//                clip = ClipData.newUri(context.contentResolver, "URI", uri)
//                uriCreated = true
//            } else {
//                clip.addItem(ClipData.Item(uri))
//            }
//        }
//        clipboard.setPrimaryClip(clip)
//        updateHash(received)
//        updateFileCount(
//            if (sharedDir != null) {
//                clip.itemCount
//            } else {
//                getAllFiles(
//                    context,
//                    context.filesDir
//                ).count()
//            }
//        )
    }
//
//    fun getAllFiles(context: Context, directory: File): ArrayList<Uri> {
//        val fileUris: ArrayList<Uri> = arrayListOf()
//        for (filePath in directory.listFiles().orEmpty()) {
//            if (filePath.isDirectory) {
//                val newFiles = getAllFiles(context, filePath)
//                fileUris.addAll(newFiles)
//            } else {
//                try {
//                    val uri = FileProvider.getUriForFile(
//                        context,
//                        BuildConfig.APPLICATION_ID + ".file_provider",
//                        filePath
//                    )
//                    fileUris.add(uri)
//                } catch (e: IllegalArgumentException) {
//                    Log.e("error adding", "${e.message} $filePath")
//                } catch (e: StringIndexOutOfBoundsException) {
//                    Log.e("error adding", "${e.message} $filePath")
//                }
//            }
//        }
//        return fileUris
//    }

    fun sendCertificate(context: Context, certificate: String) {
        if (sync.isRunning()) {
            viewModelScope.launch {
                queueTextBuffer(certificate)
            }
            return
        }

        viewModelScope.launch {
            val json = createJsonObject(context)
            val status = sendTextBuffer(json.toString(), certificate)
            updateText(status)
        }
    }

    fun waitForCertificate() {
        receiveCertificate = true
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
        sync.queue(text.toByteArray(), "text")
    }

    private suspend fun sendTextBuffer(
        json: String,
        text: String,
    ): String = withContext(Dispatchers.IO) {
        sync.send(json, text.toByteArray(), "text", 5000)
    }

    private suspend fun sendFile(
        context: Context,
        resolver: ContentResolver,
        uri: Uri,
        fileName: String
    ): String = withContext(Dispatchers.IO)
    {
        val inputStream = resolver.openInputStream(uri) ?: return@withContext "Unable to send file"
        val arr = inputStream.readBytes()
        inputStream.close()

        if (sync.isRunning()) {
            sync.queue(arr, fileName)
        } else {
            sync.send(createJson(context), arr, fileName, 20000)
        }
    }

    private suspend fun sendFiles(
        context: Context,
        resolver: ContentResolver,
        uris: List<Uri>,
    ): String = withContext(Dispatchers.IO)
    {
        var map: HashMap<String, ByteArray> = hashMapOf()
        for (uri in uris) {
            val fileName = helper.getFileName(resolver, uri) ?: return@withContext "Unable to obtain file name $uri"
            val inputStream = resolver.openInputStream(uri) ?: return@withContext "Unable open file $fileName"
            val arr = inputStream.readBytes()
            inputStream.close()
            map[fileName] = arr
        }
        if (sync.isRunning()) {
            sync.queueFiles(map)
        } else {
            sync.sendFiles(createJson(context), map, 20000)
        }
    }

    private fun createJson(context: Context): String {
        val json = createJsonObject(context)
        return json.toString()
    }

    private fun createJsonObject(context: Context): JSONObject {
        val prefs = PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext
        )
        val dataDir = context.getExternalFilesDir("data") ?: File(context.filesDir, "data")
        return helper.prefsToJson(prefs, dataDir)
    }

//    private fun saveFilesTo(context: AppCompatActivity, fileUris: ArrayList<Uri>) {
//        val shareIntent = Intent().apply {
//            action = Intent.ACTION_SEND_MULTIPLE
//            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
//            type = "*/*"
//            flags =
//                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//        }
//        viewModelScope.launch {
//            context.startActivityForResult(
//                Intent.createChooser(shareIntent, "Save files to.."),
//                Config.MOVE_FILES_INTENT,
//            )
//        }
//    }

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