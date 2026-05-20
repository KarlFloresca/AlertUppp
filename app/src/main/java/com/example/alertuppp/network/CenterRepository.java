package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.model.EvacuationCenter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CenterRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CenterRepository(Context ctx) {
        client = new SupabaseClient(ctx);
    }

    /** Load all evacuation centers ordered by name. */
    public void loadAll(Callback<List<EvacuationCenter>> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("evacuation_centers", "select=*&order=name.asc");
                List<EvacuationCenter> list = parseList(json);
                cb.onSuccess(list);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Add a new evacuation center (official only). */
    public void addCenter(EvacuationCenter center, String managedBy, Callback<EvacuationCenter> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name",              center.getName());
                body.put("address",           center.getAddress());
                body.put("municipality",      center.getMunicipality());
                body.put("max_capacity",      center.getMaxCapacity());
                body.put("current_occupancy", 0);
                body.put("status",            "available");
                if (center.getLatitude()  != 0) body.put("latitude",  center.getLatitude());
                if (center.getLongitude() != 0) body.put("longitude", center.getLongitude());
                if (managedBy != null && !managedBy.isEmpty()) body.put("managed_by", managedBy);

                String response = client.post("evacuation_centers", body.toString());
                JSONArray arr = new JSONArray(response);
                cb.onSuccess(fromJson(arr.getJSONObject(0)));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Update all editable fields of a center (official only). */
    public void updateCenter(EvacuationCenter center, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name",         center.getName());
                body.put("address",      center.getAddress());
                body.put("municipality", center.getMunicipality());
                body.put("max_capacity", center.getMaxCapacity());
                body.put("status",       center.getStatus() != null ? center.getStatus() : "available");
                if (center.getLatitude()  != 0) body.put("latitude",  center.getLatitude());
                if (center.getLongitude() != 0) body.put("longitude", center.getLongitude());
                client.patch("evacuation_centers", "id=eq." + center.getId(), body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Delete a center by id (official only). */
    public void deleteCenter(EvacuationCenter center, Callback<Void> cb) {
        if (center.getCurrentOccupancy() > 0) {
            cb.onError("Cannot delete a center that currently has occupants. Please move families first.");
            return;
        }
        executor.execute(() -> {
            try {
                client.delete("evacuation_centers", "id=eq." + center.getId());
                cb.onSuccess(null);
            } catch (IOException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Update occupancy for a center (official only). */
    public void updateOccupancy(String centerId, int newOccupancy, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("current_occupancy", newOccupancy);
                client.patch("evacuation_centers", "id=eq." + centerId, body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    public void adjustOccupancy(String centerId, int delta, Callback<Void> cb) {
        if (centerId == null || centerId.isEmpty() || "null".equals(centerId)) {
            if (cb != null) cb.onSuccess(null);
            return;
        }
        executor.execute(() -> {
            try {
                String json = client.get("evacuation_centers", "id=eq." + centerId + "&select=current_occupancy,max_capacity");
                JSONArray arr = new JSONArray(json);
                if (arr.length() > 0) {
                    JSONObject o = arr.getJSONObject(0);
                    int current = o.optInt("current_occupancy", 0);
                    int max = o.optInt("max_capacity", 0);
                    int next = Math.max(0, current + delta);
                    
                    JSONObject body = new JSONObject();
                    body.put("current_occupancy", next);
                    body.put("status", next >= max ? "full" : "available");
                    client.patch("evacuation_centers", "id=eq." + centerId, body.toString());
                }
                if (cb != null) cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                if (cb != null) cb.onError(e.getMessage());
            }
        });
    }

    /** Check a family into a center (increments occupancy). */
    /** 
     * Complex check-in: 
     * 1. Detects if user is already in this center.
     * 2. Handles transfers (decrements old center, increments new).
     * 3. Updates all family registrations for the resident.
     */
    public void checkInHousehold(String newCenterId, String residentId, int memberCount, Callback<String> cb) {
        executor.execute(() -> {
            try {
                // 1. Find if already checked in elsewhere
                String regJson = client.get("family_registrations", "resident_id=eq." + residentId + "&select=id,center_id");
                JSONArray regs = new JSONArray(regJson);
                
                String oldCenterId = null;
                for (int i = 0; i < regs.length(); i++) {
                    String cid = regs.getJSONObject(i).optString("center_id", "");
                    if (!cid.isEmpty() && !"null".equals(cid)) {
                        oldCenterId = cid;
                        break;
                    }
                }

                if (newCenterId.equals(oldCenterId)) {
                    cb.onSuccess("ALREADY_HERE");
                    return;
                }

                // 2. Handle Decrement of Old Center (Transfer)
                if (oldCenterId != null && !oldCenterId.isEmpty()) {
                    String oldJson = client.get("evacuation_centers", "id=eq." + oldCenterId + "&select=current_occupancy");
                    JSONArray oldArr = new JSONArray(oldJson);
                    if (oldArr.length() > 0) {
                        int oldOcc = oldArr.getJSONObject(0).optInt("current_occupancy", 0);
                        JSONObject decBody = new JSONObject();
                        decBody.put("current_occupancy", Math.max(0, oldOcc - memberCount));
                        decBody.put("status", "available");
                        client.patch("evacuation_centers", "id=eq." + oldCenterId, decBody.toString());
                    }
                }

                // 3. Increment New Center
                String newJson = client.get("evacuation_centers", "id=eq." + newCenterId + "&select=current_occupancy,max_capacity");
                JSONArray newArr = new JSONArray(newJson);
                if (newArr.length() > 0) {
                    JSONObject centerObj = newArr.getJSONObject(0);
                    int current = centerObj.optInt("current_occupancy", 0);
                    int max = centerObj.optInt("max_capacity", 0);
                    int next = current + memberCount;
                    
                    JSONObject incBody = new JSONObject();
                    incBody.put("current_occupancy", next);
                    if (next >= max) incBody.put("status", "full");
                    client.patch("evacuation_centers", "id=eq." + newCenterId, incBody.toString());
                }

                // 4. Update all family registrations
                JSONObject regBody = new JSONObject();
                regBody.put("center_id", newCenterId);
                client.patch("family_registrations", "resident_id=eq." + residentId, regBody.toString());

                // 5. Record History
                JSONObject history = new JSONObject();
                history.put("center_id", newCenterId);
                history.put("resident_id", residentId);
                history.put("member_count", memberCount);
                client.post("evacuation_history", history.toString());

                cb.onSuccess(oldCenterId == null ? "CHECKED_IN" : "TRANSFERRED");

            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Simple check-in (legacy support). */
    public void checkIn(String centerId, String residentId, int memberCount, Callback<Void> cb) {
        checkInHousehold(centerId, residentId, memberCount, new Callback<String>() {
            @Override public void onSuccess(String result) { cb.onSuccess(null); }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    // ── JSON ↔ Model ──────────────────────────────────────────────────────────

    private List<EvacuationCenter> parseList(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<EvacuationCenter> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(fromJson(arr.getJSONObject(i)));
        }
        return list;
    }

    public static EvacuationCenter fromJson(JSONObject o) throws JSONException {
        EvacuationCenter c = new EvacuationCenter();
        c.setId(o.optString("id"));
        c.setName(o.optString("name"));
        c.setAddress(o.optString("address"));
        c.setMunicipality(o.optString("municipality"));
        c.setLatitude(o.optDouble("latitude", 0));
        c.setLongitude(o.optDouble("longitude", 0));
        c.setMaxCapacity(o.optInt("max_capacity", 0));
        c.setCurrentOccupancy(o.optInt("current_occupancy", 0));
        c.setStatus(o.optString("status", "available"));
        // facilities stored as JSON extras or defaults
        c.setHasWater(true);
        c.setHasFood(true);
        c.setHasMedical(o.optBoolean("has_medical", false));
        return c;
    }
}
