package com.umit.budgettracker.core.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.umit.budgettracker.MainActivity
import com.umit.budgettracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Local notifications only; nothing leaves the device. */
@Singleton
class AppNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Channel(val id: String, val title: String, val importance: Int) {
        BUDGET("budget_alerts", "Bütçe ve nakit uyarıları", NotificationManager.IMPORTANCE_DEFAULT),
        PRICES("price_alerts", "Fiyat, kur ve altın", NotificationManager.IMPORTANCE_DEFAULT),
        REMINDERS("reminders", "Hatırlatıcılar", NotificationManager.IMPORTANCE_DEFAULT),
        SUMMARY("weekly_summary", "Haftalık özet", NotificationManager.IMPORTANCE_LOW),
        CAPTURE("capture", "Yakalanan harcamalar", NotificationManager.IMPORTANCE_LOW)
    }

    fun notify(channel: Channel, id: Int, title: String, text: String, route: String? = null) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel.id, channel.title, channel.importance))
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val ID_INFLATION = 1101
        const val ID_CASH = 1102
        const val ID_PRE_PAYMENT = 1103
        const val ID_BANK_DAY = 1104
        const val ID_DEPOSIT = 1105
        const val ID_WEEKLY = 1106
        const val ID_FX_MOVE = 1107
        const val ID_CAPTURE = 1108
        const val ID_PRICE_ALERT_BASE = 2000
    }
}
