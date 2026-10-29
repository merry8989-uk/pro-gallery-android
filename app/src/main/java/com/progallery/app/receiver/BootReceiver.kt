package com.progallery.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.progallery.app.data.memories.MemoriesScheduler
import com.progallery.app.data.trash.TrashPurgeScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Puts the app's scheduled work back after the phone restarts.
 *
 * Both of the app's scheduled jobs — the morning memories notification and the trash purge — are
 * registered as **one-off** work with a delay, and Android forgets one-off work when the device
 * reboots. So a phone that restarted overnight silently stopped showing memories, and the trash
 * stopped expiring. Neither failed with an error; they simply never ran again, which is the kind of
 * bug nobody reports because nothing looks broken.
 *
 * `RECEIVE_BOOT_COMPLETED` is what this needs, and it is one of the few permissions here that exists
 * for a specific, visible feature rather than for a capability in general.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun memoriesScheduler(): MemoriesScheduler
        fun trashPurgeScheduler(): TrashPurgeScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // Re-registering is idempotent: both schedulers use unique work names, so a second call
        // replaces rather than duplicates.
        runCatching {
            val entry = EntryPointAccessors.fromApplication(context, BootEntryPoint::class.java)
            entry.memoriesScheduler().ensureScheduled()
            entry.trashPurgeScheduler().ensureScheduled()
        }
    }
}
