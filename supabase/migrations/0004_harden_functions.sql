-- What the security advisors found, which reading the SQL had not.
--
-- Three findings, and the first one is not cosmetic:
--
-- 1. **`prune_log_events` was callable by `anon`.** It is `security definer` — that is what lets
--    it delete past row level security — and it takes the retention window as an argument. So
--    anyone holding the publishable key, which ships in the app, could call
--    `prune_log_events(-99999)` through `/rest/v1/rpc/prune_log_events` and **delete the entire
--    table**. Every row ever collected, by one unauthenticated request. The window is a parameter
--    and the function was exposed; those two facts together are the hole.
--
-- 2. **The trigger functions had a mutable `search_path`.** For a `security definer` function
--    that is a privilege escalation: a caller who can create a function in a schema earlier in
--    the path can have it resolved instead of the real one. Pinning the path removes the
--    question.
--
-- 3. **`log_events_hash()` was callable through the API** as well. It is a trigger function; it
--    has no business being callable at all.
--
-- Nothing here changes behaviour for the intended callers: triggers fire whether or not the
-- caller has EXECUTE, so revoking it stops the API route and nothing else.

-- 1. Pin the search path on the two that were left mutable.
alter function public.log_events_guard() set search_path = public, extensions;
alter function public.prune_log_events(int) set search_path = public, extensions;

-- 2. Close the API routes.
revoke execute on function public.log_events_guard() from public, anon, authenticated;
revoke execute on function public.log_events_hash() from public, anon, authenticated;
revoke execute on function public.prune_log_events(int) from public, anon, authenticated;

-- 3. Retention is an operator's job, so only the service role may run it.
grant execute on function public.prune_log_events(int) to service_role;

comment on function public.prune_log_events(int) is
    'Deletes events older than keep_days (default 30). Executable by the service role only: the '
    'window is an argument, so exposing it would let any client delete every row.';
