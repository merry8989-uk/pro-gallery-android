package com.progallery.app.data.scan

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.progallery.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is in front of the person right now.
 *
 * Counted rather than guessed, and counted from the activity lifecycle rather than from
 * `ProcessLifecycleOwner`, so it needs no new dependency and no annotation processor: a scan that
 * pauses itself must be sure it is paused, not merely likely to be.
 */
@Singleton
class AppForegroundState @Inject constructor() {

    private val running = MutableStateFlow(0)
    private val _isForeground = MutableStateFlow(false)

    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun attach(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    running.value += 1
                    _isForeground.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    running.value = (running.value - 1).coerceAtLeast(0)
                    _isForeground.value = running.value > 0
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

/**
 * The point a scan waits at when the person has asked it to.
 *
 * `pauseWhenBackgrounded` and the battery-saver mode both promised to stop work when the app left
 * the screen, and until now nothing read either of them. This is the reader. It is deliberately a
 * separate object rather than a flag checked inline at each loop, because there are three scanners
 * and a rule that each of them has to remember is a rule that one of them will forget.
 *
 * Two properties matter and both are tested: it **waits** rather than cancels — a paused scan
 * resumes where it stopped — and it reports **why** it is waiting, because a scan that looks stuck
 * and does not say why is indistinguishable from a scan that has hung.
 */
@Singleton
class ScanPauseGate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val foreground: AppForegroundState,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The setting, followed rather than re-read, so a change mid-scan is seen. */
    private val pausesWhenBackgrounded = settingsRepository.settings
        .map { it.effectivePauseWhenBackgrounded }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Suspends while the scan should be waiting, and returns immediately otherwise.
     *
     * Called at the same points as `ensureActive()`, so a scan that can be cancelled can also be
     * paused — and so a paused scan is still cancellable, which is the whole reason this waits on a
     * flow instead of sleeping.
     */
    suspend fun awaitIfPaused() {
        if (!pausesWhenBackgrounded.value) return
        if (foreground.isForeground.value) return
        foreground.isForeground.filter { it }.first()
    }

    /** The reason a scan is waiting, or null when it is not. */
    fun pauseReason(): String? =
        if (pausesWhenBackgrounded.value && !foreground.isForeground.value) {
            "Paused while the app is in the background."
        } else {
            null
        }
}
