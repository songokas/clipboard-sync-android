package com.clipboard.sync

import android.content.ContentResolver
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest


class MessageHelper {
    fun hashString(input: String): String {
        return MessageDigest
            .getInstance("SHA1")
            .digest(input.toByteArray())
            .fold("", { str, it -> str + "%02x".format(it) })
    }

    fun prefsToJson(prefs: SharedPreferences, appDir: File): JSONObject {
        val json = JSONObject()
        json.put("key", prefs.getString("key", null))
        json.put("group", prefs.getString("group", null))
        json.put("protocol", prefs.getString("protocol", null))
        val allHosts = JSONObject()
        val addr1 = prefs.getString("host1", "").orEmpty()
        if (addr1.isNotEmpty()) {
            val hosts = addr1.split('=', limit = 1);
            allHosts.put(hosts[0], hosts.getOrNull(1) ?: JSONObject.NULL)
        }
        val addr2 = prefs.getString("host2", "").orEmpty()
        if (addr2.isNotEmpty()) {
            val hosts = addr2.split('=', limit = 1);
            allHosts.put(hosts[0], hosts.getOrNull(1) ?: JSONObject.NULL)
        }
        val addr3 = prefs.getString("host3", "").orEmpty()
        if (addr3.isNotEmpty()) {
            val hosts = addr3.split('=', limit = 1);
            allHosts.put(hosts[0], hosts.getOrNull(1) ?: JSONObject.NULL)
        }
        json.put("hosts", allHosts)

        val sendArr = JSONArray()
        if (prefs.getString("sendAddress1", "")!!.isNotEmpty()) {
            sendArr.put(prefs.getString("sendAddress1", null))
        }
        if (prefs.getString("sendAddress2", "")!!.isNotEmpty()) {
            sendArr.put(prefs.getString("sendAddress2", null))
        }
        json.put("send_using_address", sendArr)

        val bindArr = JSONArray()
        if (prefs.getString("bindAddress1", "")!!.isNotEmpty()) {
            bindArr.put(prefs.getString("bindAddress1", null))
        }
        json.put("bind_address", bindArr)
        if (prefs.getString("visibleIp", "")!!.isNotEmpty()) {
            json.put("visible_ip", prefs.getString("visibleIp", null))
        }
        val heartbeatStr = prefs.getString("heartbeat", "0")
        try {
            val heartbeat = heartbeatStr?.toInt()
            json.put("heartbeat", heartbeat);

        } catch (e: NumberFormatException) {
        }
        val relay = JSONObject()
        if (prefs.getString("relayServer", "")!!.isNotEmpty()) {
            relay.put("host", prefs.getString("relayServer", null))
        }
        if (prefs.getString("relayPublicKey", "")!!.isNotEmpty()) {
            relay.put("public_key", prefs.getString("relayPublicKey", null))
        }
        if (relay.length() > 1) {
            json.put("relay", relay)
        }
        if (!appDir.exists()) {
            appDir.mkdir()
        }
        json.put("app_dir", appDir.absolutePath)

        if (prefs.getString("privateKey", "")!!.isNotEmpty()) {
            json.put("private_key", prefs.getString("privateKey", null))
        }
        if (prefs.getString("certificateChain", "")!!.isNotEmpty()) {
            json.put("certificate_chain", prefs.getString("certificateChain", null))
        }
        if (prefs.getString("remoteCertificates", "")!!.isNotEmpty()) {
            json.put("remote_certificates", prefs.getString("remoteCertificates", null))
        }

        val maxReceiveSize = prefs.getString("maxReceiveSize", "0")
        try {
            val heartbeat = maxReceiveSize?.toInt()
            json.put("max_receive_size", heartbeat);
        } catch (e: NumberFormatException) {
        }

        val maxFileSize = prefs.getString("maxFileSize", "0")
        try {
            val heartbeat = maxFileSize?.toInt()
            json.put("max_file_size", heartbeat);
        } catch (e: NumberFormatException) {
        }

        return json
    }

    fun getFileName(resolver: ContentResolver, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme.equals("content")) {
            val maybeCursor: Cursor? = resolver.query(uri, null, null, null, null)
            maybeCursor.use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    result = cursor.getString(columnIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result!!.substring(cut + 1)
            }
        }
        return result
    }
}