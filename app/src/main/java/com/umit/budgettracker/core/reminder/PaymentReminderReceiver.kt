package com.umit.budgettracker.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.umit.budgettracker.core.work.WorkScheduler

class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PaymentReminderScheduler(context).scheduleNextReminder()
        // Inflation reminder, smart cash warning, pre-payment and deposit notes run as one worker.
        WorkScheduler(context.applicationContext).runDailyChecks()
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ödeme hatırlatmaları", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.umit.budgettracker.R.mipmap.ic_launcher)
            .setContentTitle("Bugünkü ödemelerini kontrol et")
            .setContentText("Abonelik, kredi ve sabit gider planını gözden geçir.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "payment_reminders"
        const val NOTIFICATION_ID = 901
    }
}
