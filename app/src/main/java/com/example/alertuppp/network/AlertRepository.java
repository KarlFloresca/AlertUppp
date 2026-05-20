package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.model.Alert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AlertRepository(Context ctx) {
        client = new SupabaseClient(ctx);
    }

    public void loadActive(Callback<List<Alert>> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("alerts", "select=*&is_active=eq.true&order=issued_at.desc");
                cb.onSuccess(parseList(json));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Load all alerts (for officials). */
    public void loadAll(Callback<List<Alert>> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("alerts", "select=*&order=issued_at.desc");
                cb.onSuccess(parseList(json));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Post a new alert (official only). */
    public void postAlert(String title, String body, String level, String area,
                          Callback<Alert> cb) {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", title);
                obj.put("body", body);
                obj.put("level", levelToDb(level));
                obj.put("area", area);
                obj.put("is_active", true);
                String response = client.post("alerts", obj.toString());
                JSONArray arr = new JSONArray(response);
                cb.onSuccess(fromJson(arr.getJSONObject(0)));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Deactivate an alert. */
    public void deactivate(String alertId, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_active", false);
                client.patch("alerts", "id=eq." + alertId, body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Alert> parseList(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<Alert> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(fromJson(arr.getJSONObject(i)));
        return list;
    }

    private Alert fromJson(JSONObject o) throws JSONException {
        Alert a = new Alert();
        a.setId(o.optString("id"));
        a.setTitle(o.optString("title"));
        a.setBody(o.optString("body"));
        a.setLevel(o.optString("level", "info"));
        a.setArea(o.optString("area"));
        a.setActive(o.optBoolean("is_active", true));
        a.setIssuedAt(o.optString("issued_at"));
        a.setExpiresAt(o.optString("expires_at"));
        return a;
    }

    private String levelToDb(String display) {
        if (display == null) return "info";
        if (display.contains("Danger")) return "danger";
        if (display.contains("Warning")) return "warning";
        return "info";
    }
}
