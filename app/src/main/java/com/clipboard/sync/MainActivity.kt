package com.clipboard.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    private var sync: ClipboardSync = ClipboardSync()
    private var currentHash: String = ""
    private val helper: MessageHelper = MessageHelper()

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

    fun sendClipboard(textView: TextView)
    {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text
        if (text.isNullOrEmpty()) {
            textView.text = resources.getString(R.string.empty_clipboard)
            return
        }

        Log.d("send clipboard once", text.toString())

        val prefs = PreferenceManager.getDefaultSharedPreferences(
            applicationContext
        )
        val json = helper.prefsToJson(prefs)
        val message = sync.send(json.toString(), text.toString())
        textView.text = message
        currentHash = text.toString()
    }

    fun changeState(textView: TextView, isChecked: Boolean): Boolean
    {
        if (isChecked) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                applicationContext
            )
            val json = helper.prefsToJson(prefs)
            val status = sync.startSync(json.toString())
            val jsonResult = JSONObject(status)

            textView.text = jsonResult.optString("message")

            if (!jsonResult.optBoolean("state")) {
                return false
            } else if (prefs.getBoolean("notification", false)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(
                        Intent(
                            applicationContext,
                            SyncClipboardService::class.java
                        )
                    )
                }
            }
            return true
        }

        val status = sync.stopSync()
        val jsonResult = JSONObject(status)
        textView.text = jsonResult.optString("message")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            stopService(Intent(applicationContext, SyncClipboardService::class.java))
        }
        return false
    }

    fun processStatus(): Pair<String, Boolean>
    {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val status = sync.status()

        val jsonResult = JSONObject(status)
        val clipboardStr = jsonResult.optString("clipboard")

        if (clipboardStr.isNotEmpty()) {
//            Log.d("set clipboard", clipboardStr)
            val clip = ClipData.newPlainText("simple text", clipboardStr)
            clipboard.setPrimaryClip(clip)
            currentHash = helper.hashString(clipboardStr)
        } else {
            val text = clipboard.primaryClip?.getItemAt(0)?.text
            if (!text.isNullOrEmpty()) {
                val expectedHash = helper.hashString(text.toString())
                if (currentHash != expectedHash) {
//                    Log.d("add clipboard to queue", text.toString())
                    sync.queue(text.toString())
                    currentHash = expectedHash
                }
            }
        }

        return Pair(jsonResult.optString("message"), jsonResult.optBoolean("state", false))
    }


}