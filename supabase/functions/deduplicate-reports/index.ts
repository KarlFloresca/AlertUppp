import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

/**
 * Supabase Edge Function: deduplicate-reports
 * Combines AI Deduplication (Multimodal) and Official Notifications.
 * 
 * Logic:
 * 1. Check for duplicates using Gemini 1.5 Flash (Text + Images).
 * 2. If Duplicate: Mark as duplicate and STOP.
 * 3. If Unique: Mark as unique and trigger notify-official-report.
 */

async function fetchGemini(url: string, body: string, retries = 2) {
  for (let i = 0; i < retries; i++) {
    const res = await fetch(url, { 
      method: 'POST', 
      headers: { 'Content-Type': 'application/json' },
      body 
    });
    if (res.status === 429) {
      console.log(`Rate limit (429) hit, retrying in 2s... (Attempt ${i + 1}/${retries})`);
      await new Promise(r => setTimeout(r, 2000));
      continue;
    }
    return res;
  }
  throw new Error("Gemini API rate limit exceeded. Please try again in a minute.");
}

serve(async (req) => {
  try {
    const { record } = await req.json()
    const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY')
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
    const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

    const supabase = createClient(SUPABASE_URL!, SUPABASE_SERVICE_ROLE_KEY!)

    // 1. Fetch nearby reports (Radius check 300m)
    const { data: nearby, error: searchError } = await supabase.rpc('get_nearby_reports', {
      lat: record.latitude,
      lng: record.longitude,
      radius_meters: 300,
      current_report_id: record.id
    })

    if (searchError) throw searchError;

    let isMatch = false;
    let matchId = null;

    if (nearby && nearby.length > 0) {
      // 2. Prepare Multimodal Prompt (Use 'any' to bypass strict part-type checking)
      const parts: any[] = [
        { text: `
          Instructions: Determine if the New Report describes the SAME physical incident as any of the Existing Reports.
          
          GOAL: Prevent map clutter by merging "Most Likely" duplicates.
          
          RULES:
          - If reports are under 200m apart, be HIGHLY LENIENT with descriptions.
          - If reports are under 100m apart, they are almost CERTAINLY the same incident.
          - Look for matching keywords (e.g., "tree", "wire", "flood", "blocked").
          - Use the photos provided to verify if the physical scene or objects are the same.
          
          NEW REPORT:
          - Type: ${record.report_type}
          - Title: ${record.title}
          - Desc: ${record.description}
          - Distance: 0m
          
          EXISTING REPORTS:
          ${nearby.map(n => `[ID: ${n.id}] Type: ${n.report_type}, Title: ${n.title}, Desc: ${n.description}, Distance: ${Math.round(n.distance_meters)}m`).join('\n')}
          
          Return ONLY strict JSON:
          {
            "reasoning": "Quick explanation of context/keywords.",
            "is_match": true/false,
            "match_id": "UUID of match or null"
          }
        ` }
      ];

      // Helper to fetch image and convert to Base64 for Deno
      const getImageData = async (url: string) => {
        const resp = await fetch(url);
        const buffer = await resp.arrayBuffer();
        const uint8 = new Uint8Array(buffer);
        let binary = "";
        for (let i = 0; i < uint8.byteLength; i++) {
          binary += String.fromCharCode(uint8[i]);
        }
        return btoa(binary);
      };

      // Add New Image if exists
      if (record.photo_url) {
        try {
          const base64 = await getImageData(record.photo_url);
          parts.push({ inline_data: { mime_type: "image/jpeg", data: base64 } });
        } catch (e) { console.error("Failed to fetch new image:", e); }
      }

      // Add Nearby Images (Limit to top 3 to save tokens/rate limit)
      for (const n of nearby.slice(0, 3)) {
        if (n.photo_url) {
          try {
            const base64 = await getImageData(n.photo_url);
            parts.push({ inline_data: { mime_type: "image/jpeg", data: base64 } });
          } catch (e) { console.error(`Failed to fetch image for ${n.id}:`, e); }
        }
      }

      const aiResponse = await fetchGemini(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GEMINI_API_KEY}`,
        JSON.stringify({ contents: [{ parts }] })
      );

      const result = await aiResponse.json();
      if (result.candidates && result.candidates[0]) {
        const rawText = result.candidates[0].content.parts[0].text.trim();
        const cleanedJson = rawText.replace(/```json|```/g, "");
        const decision = JSON.parse(cleanedJson);
        isMatch = decision.is_match;
        matchId = decision.match_id;
      }
    }

    // 3. Update Database
    if (isMatch && matchId) {
      await supabase
        .from('incident_reports')
        .update({ is_duplicate: true, parent_report_id: matchId })
        .eq('id', record.id);
      
      console.log(`Report ${record.id} marked as duplicate of ${matchId}`);
      return new Response(JSON.stringify({ status: "duplicate_hidden" }), { status: 200 });
    }

    // 4. If Unique: Trigger Notification to Officials
    // We call the notify function directly to ensure it only happens for unique reports
    console.log(`Report ${record.id} is unique. Triggering notification...`);
    
    await fetch(`${SUPABASE_URL}/functions/v1/notify-official-report`, {
       method: 'POST',
       headers: { 
         'Content-Type': 'application/json',
         'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}` 
       },
       body: JSON.stringify({ record })
    });

    return new Response(JSON.stringify({ status: "unique_notified" }), { status: 200 });

  } catch (err) {
    console.error("Processing error:", err.message);
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
})
