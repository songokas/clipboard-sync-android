package com.clipboard.sync

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log

class SyncActionReceiver : BroadcastReceiver() {
    private var sync: ClipboardSync = ClipboardSync()
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("receiver", "foreground running")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text
        if (!text.isNullOrEmpty()) {
            Log.d("receiver clipboard", text.toString())
            sync.queue(text.toString())
        }
        //This is used to close the notification tray
        val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        context.sendBroadcast(it)
    }
}