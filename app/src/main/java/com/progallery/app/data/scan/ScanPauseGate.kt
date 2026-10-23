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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Singleton
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine

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
 * Whether the device is under the kind of pressure a scan should back off from.
 *
 * Two signals, both from the system rather than inferred: the battery broadcast the platform sends,
 * and the thermal status it publishes from Android 10. Kept apart from the foreground question
 * because they answer different things — one is about the person, one about the phone.
 */
@Singleton
class DevicePressureState @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _lowBattery = MutableStateFlow(false)
    val lowBattery: StateFlow<Boolean> = _lowBattery.asStateFlow()

    private val _throttled = MutableStateFlow(false)
    val throttled: StateFlow<Boolean> = _throttled.asStateFlow()

    fun attach() {
        runCatching {
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_BATTERY_LOW -> _lowBattery.value = true
                            Intent.ACTION_BATTERY_OKAY -> _lowBattery.value = false
                        }
                    }
                },
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_LOW)
                    addAction(Intent.ACTION_BATTERY_OKAY)
                },
            )
        }

        // Thermal status is Android 10 and up. Below that there is no signal to read, so nothing is
        // claimed rather than guessed at.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                power?.addThermalStatusListener(context.mainExecutor) { status ->
                    // Moderate and above is where the platform starts holding performance back.
                    _throttled.value = status >= PowerManager.THERMAL_STATUS_MODERATE
                }
            }
        }
    }
}

/** Why a scan is waiting, if it is. */
enum class PauseReason { BACKGROUND, LOW_BATTERY, THERMAL }

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
    private val pressure: DevicePressureState,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The setting, followed rather than re-read, so a change mid-scan is seen. */
    private val pausesWhenBackgrounded = settingsRepository.settings
        .map { it.effectivePauseWhenBackgrounded }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The two device settings, followed for the same reason. */
    private val pausesOnLowBattery = settingsRepository.settings
        .map { it.pauseOnLowBattery }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val pausesOnThermal = settingsRepository.settings
        .map { it.pauseOnThermalPressure }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The same, for the concurrency the performance mode or the slider asks for. */
    private val concurrency = settingsRepository.settings
        .map { it.effectiveConcurrency }
        .stateIn(scope, SharingStarted.Eagerly, 4)

    /**
     * The dispatcher a scan runs on, bounded by what the person chose.
     *
     * The scanners ask the gate rather than reading the setting themselves, and that is deliberate:
     * the setting has to bound something real, and the thing a scan uses in parallel is its
     * dispatcher. A limited view of the IO pool is the honest reader for "how many files at once" —
     * the shared pool is every other caller's as much as ours. One reader means the mode and the
     * slider cannot end up disagreeing about it.
     */
    val scanDispatcher: CoroutineDispatcher
        get() = Dispatchers.IO.limitedParallelism(concurrency.value.coerceIn(1, 64))

    /**
     * Suspends while the scan should be waiting, and returns immediately otherwise.
     *
     * Called at the same points as `ensureActive()`, so a scan that can be cancelled can also be
     * paused — and so a paused scan is still cancellable, which is the whole reason this waits on a
     * flow instead of sleeping.
     */
    suspend fun awaitIfPaused() {
        when (waitingFor()) {
            null -> return
            PauseReason.BACKGROUND -> foreground.isForeground.filter { it }.first()
            PauseReason.LOW_BATTERY -> pressure.lowBattery.filter { !it }.first()
            PauseReason.THERMAL -> pressure.throttled.filter { !it }.first()
        }
    }

    /**
     * Why a scan is waiting, or null when it is not.
     *
     * Ordered rather than arbitrary: the background case is the one the person caused, so it is the
     * one worth naming first when more than one is true.
     */
    fun waitingFor(): PauseReason? = when {
        pausesWhenBackgrounded.value && !foreground.isForeground.value -> PauseReason.BACKGROUND
        pausesOnLowBattery.value && pressure.lowBattery.value -> PauseReason.LOW_BATTERY
        pausesOnThermal.value && pressure.throttled.value -> PauseReason.THERMAL
        else -> null
    }

    /**
     * The reason a scan is waiting, as a flow.
     *
     * A **flow** rather than only a function, because a scan that is waiting cannot emit anything —
     * it is suspended inside this gate. Without a flow of its own the screen would have no way to
     * say why nothing is happening, and `pauseReason()` would be a function nobody calls, which is
     * what it had been.
     */
    val reason: StateFlow<String?> = combine(
        pausesWhenBackgrounded, foreground.isForeground, pausesOnLowBattery,
        pressure.lowBattery, pausesOnThermal, pressure.throttled,
    ) { backgroundOn, isForeground, batteryOn, isLow, thermalOn, isThrottled ->
        when {
            backgroundOn && !isForeground -> "Paused while the app is in the background."
            batteryOn && isLow -> "Paused while the battery is low."
            thermalOn && isThrottled ->
                "Paused while the device is warm — a scan that keeps going runs slower and makes it warmer."
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The reason a scan is waiting, in words, or null when it is not. */
    fun pauseReason(): String? = when (waitingFor()) {
        null -> null
        PauseReason.BACKGROUND -> "Paused while the app is in the background."
        PauseReason.LOW_BATTERY -> "Paused while the battery is low."
        PauseReason.THERMAL -> "Paused while the device is warm, because a scan that keeps going runs slower and makes it warmer."
    }
}
