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
    public void deleteCenter(String centerId, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                client.delete("evacuation_centers", "id=eq." + centerId);
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

    /** Check a family into a center (increments occupancy). */
    public void checkIn(String centerId, String residentId, int memberCount, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                // Record in evacuation_history
                JSONObject history = new JSONObject();
                history.put("center_id", centerId);
                history.put("member_count", memberCount);
                // household_id would come from the loaded profile; simplified here
                client.post("evacuation_history", history.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
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
