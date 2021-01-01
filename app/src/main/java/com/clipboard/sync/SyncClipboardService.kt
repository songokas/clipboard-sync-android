package com.clipboard.sync

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class SyncClipboardService: Service() {

    val ONGOING_NOTIFICATION_ID = 232323;

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
                    notificationIntent.action = Intent.ACTION_MAIN
                    notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
//                    PendingIntent.getBroadcast(this,1, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                }

        val input = intent?.getStringExtra("inputExtra")
        val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("clipboard sync")
                .setContentText(input)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
        Log.d("foreground", "onstart")
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
}
