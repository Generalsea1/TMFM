-- TMFM M1 — evidence-based radio catalog migration
-- Execute manually in Supabase SQL Editor.
-- No service_role key is stored here.
begin;
alter table public.radio_stations
    add column if not exists broadcast_type text,
    add column if not exists frequency_verified boolean not null default false,
    add column if not exists stream_verified boolean not null default false,
    add column if not exists stream_verified_at timestamptz,
    add column if not exists hardware_access_state text not null default 'UNKNOWN',
    add column if not exists stream_codec text,
    add column if not exists stream_bitrate_kbps integer,
    add column if not exists stream_connect_ms bigint,
    add column if not exists stream_verification_reason text,
    add column if not exists stream_consecutive_failures integer not null default 0;

update public.radio_stations
set
    broadcast_type = case
        when stream_url is not null and frequency_mhz is not null then 'HYBRID'
        when stream_url is not null then 'INTERNET'
        when frequency_mhz is not null then 'HARDWARE_FM'
        else 'DIRECTORY_ONLY'
    end,
    frequency_verified = case when frequency_mhz is not null and is_verified then true else false end
where broadcast_type is null;

alter table public.radio_stations
    drop constraint if exists radio_stations_broadcast_type_check,
    add constraint radio_stations_broadcast_type_check
    check (broadcast_type in ('HARDWARE_FM','INTERNET','HYBRID','DIRECTORY_ONLY'));

alter table public.radio_stations
    drop constraint if exists radio_stations_hardware_access_state_check,
    add constraint radio_stations_hardware_access_state_check
    check (hardware_access_state in ('UNKNOWN','NO_TUNER','SYSTEM_ONLY','AVAILABLE'));

alter table public.radio_stations
    drop constraint if exists radio_stations_stream_failures_check,
    add constraint radio_stations_stream_failures_check
    check (stream_consecutive_failures >= 0);

drop policy if exists "Public can read verified radio stations" on public.radio_stations;

create policy "Public can read TMFM catalog"
on public.radio_stations for select to anon
using (
    is_islamic = false
    and (
        (stream_verified = true and is_online = true)
        or frequency_verified = true
        or verification_status = 'OFFLINE'
        or verification_status = 'offline'
    )
);

revoke insert, update, delete on public.radio_stations from anon;
grant select on public.radio_stations to anon;

create index if not exists radio_stations_stream_verified_idx
    on public.radio_stations (country_code, stream_verified, is_online);

create index if not exists radio_stations_frequency_verified_idx
    on public.radio_stations (country_code, frequency_verified);
commit;