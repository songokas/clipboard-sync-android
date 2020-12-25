package com.clipboard.sync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject




class FirstFragment : Fragment() {

    private var sync: ClipboardSync = ClipboardSync()

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toggle: SwitchCompat = view.findViewById(R.id.toggleButton)
        val textView = view.findViewById<TextView>(R.id.textview_first)

        val timerHandler = Handler(Looper.getMainLooper())

        val runnableCode = object : Runnable {
            override fun run() {
                textView.text = sync.status()
                timerHandler.postDelayed(this, 6000)
            }
        }

        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this.context)
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
                textView.text = sync.startSync(json.toString())
            } else {
                textView.text = sync.stopSync()
            }
            timerHandler.postDelayed(runnableCode, 6000)
        }
    }

}