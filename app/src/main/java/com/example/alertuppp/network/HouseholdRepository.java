package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.model.HouseholdProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CRUD for household_profiles via Supabase REST.
 * Members are managed separately via FamilyRepository (family_members table).
 */
public class HouseholdRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public HouseholdRepository(Context ctx) {
        client = new SupabaseClient(ctx);
    }

    /** Fetch the household profile owned by the given residentId. */
    public void loadProfile(String residentId, Callback<HouseholdProfile> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("household_profiles",
                        "resident_id=eq." + residentId + "&select=*");
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) { cb.onSuccess(null); return; }
                cb.onSuccess(profileFromJson(arr.getJSONObject(0)));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Insert or update a household profile. */
    public void saveProfile(HouseholdProfile profile, String residentId,
                            Callback<HouseholdProfile> cb) {
        executor.execute(() -> {
            try {
                HouseholdProfile saved;
                if (profile.getId() == null || profile.getId().isEmpty()) {
                    saved = insertProfile(profile, residentId);
                } else {
                    saved = updateProfile(profile);
                }
                cb.onSuccess(saved);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HouseholdProfile insertProfile(HouseholdProfile p, String residentId)
            throws IOException, JSONException {
        JSONObject body = profileToJson(p);
        body.put("head_resident_id", residentId);
        body.put("resident_id", residentId);
        String response = client.post("household_profiles", body.toString());
        JSONArray arr = new JSONArray(response);
        if (arr.length() > 0) return profileFromJson(arr.getJSONObject(0));
        // Fallback: reload the just-inserted row
        String json = client.get("household_profiles",
                "resident_id=eq." + residentId + "&select=*");
        JSONArray fetched = new JSONArray(json);
        if (fetched.length() > 0) return profileFromJson(fetched.getJSONObject(0));
        // Last resort: return local object with fields set
        p.setResidentId(residentId);
        p.setHeadResidentId(residentId);
        return p;
    }

    private HouseholdProfile updateProfile(HouseholdProfile p)
            throws IOException, JSONException {
        JSONObject body = profileToJson(p);
        client.patch("household_profiles", "id=eq." + p.getId(), body.toString());
        // PATCH with RLS may return empty array — just return the local object
        return p;
    }

    private JSONObject profileToJson(HouseholdProfile p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("household_name", p.getHouseholdName());
        o.put("address",        p.getAddress());
        o.put("barangay",       p.getBarangay());
        o.put("municipality",   p.getMunicipality());
        o.put("house_type",     houseTypeToDb(p.getHouseType()));
        o.put("near_flood_zone",     p.isNearFloodZone());
        o.put("near_landslide_zone", p.isNearLandslideZone());
        return o;
    }

    private HouseholdProfile profileFromJson(JSONObject o) throws JSONException {
        HouseholdProfile p = new HouseholdProfile();
        p.setId(o.optString("id"));
        p.setHeadResidentId(o.optString("head_resident_id"));
        p.setResidentId(o.optString("resident_id"));
        p.setHouseholdName(o.optString("household_name"));
        p.setAddress(o.optString("address"));
        p.setBarangay(o.optString("barangay"));
        p.setMunicipality(o.optString("municipality"));
        p.setHouseType(dbToHouseType(o.optString("house_type")));
        p.setNearFloodZone(o.optBoolean("near_flood_zone"));
        p.setNearLandslideZone(o.optBoolean("near_landslide_zone"));
        p.setCreatedAt(o.optString("created_at"));
        p.setUpdatedAt(o.optString("updated_at"));
        return p;
    }

    private String houseTypeToDb(String display) {
        if (display == null) return "concrete";
        if (display.startsWith("Wood"))      return "wood";
        if (display.startsWith("Mixed"))     return "mixed";
        if (display.startsWith("Makeshift")) return "makeshift";
        return "concrete";
    }

    private String dbToHouseType(String db) {
        if (db == null) return "Concrete";
        switch (db) {
            case "wood":      return "Wood";
            case "mixed":     return "Mixed (Concrete & Wood)";
            case "makeshift": return "Makeshift / Light Materials";
            default:          return "Concrete";
        }
    }
}
