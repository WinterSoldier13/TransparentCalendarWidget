package com.antigravity.transparentcalendar

import android.app.KeyguardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EventNotificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        showOnLockScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_notification)

        val eventTitle = intent.getStringExtra("EVENT_TITLE") ?: "Event"
        val eventId = intent.getLongExtra("EVENT_ID", -1)

        val titleView = findViewById<TextView>(R.id.notification_title)
        titleView.text = eventTitle
        titleView.setOnClickListener {
            openEventDetails(eventId)
        }

        val detailsButton = findViewById<TextView>(R.id.notification_details)
        detailsButton.setOnClickListener {
            openEventDetails(eventId)
        }

        val dismissButton = findViewById<ImageButton>(R.id.dismiss_button)
        dismissButton.setOnClickListener {
            finish()
        }
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun openEventDetails(eventId: Long) {
        if (eventId != -1L) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
            }
            startActivity(intent)
        }
        finish()
    }
}
