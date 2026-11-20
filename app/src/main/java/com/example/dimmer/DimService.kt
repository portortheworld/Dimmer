package com.example.dimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager

class DimService : Service() {

    private var overlay: View? = null
    private lateinit var wm: WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val level = intent?.getIntExtra(EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
            ?: prefs.getInt(KEY_LEVEL, 50)

        startAsForeground()
        showOverlay(level)
        running = true
        return START_STICKY
    }

    private fun showOverlay(level: Int) {
        // Android 12+ memblokir sentuhan jika overlay lebih dari 80% opak,
        // jadi level dibatasi maksimum 80.
        val alpha = level.coerceIn(0, MAX_LEVEL) / 100f

        var view = overlay
        if (view == null) {
            view = View(this)
            view.setBackgroundColor(Color.BLACK)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(view, lp)
            overlay = view
        }
        view.alpha = alpha
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Dimmer", NotificationManager.IMPORTANCE_LOW)
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DimService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Dimmer aktif")
            .setContentText("Ketuk untuk mengatur kecerahan")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Matikan", stopIntent).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        running = false
        super.onDestroy()
    }

    companion object {
        const val PREFS = "dimmer"
        const val KEY_LEVEL = "level"
        const val EXTRA_LEVEL = "level"
        const val ACTION_STOP = "com.example.dimmer.STOP"
        const val MAX_LEVEL = 80
        private const val CHANNEL_ID = "dimmer"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false
    }
}
