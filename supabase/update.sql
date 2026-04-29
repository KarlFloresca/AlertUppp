-- ============================================================
-- AlertUp — Incremental Update
-- Run this AFTER schema.sql + policies.sql have already been applied.
-- Adds FCM device token support for push notifications.
-- ============================================================

-- ── 1. device_tokens table ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.device_tokens (
  id         uuid        NOT NULL DEFAULT uuid_generate_v4(),
  user_id    uuid        NOT NULL,
  fcm_token  text        NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT device_tokens_pkey PRIMARY KEY (id),
  CONSTRAINT device_tokens_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES public.profiles(id),
  CONSTRAINT device_tokens_user_token_key
    UNIQUE (user_id, fcm_token)
);

-- ── 2. RLS ────────────────────────────────────────────────────────────────────

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

-- Drop first so re-running is safe
DROP POLICY IF EXISTS "device_tokens_select" ON public.device_tokens;
DROP POLICY IF EXISTS "device_tokens_insert" ON public.device_tokens;
DROP POLICY IF EXISTS "device_tokens_update" ON public.device_tokens;

-- Users can only read/write their own token
CREATE POLICY "device_tokens_select" ON public.device_tokens
  FOR SELECT TO authenticated USING (user_id = auth.uid());

CREATE POLICY "device_tokens_insert" ON public.device_tokens
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

CREATE POLICY "device_tokens_update" ON public.device_tokens
  FOR UPDATE TO authenticated USING (user_id = auth.uid());

-- ── 3. Backfill profiles for any auth users still missing a row ───────────────
-- Safe to re-run; ON CONFLICT DO NOTHING skips existing rows.

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
