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
        return json
    }
}