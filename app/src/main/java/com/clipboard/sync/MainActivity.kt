package com.clipboard.sync

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var sync: ClipboardSync = ClipboardSync()

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

    override  fun onBackPressed() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
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

    fun changeState(textView: TextView, isChecked: Boolean)
    {
        if (isChecked) {
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
            textView.text = status
        } else {
            textView.text = sync.stopSync()
        }
    }

    fun getStatus(): String
    {
        return sync.status()
    }
}