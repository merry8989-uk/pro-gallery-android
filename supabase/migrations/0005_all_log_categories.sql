-- The rest of the log categories, and the two severity levels that were missing.
--
-- The app could already log application, error, security, performance, user-activity and network
-- events. The list below adds what the standard's remaining categories need on a phone:
--
--   * `database`   — the Room database opened and upgraded, and any read or write that failed
--   * `audit`      — a setting changed, data exported, data deleted. There is no administrator in
--                    an app, so this is the honest audit trail it can keep: the changes that
--                    matter, and who made them (the person holding the phone).
--   * `background` — the WorkManager jobs: the daily memories reminder and the log upload
--   * `trace`      — the steps inside an operation, at the new `trace` level
--   * `infrastructure` — what the process found on the device it started on, and the network
--                    errors it hit. Not a server's CPU, which an app does not have.
--
-- **Two levels were missing and both matter.** `trace` is what `debug` was being asked to do
-- (the steps inside an operation), and `fatal` is a failure the app cannot continue past — which
-- is not the same as an error it can report and carry on from. Without them, a severity filter
-- could not tell those apart.
--
-- Adding an event here is not optional housekeeping: the column is a closed CHECK constraint, so
-- an event the app emits but this file does not know is **refused at insert**. That is the point
-- of the constraint, and it is also how the web app's `page_view` was caught being missing.
--
-- **The list is a superset, and deliberately so.** It holds names the app emits, names the web app
-- emits, and names an older build may still be sending — a whitelist that only knew today's events
-- would start refusing yesterday's. The app's own vocabulary is the strict list; this is the
-- boundary that refuses anything outside all of them.

alter table public.log_events
    drop constraint if exists log_events_event_name_known;

alter table public.log_events
    add constraint log_events_event_name_known check (event_name in (
        -- shared
        'session_start', 'error_state_shown', 'dead_click', 'back_button_spam',
        'scroll_depth', 'drop_off', 'time_to_first_value', 'consent_changed',
        -- the app: application and error
        'screen_view', 'crash', 'anr', 'playback_failed',
        -- the app: security
        'permission_state_resolved', 'permission_denied', 'permission_loop',
        'access_fix_requested', 'access_fix_result',
        -- the app: performance
        'performance_marker', 'slow_operation', 'media_query_result',
        -- the app: user activity
        'rage_tap',
        -- the app: device and infrastructure
        'memory_report',
        -- the app: api and network
        'api_request', 'network_error', 'rate_limited',
        -- the app: database
        'migration_event', 'database_error',
        -- the app: audit
        'settings_changed', 'data_exported', 'data_deleted',
        -- the app: background jobs
        'job_started', 'job_finished', 'job_failed',
        -- the app: debug and trace
        'trace_marker',
        -- the web
        'page_view', 'search_performed', 'search_no_results', 'api_call', 'js_error',
        'web_vitals', 'form_start', 'form_submit', 'form_abandon',
        'feedback_submitted', 'conversion', 'rage_click'
    ));

alter table public.log_events
    drop constraint if exists log_events_level_known;

alter table public.log_events
    add constraint log_events_level_known check (
        log_level in ('trace', 'debug', 'info', 'warn', 'error', 'fatal')
    );

-- The category, as its own column so the database can be asked for "everything about the
-- database" without parsing JSON.
--
-- Nullable on purpose: rows written before this migration have no category, and back-filling them
-- from the event name would be inventing data. New rows always carry one — the app derives it
-- from the event itself, so it cannot be forgotten.
alter table public.log_events
    add column if not exists category text;

alter table public.log_events
    drop constraint if exists log_events_category_known;

alter table public.log_events
    add constraint log_events_category_known check (category is null or category in (
        'application', 'error', 'security', 'performance', 'user_activity',
        'infrastructure', 'network', 'database', 'audit', 'analytics',
        'background', 'trace'
    ));

create index if not exists log_events_category_idx on public.log_events (category, timestamp desc);

-- A fatal is as worth keeping as an error, so the partial index that exists to make "show me what
-- went wrong" fast has to include it.
drop index if exists public.log_events_level_idx;
create index if not exists log_events_level_idx
    on public.log_events (log_level)
    where log_level in ('warn', 'error', 'fatal');
