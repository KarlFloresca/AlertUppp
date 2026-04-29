-- ============================================================
-- AlertUp — Full Schema + RLS Policies
-- Run in Supabase SQL Editor to reset and rebuild everything.
-- ============================================================

-- ── 1. DROP existing tables (child → parent order) ───────────────────────────

DROP TABLE IF EXISTS public.family_members         CASCADE;
DROP TABLE IF EXISTS public.family_registrations   CASCADE;
DROP TABLE IF EXISTS public.household_members      CASCADE;
DROP TABLE IF EXISTS public.household_profiles     CASCADE;
DROP TABLE IF EXISTS public.incident_reports       CASCADE;
DROP TABLE IF EXISTS public.center_suggestions     CASCADE;
DROP TABLE IF EXISTS public.announcements          CASCADE;
DROP TABLE IF EXISTS public.alerts                 CASCADE;
DROP TABLE IF EXISTS public.evacuation_centers     CASCADE;
DROP TABLE IF EXISTS public.profiles               CASCADE;

DROP TYPE IF EXISTS public.user_role         CASCADE;
DROP TYPE IF EXISTS public.alert_level       CASCADE;
DROP TYPE IF EXISTS public.center_status     CASCADE;
DROP TYPE IF EXISTS public.suggestion_status CASCADE;

-- ── 2. ENUM types ─────────────────────────────────────────────────────────────

CREATE TYPE public.user_role         AS ENUM ('resident', 'official');
CREATE TYPE public.alert_level       AS ENUM ('info', 'warning', 'danger');
CREATE TYPE public.center_status     AS ENUM ('available', 'full', 'closed');
CREATE TYPE public.suggestion_status AS ENUM ('pending', 'approved', 'rejected');

-- ── 3. Tables ─────────────────────────────────────────────────────────────────

CREATE TABLE public.profiles (
  id           uuid        NOT NULL,
  full_name    text        NOT NULL DEFAULT '',
  phone        text,
  municipality text,
  role         public.user_role NOT NULL DEFAULT 'resident',
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT profiles_pkey PRIMARY KEY (id),
  CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id)
);

CREATE TABLE public.evacuation_centers (
  id                uuid        NOT NULL DEFAULT uuid_generate_v4(),
  name              text        NOT NULL,
  address           text        NOT NULL,
  municipality      text        NOT NULL,
  latitude          double precision,
  longitude         double precision,
  max_capacity      integer     NOT NULL DEFAULT 0,
  current_occupancy integer     NOT NULL DEFAULT 0,
  status            public.center_status NOT NULL DEFAULT 'available',
  managed_by        uuid,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT evacuation_centers_pkey PRIMARY KEY (id),
  CONSTRAINT evacuation_centers_managed_by_fkey FOREIGN KEY (managed_by) REFERENCES public.profiles(id)
);

CREATE TABLE public.alerts (
  id         uuid        NOT NULL DEFAULT uuid_generate_v4(),
  title      text        NOT NULL,
  body       text        NOT NULL,
  level      public.alert_level NOT NULL DEFAULT 'info',
  area       text,
  issued_by  uuid,
  is_active  boolean     NOT NULL DEFAULT true,
  issued_at  timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  CONSTRAINT alerts_pkey PRIMARY KEY (id),
  CONSTRAINT alerts_issued_by_fkey FOREIGN KEY (issued_by) REFERENCES public.profiles(id)
);

CREATE TABLE public.announcements (
  id         uuid        NOT NULL DEFAULT uuid_generate_v4(),
  title      text        NOT NULL,
  body       text        NOT NULL,
  posted_by  uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT announcements_pkey PRIMARY KEY (id),
  CONSTRAINT announcements_posted_by_fkey FOREIGN KEY (posted_by) REFERENCES public.profiles(id)
);

CREATE TABLE public.center_suggestions (
  id           uuid        NOT NULL DEFAULT uuid_generate_v4(),
  suggested_by uuid,
  name         text        NOT NULL,
  address      text        NOT NULL,
  latitude     double precision,
  longitude    double precision,
  max_capacity integer,
  status       public.suggestion_status NOT NULL DEFAULT 'pending',
  reviewed_by  uuid,
  reviewed_at  timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT center_suggestions_pkey PRIMARY KEY (id),
  CONSTRAINT center_suggestions_suggested_by_fkey FOREIGN KEY (suggested_by) REFERENCES public.profiles(id),
  CONSTRAINT center_suggestions_reviewed_by_fkey  FOREIGN KEY (reviewed_by)  REFERENCES public.profiles(id)
);

-- incident_reports: flood | road | damage | unsafe | missing | rescue | medical | supply
CREATE TABLE public.incident_reports (
  id           uuid        NOT NULL DEFAULT uuid_generate_v4(),
  submitted_by uuid,
  report_type  text        NOT NULL DEFAULT 'flood',
  title        text        NOT NULL,
  description  text        NOT NULL DEFAULT '',
  flood_level  text,
  latitude     double precision,
  longitude    double precision,
  landmark     text,
  photo_url    text,
  status       text        NOT NULL DEFAULT 'pending',
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT incident_reports_pkey PRIMARY KEY (id),
  CONSTRAINT incident_reports_submitted_by_fkey FOREIGN KEY (submitted_by) REFERENCES public.profiles(id)
);

-- household_profiles: one per resident (head_resident_id is unique)
CREATE TABLE public.household_profiles (
  id                  uuid        NOT NULL DEFAULT uuid_generate_v4(),
  head_resident_id    uuid        NOT NULL,
  resident_id         uuid        NOT NULL,
  household_name      text        NOT NULL DEFAULT '',
  address             text        NOT NULL DEFAULT '',
  barangay            text        NOT NULL DEFAULT '',
  municipality        text        NOT NULL DEFAULT '',
  house_type          text        NOT NULL DEFAULT 'concrete',
  near_flood_zone     boolean     NOT NULL DEFAULT false,
  near_landslide_zone boolean     NOT NULL DEFAULT false,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT household_profiles_pkey PRIMARY KEY (id),
  CONSTRAINT household_profiles_head_unique UNIQUE (head_resident_id),
  CONSTRAINT household_profiles_head_fkey FOREIGN KEY (head_resident_id) REFERENCES public.profiles(id),
  CONSTRAINT household_profiles_resident_fkey FOREIGN KEY (resident_id)     REFERENCES public.profiles(id)
);

-- household_members: people in a household (vulnerability tracking)
CREATE TABLE public.household_members (
  id           uuid        NOT NULL DEFAULT uuid_generate_v4(),
  household_id uuid        NOT NULL,
  full_name    text        NOT NULL,
  age          integer     NOT NULL DEFAULT 0,
  sex          text        NOT NULL DEFAULT 'Male',
  relation     text        NOT NULL DEFAULT 'other',
  is_head      boolean     NOT NULL DEFAULT false,
  is_pwd       boolean     NOT NULL DEFAULT false,
  is_pregnant  boolean     NOT NULL DEFAULT false,
  notes        text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT household_members_pkey PRIMARY KEY (id),
  CONSTRAINT household_members_household_fkey FOREIGN KEY (household_id) REFERENCES public.household_profiles(id)
);

-- family_registrations: named family groups under a household
-- center_id is nullable — assigned later when evacuating
CREATE TABLE public.family_registrations (
  id            uuid        NOT NULL DEFAULT uuid_generate_v4(),
  household_id  uuid        NOT NULL,
  resident_id   uuid        NOT NULL,
  family_name   text        NOT NULL DEFAULT 'Family',
  center_id     uuid,
  registered_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT family_registrations_pkey PRIMARY KEY (id),
  CONSTRAINT family_registrations_household_fkey FOREIGN KEY (household_id) REFERENCES public.household_profiles(id),
  CONSTRAINT family_registrations_resident_fkey  FOREIGN KEY (resident_id)  REFERENCES public.profiles(id),
  CONSTRAINT family_registrations_center_fkey    FOREIGN KEY (center_id)    REFERENCES public.evacuation_centers(id)
);

-- family_members: individual people in a family registration
CREATE TABLE public.family_members (
  id              uuid        NOT NULL DEFAULT uuid_generate_v4(),
  registration_id uuid        NOT NULL,
  full_name       text        NOT NULL,
  age             integer     NOT NULL DEFAULT 0,
  notes           text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT family_members_pkey PRIMARY KEY (id),
  CONSTRAINT family_members_registration_fkey FOREIGN KEY (registration_id) REFERENCES public.family_registrations(id)
);

-- ── 4. Trigger: auto-create profile on sign-up ───────────────────────────────

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $func$
BEGIN
  INSERT INTO public.profiles (id, full_name, phone, municipality, role)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
    COALESCE(NEW.raw_user_meta_data->>'phone', ''),
    COALESCE(NEW.raw_user_meta_data->>'municipality', ''),
    COALESCE(
      (NEW.raw_user_meta_data->>'role')::public.user_role,
      'resident'::public.user_role
    )
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$func$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- ── 5. Backfill profiles for existing auth users ─────────────────────────────
-- Run this after a schema reset to restore profile rows for existing accounts.

INSERT INTO public.profiles (id, full_name, phone, municipality, role)
SELECT
  id,
  COALESCE(raw_user_meta_data->>'full_name', ''),
  COALESCE(raw_user_meta_data->>'phone', ''),
  COALESCE(raw_user_meta_data->>'municipality', ''),
  COALESCE(
    (raw_user_meta_data->>'role')::public.user_role,
    'resident'::public.user_role
  )
FROM auth.users
ON CONFLICT (id) DO NOTHING;

-- ── 6. Device tokens for FCM push notifications ───────────────────────────────
-- Stores one FCM token per user per device. Used by the Edge Function to send
-- targeted push notifications when an alert is posted.

CREATE TABLE IF NOT EXISTS public.device_tokens (
  id         uuid        NOT NULL DEFAULT uuid_generate_v4(),
  user_id    uuid        NOT NULL,
  fcm_token  text        NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT device_tokens_pkey PRIMARY KEY (id),
  CONSTRAINT device_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id),
  CONSTRAINT device_tokens_user_token_key UNIQUE (user_id, fcm_token)
);
