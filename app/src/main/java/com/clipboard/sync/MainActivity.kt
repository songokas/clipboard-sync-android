package com.clipboard.sync

import android.R.attr.label
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject



class MainActivity : AppCompatActivity() {

    private var sync: ClipboardSync = ClipboardSync()
    private var last: String = ""
//    private lateinit var timerHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<MainFragment>(R.id.main_fragment)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun changeState(textView: TextView, buttonView: SwitchCompat)
    {
        if (buttonView.isChecked) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val json = JSONObject();
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

            val status = sync.startSync(json.toString())
            val jsonResult = JSONObject(status)
            textView.text = jsonResult.optString("message")
            if (!jsonResult.optBoolean("state")) {
                buttonView.isChecked = false
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(Intent(applicationContext, SyncClipboardService::class.java))
                }
            }

//            startClipboardSend()

//            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//            clipboard.addPrimaryClipChangedListener {
//                Log.d("clipboard changed")
//                sync.queue(clipboard.primaryClip?.getItemAt(0)?.text.toString())
//            }

        } else {
            val status = sync.stopSync()
            val jsonResult = JSONObject(status)
            textView.text = jsonResult.optString("message")
        }
    }

//    private fun startClipboardSend()
//    {
//        val runnable = object : Runnable {
//            override fun run() {
//                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                val text = clipboard.primaryClip?.getItemAt(0)?.text.toString()
//                sync.queue(text)
//                timerHandler.postDelayed(this, 3000)
//            }
//        }
//        timerHandler.postDelayed(runnable, 3000)
//    }

    fun getStatus(): String
    {
        Log.d("status", "called")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager


        val status = sync.status()
        val jsonResult = JSONObject(status)
        val clipboardStr = jsonResult.optString("clipboard");

        if (clipboardStr.isNotEmpty()) {
            Log.d("status set", clipboardStr)
            val clip = ClipData.newPlainText("simple text", clipboardStr)
            clipboard.setPrimaryClip(clip)
        } else {
            val text = clipboard.primaryClip?.getItemAt(0)?.text
            if (!text.isNullOrEmpty()) {
                Log.d("status queue", text.toString())
                sync.queue(text.toString())
            }
        }

        return jsonResult.getString("message")
    }


}