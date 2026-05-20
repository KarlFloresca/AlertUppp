-- ============================================================
-- AlertUp — RLS Policies
-- Run AFTER schema.sql.
-- ============================================================

-- ── 1. Enable RLS on all tables ───────────────────────────────────────────────

ALTER TABLE public.profiles             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.evacuation_centers   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerts               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.center_suggestions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.incident_reports     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.household_profiles   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.household_members    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_members       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.evacuation_history   ENABLE ROW LEVEL SECURITY;

-- ── 2. Helper: is the current user an official? ───────────────────────────────

CREATE OR REPLACE FUNCTION public.is_official()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $func$
  SELECT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = auth.uid() AND role = 'official'
  );
$func$;

-- ── 3. profiles ───────────────────────────────────────────────────────────────
-- Any authenticated user can read profiles (needed for name lookups).
-- Users can only insert/update their own row.

CREATE POLICY "profiles_select" ON public.profiles
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "profiles_insert" ON public.profiles
  FOR INSERT TO authenticated WITH CHECK (id = auth.uid());

CREATE POLICY "profiles_update" ON public.profiles
  FOR UPDATE TO authenticated
  USING (id = auth.uid()) WITH CHECK (id = auth.uid());

-- ── 4. evacuation_centers ─────────────────────────────────────────────────────
-- Everyone can read. Only officials can write.

CREATE POLICY "centers_select" ON public.evacuation_centers
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "centers_insert" ON public.evacuation_centers
  FOR INSERT TO authenticated WITH CHECK (public.is_official());

CREATE POLICY "centers_update" ON public.evacuation_centers
  FOR UPDATE TO authenticated 
  USING (true)
  WITH CHECK (true);

CREATE POLICY "centers_delete" ON public.evacuation_centers
  FOR DELETE TO authenticated USING (public.is_official());

-- ── 5. alerts ─────────────────────────────────────────────────────────────────

CREATE POLICY "alerts_select" ON public.alerts
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "alerts_insert" ON public.alerts
  FOR INSERT TO authenticated WITH CHECK (public.is_official());

CREATE POLICY "alerts_update" ON public.alerts
  FOR UPDATE TO authenticated USING (public.is_official());

CREATE POLICY "alerts_delete" ON public.alerts
  FOR DELETE TO authenticated USING (public.is_official());

-- ── 6. announcements ──────────────────────────────────────────────────────────

CREATE POLICY "announcements_select" ON public.announcements
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "announcements_insert" ON public.announcements
  FOR INSERT TO authenticated WITH CHECK (public.is_official());

CREATE POLICY "announcements_update" ON public.announcements
  FOR UPDATE TO authenticated USING (public.is_official());

-- ── 7. center_suggestions ─────────────────────────────────────────────────────
-- Any resident can submit a suggestion. Only officials can review/update.

CREATE POLICY "suggestions_select" ON public.center_suggestions
  FOR SELECT TO authenticated USING (true);

CREATE POLICY "suggestions_insert" ON public.center_suggestions
  FOR INSERT TO authenticated WITH CHECK (suggested_by = auth.uid());

CREATE POLICY "suggestions_update" ON public.center_suggestions
  FOR UPDATE TO authenticated USING (public.is_official());

-- ── 8. incident_reports ───────────────────────────────────────────────────────
-- Residents see only their own reports. Officials see all.

CREATE POLICY "reports_select" ON public.incident_reports
  FOR SELECT TO authenticated
  USING (submitted_by = auth.uid() OR public.is_official());

CREATE POLICY "reports_insert" ON public.incident_reports
  FOR INSERT TO authenticated WITH CHECK (submitted_by = auth.uid());

CREATE POLICY "reports_update" ON public.incident_reports
  FOR UPDATE TO authenticated
  USING (submitted_by = auth.uid() OR public.is_official());

-- ── 9. household_profiles ─────────────────────────────────────────────────────
-- Head resident manages their own household. Officials can read all.

CREATE POLICY "household_profiles_select" ON public.household_profiles
  FOR SELECT TO authenticated
  USING (head_resident_id = auth.uid() OR public.is_official());

CREATE POLICY "household_profiles_insert" ON public.household_profiles
  FOR INSERT TO authenticated
  WITH CHECK (head_resident_id = auth.uid() AND resident_id = auth.uid());

CREATE POLICY "household_profiles_update" ON public.household_profiles
  FOR UPDATE TO authenticated USING (head_resident_id = auth.uid());

-- ── 10. household_members ─────────────────────────────────────────────────────

CREATE POLICY "household_members_select" ON public.household_members
  FOR SELECT TO authenticated
  USING (
    household_id IN (
      SELECT id FROM public.household_profiles WHERE head_resident_id = auth.uid()
    )
    OR public.is_official()
  );

CREATE POLICY "household_members_insert" ON public.household_members
  FOR INSERT TO authenticated
  WITH CHECK (
    household_id IN (
      SELECT id FROM public.household_profiles WHERE head_resident_id = auth.uid()
    )
  );

CREATE POLICY "household_members_update" ON public.household_members
  FOR UPDATE TO authenticated
  USING (
    household_id IN (
      SELECT id FROM public.household_profiles WHERE head_resident_id = auth.uid()
    )
  );

CREATE POLICY "household_members_delete" ON public.household_members
  FOR DELETE TO authenticated
  USING (
    household_id IN (
      SELECT id FROM public.household_profiles WHERE head_resident_id = auth.uid()
    )
  );

-- ── 11. family_registrations ──────────────────────────────────────────────────
-- Only the household head (resident_id) can manage their families.

CREATE POLICY "family_reg_select" ON public.family_registrations
  FOR SELECT TO authenticated
  USING (resident_id = auth.uid() OR public.is_official());

CREATE POLICY "family_reg_insert" ON public.family_registrations
  FOR INSERT TO authenticated
  WITH CHECK (
    resident_id = auth.uid()
    AND household_id IN (
      SELECT id FROM public.household_profiles WHERE head_resident_id = auth.uid()
    )
  );

CREATE POLICY "family_reg_update" ON public.family_registrations
  FOR UPDATE TO authenticated USING (resident_id = auth.uid());

CREATE POLICY "family_reg_delete" ON public.family_registrations
  FOR DELETE TO authenticated USING (resident_id = auth.uid());

-- ── 12. family_members ────────────────────────────────────────────────────────

CREATE POLICY "family_members_select" ON public.family_members
  FOR SELECT TO authenticated
  USING (
    registration_id IN (
      SELECT id FROM public.family_registrations WHERE resident_id = auth.uid()
    )
    OR public.is_official()
  );

CREATE POLICY "family_members_insert" ON public.family_members
  FOR INSERT TO authenticated
  WITH CHECK (
    registration_id IN (
      SELECT id FROM public.family_registrations WHERE resident_id = auth.uid()
    )
  );

CREATE POLICY "family_members_update" ON public.family_members
  FOR UPDATE TO authenticated
  USING (
    registration_id IN (
      SELECT id FROM public.family_registrations WHERE resident_id = auth.uid()
    )
  );

CREATE POLICY "family_members_delete" ON public.family_members
  FOR DELETE TO authenticated
  USING (
    registration_id IN (
      SELECT id FROM public.family_registrations WHERE resident_id = auth.uid()
    )
  );

-- ── 13. device_tokens ─────────────────────────────────────────────────────────

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

-- Users can only read/write their own token
CREATE POLICY "device_tokens_select" ON public.device_tokens
  FOR SELECT TO authenticated USING (user_id = auth.uid());

CREATE POLICY "device_tokens_insert" ON public.device_tokens
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

CREATE POLICY "device_tokens_update" ON public.device_tokens
  FOR UPDATE TO authenticated USING (user_id = auth.uid());

-- Service role (Edge Function) can read all tokens to send notifications
-- This is handled automatically by service_role key bypassing RLS.
-- ── 14. evacuation_history ────────────────────────────────────────────────────
-- Residents see their own. Officials see all.

CREATE POLICY "evacuation_history_select" ON public.evacuation_history
  FOR SELECT TO authenticated
  USING (resident_id = auth.uid() OR public.is_official());

CREATE POLICY "evacuation_history_insert" ON public.evacuation_history
  FOR INSERT TO authenticated
  WITH CHECK (resident_id = auth.uid());
