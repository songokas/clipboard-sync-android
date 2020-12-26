package com.clipboard.sync

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment


class MainFragment : Fragment() {
    private lateinit var textView: TextView
    private lateinit var toggleButton: SwitchCompat
    private lateinit var timerHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("statusText", textView.text.toString())
        outState.putBoolean("statusText", toggleButton.isChecked)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        timerHandler = Handler(Looper.getMainLooper())
        val view = inflater.inflate(R.layout.main_fragment, container, false)
        textView = view.findViewById(R.id.text_view)
        toggleButton = view.findViewById(R.id.toggle_button)
        val mainActivity = activity as MainActivity

        if (savedInstanceState != null) {
            textView.text = savedInstanceState.getString("statusText")
            toggleButton.isChecked = savedInstanceState.getBoolean("buttonState", false)
        }

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startLog()
            }
            mainActivity.changeState(textView, isChecked)
        }
        return view
    }

    private fun startLog()
    {
        val runnable = object : Runnable {
            override fun run() {
                textView.text = (activity as MainActivity).getStatus()
                timerHandler.postDelayed(this, 6000)
            }
        }
        timerHandler.postDelayed(runnable, 6000)
    }
}