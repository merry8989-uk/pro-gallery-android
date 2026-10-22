-- The web app's event names, added to the vocabulary.
--
-- Found by testing rather than by reading: a `page_view` row — a perfectly ordinary web event —
-- was refused by the event-name constraint. The table has a `platform` column that accepts
-- 'web', so it was built to hold both sides, but the vocabulary only ever learned the Android
-- names. The two are meant to be joined; a vocabulary that rejects half of them cannot be.
--
-- The names come from the shared standard, and the two that differ between platforms are kept as
-- they are on each side: the web says `rage_click` and `api_call`, the app says `rage_tap` and
-- `api_request`. Renaming one side to match the other would be tidier and would also invalidate
-- every row already written, so both spellings are allowed and the platform says which is which.

alter table public.log_events
    drop constraint if exists log_events_event_name_known;

alter table public.log_events
    add constraint log_events_event_name_known check (event_name in (
        -- shared
        'session_start', 'error_state_shown', 'dead_click', 'back_button_spam',
        'scroll_depth', 'drop_off', 'time_to_first_value', 'consent_changed',
        -- the app
        'screen_view', 'permission_state_resolved',
        'access_fix_requested', 'access_fix_result', 'media_query_result',
        'api_request', 'crash', 'anr', 'performance_marker',
        'rage_tap', 'permission_loop',
        -- the web
        'page_view', 'search_performed', 'search_no_results', 'api_call', 'js_error',
        'web_vitals', 'form_start', 'form_submit', 'form_abandon',
        'feedback_submitted', 'conversion', 'rage_click'
    ));
