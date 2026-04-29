// Supabase Edge Function: send-alert-notification
// Uses FCM v1 API (HTTP v1) with service account OAuth2.
//
// Deploy:
//   supabase functions deploy send-alert-notification
//
// Set secret (paste the entire service account JSON as one line):
//   supabase secrets set FCM_SERVICE_ACCOUNT='{"type":"service_account","project_id":"...","private_key":"...","client_email":"...",...}'
//
// Create a Supabase Database Webhook:
//   Table: alerts, Event: INSERT
//   Type: Supabase Edge Functions → send-alert-notification

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create, getNumericDate } from "https://deno.land/x/djwt@v2.8/mod.ts";

// ── OAuth2: get a short-lived access token from the service account ───────────

async function getAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  // Import the RSA private key
  const pemKey = serviceAccount.private_key.replace(/\\n/g, "\n");
  const keyData = pemKey
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");

  const binaryKey = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  // Build JWT for Google OAuth2
  const jwt = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: getNumericDate(0),
      exp: getNumericDate(3600),
    },
    cryptoKey
  );

  // Exchange JWT for access token
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });

  const json = await res.json();
  if (!json.access_token) throw new Error("Failed to get access token: " + JSON.stringify(json));
  return json.access_token;
}

// ── Send one FCM v1 message ───────────────────────────────────────────────────

async function sendFcmMessage(
  token: string,
  title: string,
  body: string,
  level: string,
  area: string,
  projectId: string,
  accessToken: string
): Promise<void> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      message: {
        token,
        notification: {
          title: `${level === "danger" ? "🔴" : level === "warning" ? "🟠" : "🔵"} ${title}`,
          body: area !== "Camarines Norte" ? `${body}\n📍 ${area}` : body,
        },
        data: { title, body, level, area },
        android: {
          priority: level === "danger" ? "HIGH" : "NORMAL",
          notification: { sound: "default" },
        },
      },
    }),
  });
}

// ── Main handler ──────────────────────────────────────────────────────────────

serve(async (req) => {
  try {
    const payload = await req.json();
    const alert = payload.record;

    if (!alert?.is_active) return new Response("skipped", { status: 200 });

    const title = alert.title ?? "Emergency Alert";
    const body  = alert.body  ?? "";
    const level = alert.level ?? "info";
    const area  = alert.area  ?? "Camarines Norte";

    // Parse service account
    const serviceAccount = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!);
    const projectId = serviceAccount.project_id;

    // Get OAuth2 access token
    const accessToken = await getAccessToken(serviceAccount);

    // Build Supabase admin client
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Fetch target FCM tokens
    let tokensQuery = supabase.from("device_tokens").select("fcm_token");

    if (area !== "Camarines Norte") {
      const parts = area.split(",").map((s: string) => s.trim());
      const municipality = parts.length >= 2 ? parts[parts.length - 2] : null;
      const barangayPart = parts[0].startsWith("Brgy.")
        ? parts[0].replace("Brgy.", "").trim()
        : null;

      if (municipality) {
        let hq = supabase
          .from("household_profiles")
          .select("resident_id")
          .eq("municipality", municipality);
        if (barangayPart) hq = hq.eq("barangay", barangayPart);

        const { data: households } = await hq;
        const userIds = (households ?? []).map((h: any) => h.resident_id);
        if (userIds.length > 0) tokensQuery = tokensQuery.in("user_id", userIds);
      }
    }

    const { data: tokens, error } = await tokensQuery;
    if (error) throw error;
    if (!tokens?.length) return new Response("no tokens", { status: 200 });

    // Send to each token (FCM v1 requires one message per token)
    await Promise.allSettled(
      tokens.map((t: any) =>
        sendFcmMessage(t.fcm_token, title, body, level, area, projectId, accessToken)
      )
    );

    return new Response(JSON.stringify({ sent: tokens.length }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Error:", err);
    return new Response(String(err), { status: 500 });
  }
});
