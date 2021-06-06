package com.clipboard.sync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class MainFragment : Fragment() {
    private lateinit var textView: TextView
    private lateinit var toggleButton: SwitchCompat
    private lateinit var timerHandler: Handler

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        timerHandler = Handler(Looper.getMainLooper())
        val view = inflater.inflate(R.layout.main_fragment, container, false)
        textView = view.findViewById(R.id.text_view)
        toggleButton = view.findViewById(R.id.toggle_button)
        val sendButton: Button = view.findViewById(R.id.send_button)

        val mainActivity = activity as MainActivity

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                processUpdates()
            }
            val success = mainActivity.changeState(textView, isChecked)
            if (success != isChecked) {
                toggleButton.isChecked = success
            }
        }

        sendButton.setOnClickListener {
            mainActivity.sendClipboard(textView, toggleButton.isChecked)
        }

        return view
    }

    private fun processUpdates()
    {
        val runnable = object : Runnable {
            override fun run() {
                val pair = (activity as MainActivity).processStatus()
                textView.text = pair.first
                if (pair.second != toggleButton.isChecked) {
                    toggleButton.isChecked = pair.second
                }
                if (toggleButton.isChecked) {
                    timerHandler.postDelayed(this, 3000)
                }
            }
        }
        timerHandler.postDelayed(runnable, 3000)
    }
}