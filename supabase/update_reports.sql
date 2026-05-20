-- ============================================================
-- AlertUp — Reports & Notifications Update
-- ============================================================

-- 1. Table Updates
ALTER TABLE public.incident_reports 
ADD COLUMN IF NOT EXISTS assigned_team text;

-- 2. Notification Instructions for Officials
-- This function has been created in: supabase/functions/notify-official-report/index.ts
--
-- To enable automatic notifications when a resident submits a report:
--
-- Step A: Deploy the function
--   supabase functions deploy notify-official-report
--
-- Step B: Set the FCM secret (if not already set)
--   supabase secrets set FCM_SERVICE_ACCOUNT='{...}'
--
-- Step C: Create the Database Webhook in the Supabase Dashboard:
--   - Name: notify_officials_on_report
--   - Table: incident_reports
--   - Events: INSERT
--   - Type: Supabase Edge Functions
-- 3. AI Deduplication Setup
-- ============================================================

-- Enable PostGIS extensions for radius searching
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

-- Add columns to track duplicates
ALTER TABLE public.incident_reports 
ADD COLUMN IF NOT EXISTS parent_report_id UUID REFERENCES incident_reports(id),
ADD COLUMN IF NOT EXISTS is_duplicate BOOLEAN DEFAULT FALSE;

-- Function for radius searching with distance calculation
CREATE OR REPLACE FUNCTION get_nearby_reports(
  lat DOUBLE PRECISION, 
  lng DOUBLE PRECISION, 
  radius_meters DOUBLE PRECISION,
  current_report_id UUID
)
RETURNS TABLE (
  id UUID,
  report_type TEXT,
  title TEXT,
  description TEXT,
  landmark TEXT,
  distance_meters FLOAT
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    ir.id, ir.report_type, ir.title, ir.description, ir.landmark,
    (point(lng, lat) <@> point(ir.longitude, ir.latitude)) * 1609.34 as distance_meters
  FROM incident_reports ir
  WHERE 
    ir.id != current_report_id
    AND ir.status != 'resolved'
    AND ir.is_duplicate = FALSE
    AND (point(lng, lat) <@> point(ir.longitude, ir.latitude)) * 1609.34 <= radius_meters;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
