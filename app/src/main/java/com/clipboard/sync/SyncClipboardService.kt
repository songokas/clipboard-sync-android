package com.clipboard.sync

import android.app.*
import android.content.ClipboardManager
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class EventHandler
{

}

class SyncClipboardService: Service() {

    private var sync: ClipboardSync = ClipboardSync()
    private lateinit var timerHandler: Handler
//    private val listener = OnPrimaryClipChangedListener { startClipboardSend() }

    val ONGOING_NOTIFICATION_ID = 232323;

    override fun onCreate() {
        super.onCreate()
        timerHandler = Handler(Looper.getMainLooper())
//        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel("clipboard sync", "Clipboard sync background service")
                } else {
                    ""
                }

        val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
//                    notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    notificationIntent.action = Intent.ACTION_MAIN
                    notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
//                    PendingIntent.getBroadcast(this,1, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                }

        val input = intent?.getStringExtra("inputExtra")
// 1
        val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("clipboard sync")
                .setContentText(input)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        Log.d("foreground", "onstart")
// 2
        return START_STICKY
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

////    override fun o
//
//    private fun startClipboardSend()
//    {
////        val runnable = object : Runnable {
////            override fun run() {
//                Log.d("foreground", "foreground running")
//                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                val text = clipboard.primaryClip?.getItemAt(0)?.text
//                if (!text.isNullOrEmpty()) {
//                    Log.d("foreground", text.toString())
//                    sync.queue(text.toString())
//                }
////                timerHandler.postDelayed(this, 3000)
////            }
////        }
////        timerHandler.postDelayed(runnable, 3000)
//    }
}
