package com.example.dimmer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var percent: TextView
    private lateinit var seek: SeekBar
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(DimService.PREFS, MODE_PRIVATE)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Dimmer"
            textSize = 32f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        root.addView(status)

        percent = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(percent)

        seek = SeekBar(this).apply {
            max = DimService.MAX_LEVEL
            progress = prefs.getInt(DimService.KEY_LEVEL, 50).coerceIn(0, DimService.MAX_LEVEL)
            setPadding(pad, pad, pad, pad)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    percent.text = "Redup: $value%"
                    if (fromUser) {
                        prefs.edit().putInt(DimService.KEY_LEVEL, value).apply()
                        if (DimService.running) startDim(value)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(seek)
        percent.text = "Redup: ${seek.progress}%"

        toggle = Button(this).apply {
            setOnClickListener { onToggle() }
        }
        root.addView(toggle)

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onToggle() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (DimService.running) {
            stopService(Intent(this, DimService::class.java))
            toggle.postDelayed({ refresh() }, 200)
        } else {
            startDim(seek.progress)
            toggle.postDelayed({ refresh() }, 200)
        }
    }

    private fun startDim(level: Int) {
        startForegroundService(
            Intent(this, DimService::class.java).putExtra(DimService.EXTRA_LEVEL, level)
        )
    }

    private fun refresh() {
        val allowed = Settings.canDrawOverlays(this)
        when {
            !allowed -> {
                status.text = "Izin \"Tampil di atas aplikasi lain\" belum diberikan"
                toggle.text = "Beri izin"
            }
            DimService.running -> {
                status.text = "Dimmer sedang aktif"
                toggle.text = "Matikan"
            }
            else -> {
                status.text = "Dimmer mati"
                toggle.text = "Nyalakan"
            }
        }
    }
}
