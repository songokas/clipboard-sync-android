package com.clipboard.sync

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class MessageHelper {
    fun hashString(input: String): String {
        return MessageDigest
            .getInstance("SHA1")
            .digest(input.toByteArray())
            .fold("", { str, it -> str + "%02x".format(it) })
    }

    fun prefsToJson(prefs: SharedPreferences): JSONObject
    {
        val json = JSONObject()
        json.put("key", prefs.getString("key", null))
        json.put("group", prefs.getString("group", null))
        json.put("protocol", prefs.getString("protocol", null))
        val arr = JSONArray()
        if (prefs.getString("host1", "")!!.isNotEmpty()) {
            arr.put(prefs.getString("host1", null))
        }
        if (prefs.getString("host2", "")!!.isNotEmpty()) {
            arr.put(prefs.getString("host2", null))
        }
        if (prefs.getString("host3", "")!!.isNotEmpty()) {
            arr.put(prefs.getString("host3", null))
        }
        json.put("hosts", arr)

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
        val heartbeat = try {
            heartbeatStr?.toInt()
        } catch (e: NumberFormatException) {
            0
        }
        json.put("heartbeat", heartbeat);
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
        return json
    }
}