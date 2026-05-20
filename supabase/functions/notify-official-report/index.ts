// Supabase Edge Function: notify-official-report
// Sends a push notification to all officials when a new incident report is submitted.
//
// Deploy:
//   supabase functions deploy notify-official-report
//
// Set secret (reuse the same FCM service account):
//   supabase secrets set FCM_SERVICE_ACCOUNT='{...}'

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { create, getNumericDate } from "https://deno.land/x/djwt@v2.8/mod.ts";

async function getAccessToken(serviceAccount: Record<string, string>): Promise<string> {
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

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });

  const json = await res.json();
  return json.access_token;
}

async function sendFcmMessage(
  token: string,
  title: string,
  body: string,
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
        notification: { title, body },
        data: { title, body, type: "report" },
        android: { priority: "HIGH" },
      },
    }),
  });
}

serve(async (req) => {
  try {
    const payload = await req.json();
    const report = payload.record;

    const typeLabel = report.report_type.toUpperCase();
    const title = `🚨 New Incident: ${typeLabel}`;
    const body = `Reported at ${report.landmark || 'unknown location'}. Description: ${report.description}`;

    const serviceAccount = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!);
    const accessToken = await getAccessToken(serviceAccount);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Get all official tokens
    const { data: tokens, error } = await supabase
      .from("device_tokens")
      .select(`
        fcm_token,
        profiles!inner(role)
      `)
      .eq("profiles.role", "official");

    if (error) throw error;
    if (!tokens?.length) return new Response("no officials to notify", { status: 200 });

    await Promise.allSettled(
      tokens.map((t: any) =>
        sendFcmMessage(t.fcm_token, title, body, serviceAccount.project_id, accessToken)
      )
    );

    return new Response(JSON.stringify({ sent: tokens.length }), { status: 200 });
  } catch (err) {
    return new Response(String(err), { status: 500 });
  }
});
