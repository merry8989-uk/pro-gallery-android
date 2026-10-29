package com.progallery.app.data.eventlog

/** Schema version, bumped on a breaking change. Sent with every event. */
const val LOG_SCHEMA_VERSION = "1.1.0"

/**
 * How serious an event is. The values are the ones the schema and the database use.
 *
 * Six levels, because the standard names six and the two added here are the ones that were
 * missing: **trace** for the steps inside an operation, which is what a detailed diagnostic
 * needs and what `debug` was being used for, and **fatal** for a failure the app cannot continue
 * past — which is not the same as an error it can report and survive.
 */
enum class LogLevel(val wire: String) {
    TRACE("trace"),
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    FATAL("fatal"),
    ;

    companion object {
        fun ofWire(value: String): LogLevel? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * What kind of log this is.
 *
 * This is a different axis from the level, and the standard is explicit about it: an error log can
 * be a security log or a network log. Keeping them separate is what lets a person ask "show me
 * everything that failed" and "show me everything touching the network" and get different answers.
 *
 * **The list is the standard's twelve, and every one of them is here on purpose.** Some were
 * written for a service with a server — there is no deployment to log from an app and no
 * administrator to audit — so the categories that would otherwise sit empty are given the honest
 * thing they *do* hold on a phone:
 *
 * - `INFRASTRUCTURE` holds the device the app runs on: when the process started, how much memory
 *   it found, and the network errors it hit. Not a server's CPU, which it does not have.
 * - `AUDIT` holds the changes that matter and have no administrator behind them: a setting
 *   changed, data exported, data deleted.
 * - `ANALYTICS` is counted, not logged — see the note on [EventName]. The standard itself says
 *   keeping the metrics apart from the raw logs is useful, and this app does.
 */
enum class LogCategory(val wire: String, val label: String) {
    APPLICATION("application", "Application"),
    ERROR("error", "Error"),
    SECURITY("security", "Security"),
    PERFORMANCE("performance", "Performance"),
    USER_ACTIVITY("user_activity", "User activity"),
    INFRASTRUCTURE("infrastructure", "Device & infrastructure"),
    NETWORK("network", "API & network"),
    DATABASE("database", "Database"),
    AUDIT("audit", "Audit"),
    ANALYTICS("analytics", "Analytics & business"),
    BACKGROUND("background", "Background jobs"),
    TRACE("trace", "Debug & trace"),
    ;

    companion object {
        fun ofWire(value: String): LogCategory? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Every event the app can emit, with the level and the category each one carries.
 *
 * **Both belong to the event, not to the caller.** A call site that picks its own level
 * eventually picks `info` for everything, and a log where every line is the same severity is a
 * log nobody can filter; the category drifts the same way. Here they are properties of the event,
 * so they cannot.
 *
 * Closed on purpose: an event that is not in this list cannot be logged, which is what keeps
 * `event_name` a fixed enum rather than free text — and what stops a file name being smuggled
 * into one.
 */
enum class EventName(
    val wire: String,
    val level: LogLevel,
    val category: LogCategory,
) {
    // ---- Application ------------------------------------------------------------------------
    SESSION_START("session_start", LogLevel.INFO, LogCategory.APPLICATION),
    SCREEN_VIEW("screen_view", LogLevel.INFO, LogCategory.APPLICATION),

    // ---- Error ------------------------------------------------------------------------------
    CRASH("crash", LogLevel.ERROR, LogCategory.ERROR),
    ERROR_STATE_SHOWN("error_state_shown", LogLevel.WARN, LogCategory.ERROR),
    /** A file that would not play, with the reason — the app's most common real failure. */
    PLAYBACK_FAILED("playback_failed", LogLevel.WARN, LogCategory.ERROR),
    /**
     * A file was opened and shown.
     *
     * **Successes are recorded deliberately.** A log of failures alone has no denominator: twenty
     * `.webp` failures mean one thing if `.webp` was opened twenty-five times and another if it was
     * opened five thousand. Without the successes there is no rate, and without a rate a broken
     * format cannot be told from one unlucky file.
     */
    FILE_OPENED("file_opened", LogLevel.INFO, LogCategory.USER_ACTIVITY),

    /**
     * A file could not be opened, with the stage it stopped at.
     *
     * `stage` is the first useful question — opening the address, decoding the bytes, starting
     * playback, parsing the document — because each has a different fix. The reason is only
     * classified when something actually said why.
     */
    FILE_OPEN_FAILED("file_open_failed", LogLevel.WARN, LogCategory.ERROR),

    // ---- Security ---------------------------------------------------------------------------
    PERMISSION_STATE_RESOLVED("permission_state_resolved", LogLevel.INFO, LogCategory.SECURITY),
    /** Access was asked for and refused, which is what a security log is for. */
    PERMISSION_DENIED("permission_denied", LogLevel.WARN, LogCategory.SECURITY),
    PERMISSION_LOOP("permission_loop", LogLevel.WARN, LogCategory.SECURITY),
    /** The intent, always paired with a result carrying the same attempt id. */
    ACCESS_FIX_REQUESTED("access_fix_requested", LogLevel.INFO, LogCategory.SECURITY),
    /**
     * The outcome of the intent above.
     *
     * This is the event whose absence made the 21 October log useless: 71 requests, no results,
     * and no way to say why the user stayed stuck. A request without its result is a rumour.
     */
    ACCESS_FIX_RESULT("access_fix_result", LogLevel.INFO, LogCategory.SECURITY),

    // ---- Performance ------------------------------------------------------------------------
    PERFORMANCE_MARKER("performance_marker", LogLevel.DEBUG, LogCategory.PERFORMANCE),
    /** An operation that took long enough to be worth noticing, with what it was. */
    SLOW_OPERATION("slow_operation", LogLevel.WARN, LogCategory.PERFORMANCE),
    MEDIA_QUERY_RESULT("media_query_result", LogLevel.DEBUG, LogCategory.PERFORMANCE),

    // ---- User activity ----------------------------------------------------------------------
    /*
     * Three friction signals are **not** here, and their absence is deliberate: `rage_tap`,
     * `dead_click` and `scroll_depth` each need gesture instrumentation that does not exist yet —
     * counting taps on an element that did nothing, and reading the scroll position of a list that
     * has no list state. An event in this list is a promise that the app collects it, and a
     * promise kept by writing the enum and nothing else is the exact thing this file is for
     * avoiding. They go back in when their call sites do.
     *
     * The same applies to `anr`: detecting a blocked main thread is a watchdog with real battery
     * and false-positive costs, not a line to add hopefully.
     */
    BACK_BUTTON_SPAM("back_button_spam", LogLevel.WARN, LogCategory.USER_ACTIVITY),
    DROP_OFF("drop_off", LogLevel.INFO, LogCategory.USER_ACTIVITY),
    TIME_TO_FIRST_VALUE("time_to_first_value", LogLevel.INFO, LogCategory.USER_ACTIVITY),

    // ---- Device & infrastructure ------------------------------------------------------------
    /**
     * What the process found when it started.
     *
     * A phone is the infrastructure this app runs on, so this is the honest equivalent of a
     * server's startup line: memory available, storage state, and the version it started as.
     */
    MEMORY_REPORT("memory_report", LogLevel.DEBUG, LogCategory.INFRASTRUCTURE),

    // ---- API & network ----------------------------------------------------------------------
    API_REQUEST("api_request", LogLevel.DEBUG, LogCategory.NETWORK),
    /** A request that did not complete, with the endpoint reduced to host and route. */
    NETWORK_ERROR("network_error", LogLevel.WARN, LogCategory.NETWORK),
    /** A host that asked us to slow down — the reason the update check was rebuilt around it. */
    RATE_LIMITED("rate_limited", LogLevel.WARN, LogCategory.NETWORK),

    // ---- Database ---------------------------------------------------------------------------
    /** The database was opened, with the version it was upgraded to. */
    MIGRATION_EVENT("migration_event", LogLevel.INFO, LogCategory.DATABASE),
    /** A read or write that failed. The message is never stored — only which operation it was. */
    DATABASE_ERROR("database_error", LogLevel.ERROR, LogCategory.DATABASE),

    // ---- Audit ------------------------------------------------------------------------------
    /** A setting changed, from what to what. This is the audit trail an app can honestly keep. */
    SETTINGS_CHANGED("settings_changed", LogLevel.INFO, LogCategory.AUDIT),
    DATA_EXPORTED("data_exported", LogLevel.INFO, LogCategory.AUDIT),
    DATA_DELETED("data_deleted", LogLevel.WARN, LogCategory.AUDIT),
    CONSENT_CHANGED("consent_changed", LogLevel.INFO, LogCategory.AUDIT),

    // ---- Background jobs --------------------------------------------------------------------
    JOB_STARTED("job_started", LogLevel.DEBUG, LogCategory.BACKGROUND),
    JOB_FINISHED("job_finished", LogLevel.INFO, LogCategory.BACKGROUND),
    JOB_FAILED("job_failed", LogLevel.ERROR, LogCategory.BACKGROUND),

    // ---- Debug & trace ----------------------------------------------------------------------
    /** A step inside an operation, for the detailed end of the scale. */
    TRACE_MARKER("trace_marker", LogLevel.TRACE, LogCategory.TRACE),
    ;

    companion object {
        fun ofWire(value: String): EventName? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The names an outcome can take. Fixed, because "success-ish" is not a value anyone can group by.
 */
enum class FixOutcome(val wire: String) {
    SUCCESS("success"),
    NO_OP("no_op"),
    FAILED("failed"),
    ;

    companion object {
        fun ofWire(value: String): FixOutcome? = entries.firstOrNull { it.wire == value }
    }
}

/*
 * There is deliberately no access-state enum here. The app already has one —
 * `com.progallery.app.data.permissions.AccessState` — and a second one for the same idea is
 * exactly how two parts of an app come to disagree. The wire names the schema uses live in
 * `AccessStateWire.kt`, next to the mapping rather than in a rival enum.
 */
