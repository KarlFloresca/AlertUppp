package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.FamilyMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CRUD for family_registrations and family_members via Supabase REST.
 *
 * Schema:
 *   family_registrations(id, household_id, resident_id, family_name, center_id, registered_at)
 *   family_members(id, registration_id, full_name, age, notes, created_at)
 *
 * A household_profile has many family_registrations.
 * Each family_registration has many family_members.
 * center_id on family_registrations is nullable — assigned later when evacuating.
 */
public class FamilyRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FamilyRepository(Context ctx) {
        client = new SupabaseClient(ctx);
    }

    // ── Load all families for a household ────────────────────────────────────

    public void loadFamilies(String householdId, Callback<List<Family>> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("family_registrations",
                        "household_id=eq." + householdId
                        + "&select=*,evacuation_centers(name)"
                        + "&order=registered_at.desc");
                JSONArray arr = new JSONArray(json);
                List<Family> families = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    Family f = familyFromJson(arr.getJSONObject(i));
                    f.setMembers(loadMembersSync(f.getId()));
                    families.add(f);
                }
                cb.onSuccess(families);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Add a new family (no center required) ────────────────────────────────

    public void addFamily(String householdId, String residentId, String familyName, Callback<Family> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("household_id", householdId);
                body.put("resident_id", residentId);
                body.put("family_name", familyName != null && !familyName.isEmpty() ? familyName : "Family");
                // center_id intentionally omitted — nullable
                String response = client.post("family_registrations", body.toString());
                JSONArray arr = new JSONArray(response);
                Family f = familyFromJson(arr.getJSONObject(0));
                f.setMembers(new ArrayList<>());
                cb.onSuccess(f);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Update family name and optionally assign/change center. */
    public void updateFamily(Family family, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("family_name", family.getFamilyName());
                if (family.getCenterId() != null && !family.getCenterId().isEmpty())
                    body.put("center_id", family.getCenterId());
                client.patch("family_registrations", "id=eq." + family.getId(), body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Delete a family registration (cascades members via FK) ────────────────

    public void deleteFamily(String registrationId, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                // Delete members first (no cascade in schema)
                client.delete("family_members", "registration_id=eq." + registrationId);
                client.delete("family_registrations", "id=eq." + registrationId);
                cb.onSuccess(null);
            } catch (IOException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Add a member to a family ──────────────────────────────────────────────

    public void addMember(String registrationId, FamilyMember member, Callback<FamilyMember> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("registration_id", registrationId);
                body.put("full_name", member.getFullName());
                body.put("age", member.getAge());
                if (member.getNotes() != null && !member.getNotes().isEmpty())
                    body.put("notes", member.getNotes());
                String response = client.post("family_members", body.toString());
                JSONArray arr = new JSONArray(response);
                cb.onSuccess(memberFromJson(arr.getJSONObject(0)));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Update a member ───────────────────────────────────────────────────────

    public void updateMember(FamilyMember member, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("full_name", member.getFullName());
                body.put("age", member.getAge());
                if (member.getNotes() != null) body.put("notes", member.getNotes());
                client.patch("family_members", "id=eq." + member.getId(), body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Delete a member ───────────────────────────────────────────────────────

    public void deleteMember(String memberId, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                client.delete("family_members", "id=eq." + memberId);
                cb.onSuccess(null);
            } catch (IOException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<FamilyMember> loadMembersSync(String registrationId)
            throws IOException, JSONException {
        String json = client.get("family_members",
                "registration_id=eq." + registrationId + "&order=created_at.asc");
        JSONArray arr = new JSONArray(json);
        List<FamilyMember> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(memberFromJson(arr.getJSONObject(i)));
        return list;
    }

    private Family familyFromJson(JSONObject o) throws JSONException {
        Family f = new Family();
        f.setId(o.optString("id"));
        f.setHouseholdId(o.optString("household_id"));
        f.setResidentId(o.optString("resident_id"));
        f.setFamilyName(o.optString("family_name", "Family"));
        String centerId = o.optString("center_id", "");
        if (!centerId.isEmpty()) f.setCenterId(centerId);
        f.setRegisteredAt(o.optString("registered_at"));
        JSONObject center = o.optJSONObject("evacuation_centers");
        if (center != null) f.setCenterName(center.optString("name"));
        return f;
    }

    private FamilyMember memberFromJson(JSONObject o) throws JSONException {
        FamilyMember m = new FamilyMember();
        m.setId(o.optString("id"));
        m.setRegistrationId(o.optString("registration_id"));
        m.setFullName(o.optString("full_name"));
        m.setAge(o.optInt("age", 0));
        m.setNotes(o.optString("notes", ""));
        return m;
    }

    public void shutdown() { executor.shutdownNow(); }
}
