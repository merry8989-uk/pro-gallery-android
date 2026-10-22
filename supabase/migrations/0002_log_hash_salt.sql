-- The salted hash, computed where the salt lives.
--
-- The first version of this had the ingest function compute the hash from a secret in its
-- environment. Two things moved it into the database:
--
-- 1. **A secret in a function's environment is a secret in one more place.** Here the salt sits
--    in a table with row level security on and **no policies at all**, so not even the service
--    role can read it through the API. Only the trigger below — which is `security definer` —
--    can.
-- 2. **The hash cannot be forgotten.** A client that posts without a hash still gets one, because
--    the column is filled by a trigger rather than by whoever wrote the request. A rule that
--    depends on every caller remembering it is a rule that will be broken.
--
-- The property that matters is unchanged: the client cannot compute the hash, because the client
-- does not have the salt.

create extension if not exists pgcrypto with schema extensions;

-- One row, enforced by the primary key being a boolean that must be true.
create table if not exists public.log_hash_salt (
    id   boolean primary key default true,
    salt text not null,
    constraint log_hash_salt_single_row check (id)
);

insert into public.log_hash_salt (id, salt)
select true, encode(extensions.gen_random_bytes(32), 'hex')
where not exists (select 1 from public.log_hash_salt);

-- Nothing reads this through the API — not anon, not authenticated. The trigger is definer, so
-- it reads it as the owner.
alter table public.log_hash_salt enable row level security;
revoke all on public.log_hash_salt from anon, authenticated;

comment on table public.log_hash_salt is
    'The salt for the device-code hash. One row, no API policies, read only by the hashing '
    'trigger. Rotating it changes every future hash, which is why it is not rotated casually.';

-- ---------------------------------------------------------------------------------------------

create or replace function public.log_events_hash() returns trigger
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    the_salt text;
begin
    select salt into the_salt from public.log_hash_salt limit 1;
    if the_salt is null or length(the_salt) < 32 then
        -- Refused rather than stored with a missing hash: a row that cannot be grouped is a row
        -- that cannot be used, and quietly writing a placeholder would hide the fault.
        raise exception 'log_events: no usable hash salt is configured'
            using errcode = 'check_violation';
    end if;

    new.user_id_hash := encode(extensions.hmac(new.user_code, the_salt, 'sha256'), 'hex');
    return new;
end;
$$;

-- Runs before the NOT NULL check, so the column is filled in time.
drop trigger if exists log_events_hash_trigger on public.log_events;
create trigger log_events_hash_trigger
    before insert on public.log_events
    for each row execute function public.log_events_hash();

-- ---------------------------------------------------------------------------------------------
-- The retention job, now that there is a way to run it on a schedule.
-- ---------------------------------------------------------------------------------------------

do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
        create extension if not exists pg_cron;
        -- Daily at 03:17 UTC — an odd minute, so it is not competing with every other job that
        -- runs on the hour.
        perform cron.schedule('prune-log-events', '17 3 * * *', $cron$select public.prune_log_events(30)$cron$);
    end if;
exception when others then
    -- A schedule that cannot be created must not fail the migration: the function is still there
    -- and can be run by hand.
    raise notice 'pg_cron is not available; prune_log_events must be run manually';
end;
$$;
