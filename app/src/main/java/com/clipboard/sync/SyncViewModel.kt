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
data class CertificateInfo(val dnsNames: Array<String>, val serial: String)
data class StatusCount(val sent: Int, val received: Int)

class SyncViewModel : ViewModel() {

    private val helper: MessageHelper = MessageHelper()
    private val sync: ClipboardSync = ClipboardSync()

    private val lastText = MutableLiveData<String>()
    val textChanges: LiveData<String> get() = lastText

    private val statusCount = MutableLiveData<StatusCount>()
    val statusCountChanges: LiveData<StatusCount> get() = statusCount

    private val fileCount = MutableLiveData<Int>()
    val fileCountChanges: LiveData<Int> get() = fileCount

    private var serviceRunning: Boolean = false
    private var lastHash: String = ""
    private var isChecked: Boolean = false
    private var receiveCertificate: Boolean = false

    fun updateText(text: String) {
        if (text.isNotEmpty()) {
            lastText.value = text
        }
    }

    private fun updateStatusCount(sent: Int, received: Int) {
        statusCount.value = StatusCount(sent, received)
    }

    private fun updateFileCount(count: Int) {
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

    fun certificateInfo(certificate: String): CertificateInfo? {
        val certificates = sync.certificateInfo(certificate)
        try {
            val obj = JSONObject(certificates)
            val names = obj.getJSONArray("dns_names")
            return CertificateInfo(
                Array(names.length()) { i -> names.getString(i) },
                obj.getString("serial"),
            )
        } catch (e: JSONException) {
            Log.d("Failed to parse json", e.toString())
            return null
        }

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

    fun processStatus(context: Context): Boolean {
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

        val message = jsonResult.optString("error")
        updateText(message)

        val statusCount = jsonResult.optJSONObject("status_count")
        if (statusCount != null) {
            val sentCount = statusCount.optInt("sent")
            val receivedCount = statusCount.optInt("received")
            updateStatusCount(sentCount, receivedCount)
        }

        val received = sync.receive();
        if (received.isEmpty()) {
            sendClipboard(context, "")
            return true
        }

        Log.d("clipboard", "Received clipboard $received");

        // handle certificate
        if (receiveCertificate && received.startsWith("-----BEGIN CERTIFICATE-----")) {
            val certInfo = certificateInfo(received)
            if (certInfo == null) {
                updateText("Unknown certificate received. Ignoring")
                return true
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                context.applicationContext
            )
            val remoteCertificates = prefs.getString("remoteCertificates", null)
            val comment = "# ${certInfo.serial} ${certInfo.dnsNames.joinToString(",")}"
            if (remoteCertificates.isNullOrEmpty()) {
                prefs.edit().putString("remoteCertificates", "$comment\n$received")
                    .apply()
            } else {
                if (!remoteCertificates.contains(received)) {
                    prefs.edit().putString("remoteCertificates", "$comment\n$received\n${remoteCertificates}")
                        .apply()
                }
            }
            updateText("${certInfo.serial} ${certInfo.dnsNames.joinToString(",")}")
            updateText("Certificate received")
            receiveCertificate = false
            return false
        }

        // files are handled by the receiver
        if (received.startsWith("file://")) {
            updateFileCount(received.lines().count())
            return true
        }

        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("simple text", received)
        clipboard.setPrimaryClip(clip)
        updateHash(received)

        return true
    }

    fun sendCertificate(context: Context, certificate: String) {
        if (sync.isRunning()) {
            viewModelScope.launch {
                queueTextBuffer(certificate)
            }
            return
        }

        viewModelScope.launch {
            val json = createJsonObject(context)
            json.put("do_not_verify_server_certificate", true);
            val status = sendTextBuffer(json.toString(), certificate, "public_key")
            updateText(status)
        }
    }

    fun waitForCertificate() {
        receiveCertificate = true
    }

    private fun startSync(context: Context): Boolean {
        var json = createJsonObject(context)
        if (receiveCertificate) {
            json.put("do_not_verify_client_certificate", true)
        }
        val statusStr = sync.startSync(json.toString())
        val jsonResult = try {
            JSONObject(statusStr)
        } catch (e: JSONException) {
            updateText(e.toString())
            return false
        }

        val state = jsonResult.optBoolean("state")

        val status = jsonResult.optString("error")
        updateText(status)

        val prefs = PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext
        )

        if (!state) {
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

        val message = jsonResult.optString("error")
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
        messageType: String = "text",
    ): String = withContext(Dispatchers.IO) {
        sync.send(json, text.toByteArray(), messageType, 5000)
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