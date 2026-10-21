package com.progallery.app.data.eventlog

/** Schema version, bumped on a breaking change. Sent with every event. */
const val LOG_SCHEMA_VERSION = "1.0.0"

/** How serious an event is. The values are the ones the schema and the database use. */
enum class LogLevel(val wire: String) {
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
}

/**
 * Every event the app can emit, and the level each one carries.
 *
 * **The level belongs to the event, not to the caller.** A call site that picks its own level
 * eventually picks `info` for everything, and a log where every line is the same severity is a
 * log nobody can filter. Here the level is a property of the event, so it cannot drift.
 *
 * Closed on purpose: an event that is not in this list cannot be logged, which is what keeps
 * `event_name` a fixed enum rather than free text — and what stops a file name being smuggled
 * into one.
 */
enum class EventName(val wire: String, val level: LogLevel) {
    SESSION_START("session_start", LogLevel.INFO),
    SCREEN_VIEW("screen_view", LogLevel.INFO),
    PERMISSION_STATE_RESOLVED("permission_state_resolved", LogLevel.INFO),

    /** The intent. Always paired with a result carrying the same attempt id. */
    ACCESS_FIX_REQUESTED("access_fix_requested", LogLevel.INFO),

    /**
     * The outcome of the intent above.
     *
     * This is the event whose absence made the 21 October log useless: 71 requests, no results,
     * and no way to say why the user stayed stuck. A request without its result is a rumour.
     */
    ACCESS_FIX_RESULT("access_fix_result", LogLevel.INFO),

    MEDIA_QUERY_RESULT("media_query_result", LogLevel.DEBUG),
    API_REQUEST("api_request", LogLevel.DEBUG),
    CRASH("crash", LogLevel.ERROR),
    ANR("anr", LogLevel.ERROR),
    PERFORMANCE_MARKER("performance_marker", LogLevel.DEBUG),
    ERROR_STATE_SHOWN("error_state_shown", LogLevel.WARN),

    // Friction signals: how a UX problem is found before anyone complains.
    RAGE_TAP("rage_tap", LogLevel.WARN),
    DEAD_CLICK("dead_click", LogLevel.WARN),
    PERMISSION_LOOP("permission_loop", LogLevel.WARN),
    BACK_BUTTON_SPAM("back_button_spam", LogLevel.WARN),
    SCROLL_DEPTH("scroll_depth", LogLevel.DEBUG),
    DROP_OFF("drop_off", LogLevel.INFO),
    TIME_TO_FIRST_VALUE("time_to_first_value", LogLevel.INFO),
    CONSENT_CHANGED("consent_changed", LogLevel.INFO),
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

/** The access states an attempt can start and end in. */
enum class AccessState(val wire: String) {
    GRANTED("granted"),
    PARTIAL("partial"),
    DENIED("denied"),
    PERMANENTLY_DENIED("permanently_denied"),
    ;

    companion object {
        fun ofWire(value: String): AccessState? = entries.firstOrNull { it.wire == value }
    }
}
