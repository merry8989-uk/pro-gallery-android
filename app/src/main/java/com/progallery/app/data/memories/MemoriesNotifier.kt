package com.progallery.app.data.memories

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.progallery.app.MainActivity
import com.progallery.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The daily memories reminder.
 *
 * Says one thing — how long ago today's pictures were taken — and opens Memories when tapped.
 * It is not sent when there is nothing to remember, because a notification that says "nothing
 * today" is worse than silence.
 */
@Singleton
class MemoriesNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_ID = "memories"

        /** The extra that tells the app to open Memories when it is launched. */
        const val EXTRA_OPEN_MEMORIES = "com.progallery.app.OPEN_MEMORIES"

        private const val NOTIFICATION_ID = 7100
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Memories",
                // Default importance, so it appears without sound or a heads-up banner. A
                // reminder about the past is not urgent.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Pictures from this day in earlier years"
            },
        )
    }

    /** True when the app is allowed to post notifications at all. */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Posts the reminder, if there is anything to remember.
     *
     * @return true when a notification was actually posted, so the caller can say so.
     */
    fun notifyIfAnything(memories: Memories, today: LocalDate): Boolean {
        val group = memories.onThisDay.firstOrNull() ?: return false
        if (!canNotify()) return false

        ensureChannel()

        val years = group.yearsAgo
        val title = "$years ${if (years == 1) "year" else "years"} ago today"
        val date = group.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        val count = memories.itemCount
        val text = "$date · ${if (count == 1) "1 picture" else "$count pictures"}"

        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_MEMORIES, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_memories_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
