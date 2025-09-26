package com.grismund.ringertap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.widget.RemoteViews
import com.grismund.ringertap.R
import androidx.core.content.edit

class RingerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_RINGER = "com.grismund.ringertap.TOGGLE_RINGER"
        private const val PREFS_NAME = "RingerWidgetPrefs"
        private const val PREF_CURRENT_MODE = "current_mode"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
            // Update all widgets when the ringer mode changes
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, RingerWidgetProvider::class.java)
            )

            // Force update of all widgets
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        } else if (ACTION_TOGGLE_RINGER == intent.action) {
            toggleRingerMode(context)

            // Update all widgets
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, RingerWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.ringer_widget)

        // Get current ringer mode
        val currentMode = getCurrentRingerMode(context)

        // Update icon, text, and background color based on current mode
        when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_ringer_normal)
                views.setTextViewText(R.id.widget_text, context.getString(R.string.ringer_normal))
                views.setInt(R.id.widget_root, "setBackgroundColor", androidx.core.content.ContextCompat.getColor(context, R.color.ringer_normal))
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_ringer_vibrate)
                views.setTextViewText(R.id.widget_text, context.getString(R.string.ringer_vibrate))
                views.setInt(R.id.widget_root, "setBackgroundColor", androidx.core.content.ContextCompat.getColor(context, R.color.ringer_vibrate))
            }
            AudioManager.RINGER_MODE_SILENT -> {
                views.setImageViewResource(R.id.widget_icon, R.drawable.ic_ringer_silent)
                views.setTextViewText(R.id.widget_text, context.getString(R.string.ringer_silent))
                views.setInt(R.id.widget_root, "setBackgroundColor", androidx.core.content.ContextCompat.getColor(context, android.R.color.black))
            }
        }

        // Set up click listener on the entire widget
        val intent = Intent(context, RingerWidgetProvider::class.java)
        intent.action = ACTION_TOGGLE_RINGER
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getCurrentRingerMode(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.ringerMode
    }

    private fun toggleRingerMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentMode = audioManager.ringerMode

        val nextMode = when (currentMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }

        // Check if we can set the ringer mode
        if (canChangeRingerMode(context, nextMode)) {
            audioManager.ringerMode = nextMode
            saveCurrentMode(context, nextMode)

            // Trigger vibration or sound based on the mode
            when (nextMode) {
                AudioManager.RINGER_MODE_VIBRATE -> vibrate(context)
                AudioManager.RINGER_MODE_NORMAL -> playSound(context)
            }
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val vibrationEffect = android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                vibrator.vibrate(vibrationEffect)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(200) // Fallback for older devices
            }
        }
    }

    private fun playSound(context: Context) {
        val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val ringtone = android.media.RingtoneManager.getRingtone(context, notificationUri)
        ringtone?.play()
    }

    private fun canChangeRingerMode(context: Context, mode: Int): Boolean {
        // For silent mode on Android 6+, we need Do Not Disturb permission
        if (mode == AudioManager.RINGER_MODE_SILENT
        ) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            return notificationManager.isNotificationPolicyAccessGranted
        }

        return true
    }

    private fun saveCurrentMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(PREF_CURRENT_MODE, mode) }
    }
}
