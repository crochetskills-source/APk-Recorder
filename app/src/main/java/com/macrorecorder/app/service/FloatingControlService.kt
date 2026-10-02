package com.macrorecorder.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.macrorecorder.app.R
import com.macrorecorder.app.ui.MainActivity

/**
 * Floating overlay service that shows Record/Stop/Play controls on top of any app
 */
class FloatingControlService : Service() {

    companion object {
        const val CHANNEL_ID = "macro_recorder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.macrorecorder.STOP_FLOATING"
        const val ACTION_TOGGLE_RECORDING = "com.macrorecorder.TOGGLE_RECORDING"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var isViewAdded = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_RECORDING -> {
                toggleRecording()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification("Macro Recorder active"))
        showFloatingView()
        return START_STICKY
    }

    private fun showFloatingView() {
        if (isViewAdded) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_controls, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Make it draggable
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    if (isViewAdded) windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        setupButtons()
        windowManager.addView(floatingView, params)
        isViewAdded = true
    }

    private fun setupButtons() {
        val btnRecord = floatingView.findViewById<ImageButton>(R.id.btn_float_record)
        val btnStop = floatingView.findViewById<ImageButton>(R.id.btn_float_stop)
        val btnOpen = floatingView.findViewById<ImageButton>(R.id.btn_float_open)
        val tvStatus = floatingView.findViewById<TextView>(R.id.tv_float_status)

        updateFloatingUI(tvStatus, btnRecord, btnStop)

        btnRecord.setOnClickListener {
            if (!isDragging) {
                val service = MacroAccessibilityService.instance
                if (service != null) {
                    if (MacroAccessibilityService.isRecording) {
                        service.stopRecording()
                        // Broadcast to main app to show save dialog
                        val intent = Intent(MacroAccessibilityService.ACTION_RECORD_STOP)
                        sendBroadcast(intent)
                    } else {
                        service.startRecording()
                    }
                    updateFloatingUI(tvStatus, btnRecord, btnStop)
                } else {
                    tvStatus.text = "⚠ Enable Accessibility"
                }
            }
        }

        btnStop.setOnClickListener {
            if (!isDragging) {
                val service = MacroAccessibilityService.instance
                service?.stopPlayback()
                updateFloatingUI(tvStatus, btnRecord, btnStop)
            }
        }

        btnOpen.setOnClickListener {
            if (!isDragging) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        }
    }

    private fun updateFloatingUI(tv: TextView, btnRecord: ImageButton, btnStop: ImageButton) {
        when {
            MacroAccessibilityService.isRecording -> {
                tv.text = "● REC"
                btnRecord.setImageResource(R.drawable.ic_stop_record)
                btnStop.visibility = View.GONE
            }
            MacroAccessibilityService.isPlaying -> {
                tv.text = "▶ PLAY"
                btnRecord.visibility = View.GONE
                btnStop.visibility = View.VISIBLE
            }
            else -> {
                tv.text = "Ready"
                btnRecord.visibility = View.VISIBLE
                btnRecord.setImageResource(R.drawable.ic_record)
                btnStop.visibility = View.GONE
            }
        }
    }

    private fun toggleRecording() {
        val service = MacroAccessibilityService.instance ?: return
        if (MacroAccessibilityService.isRecording) {
            service.stopRecording()
            sendBroadcast(Intent(MacroAccessibilityService.ACTION_RECORD_STOP))
        } else {
            service.startRecording()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Macro Recorder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Macro Recorder",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Macro Recorder controls" }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isViewAdded) {
            windowManager.removeView(floatingView)
            isViewAdded = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
