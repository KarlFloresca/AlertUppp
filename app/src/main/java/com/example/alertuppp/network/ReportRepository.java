package com.example.alertuppp.network;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.alertuppp.model.IncidentReport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ReportRepository(Context ctx) {
        client = new SupabaseClient(ctx);
    }

    /** Submit a new incident report. */
    public void submit(IncidentReport report, String userId, Callback<IncidentReport> cb) {
        submitInternal(report, userId, null, cb);
    }

    /**
     * Upload photo to the "report" bucket then submit the report with photo_url.
     * @param photoBytes  raw JPEG/PNG bytes from the picked image
     * @param mimeType    e.g. "image/jpeg"
     */
    public void submitWithPhoto(IncidentReport report, String userId,
                                byte[] photoBytes, String mimeType,
                                Callback<IncidentReport> cb) {
        executor.execute(() -> {
            try {
                // 1. Upload image → get public URL
                String fileName = "report_" + userId + "_" + System.currentTimeMillis() + ".jpg";
                String photoUrl = client.uploadStorage("report", fileName, photoBytes, mimeType);

                // 2. Submit report with photo_url on the main executor thread
                submitInternal(report, userId, photoUrl, cb);
            } catch (IOException e) {
                cb.onError("Photo upload failed: " + e.getMessage());
            }
        });
    }

    private void submitInternal(IncidentReport report, String userId,
                                @Nullable String photoUrl, Callback<IncidentReport> cb) {
        executor.execute(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("submitted_by", userId);
                obj.put("report_type", report.getReportType());
                obj.put("title", report.getTitle());
                obj.put("description", report.getDescription() != null ? report.getDescription() : "");
                if (report.getFloodLevel() != null && !report.getFloodLevel().isEmpty())
                    obj.put("flood_level", report.getFloodLevel());
                if (report.getLatitude() != 0) obj.put("latitude", report.getLatitude());
                if (report.getLongitude() != 0) obj.put("longitude", report.getLongitude());
                if (report.getLandmark() != null && !report.getLandmark().isEmpty())
                    obj.put("landmark", report.getLandmark());
                if (photoUrl != null) obj.put("photo_url", photoUrl);
                obj.put("status", "pending");

                String response = client.post("incident_reports", obj.toString());
                JSONArray arr = new JSONArray(response);
                cb.onSuccess(fromJson(arr.getJSONObject(0)));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Load all reports (officials). Optionally filter by type. */
    public void loadAll(String typeFilter, @Nullable String statusFilter, Callback<List<IncidentReport>> cb) {
        executor.execute(() -> {
            try {
                String query = "select=*&order=created_at.desc";
                if (typeFilter != null && !typeFilter.equals("all"))
                    query += "&report_type=eq." + typeFilter;
                if (statusFilter != null) {
                    if (statusFilter.contains(",")) query += "&status=in.(" + statusFilter + ")";
                    else query += "&status=eq." + statusFilter;
                }
                String json = client.get("incident_reports", query);
                cb.onSuccess(parseList(json));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Load reports submitted by a specific user. */
    public void loadMine(String userId, Callback<List<IncidentReport>> cb) {
        executor.execute(() -> {
            try {
                String json = client.get("incident_reports",
                        "submitted_by=eq." + userId + "&select=*&order=created_at.desc");
                cb.onSuccess(parseList(json));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Update report status (official action). */
    public void updateStatus(String reportId, String newStatus, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", newStatus);
                client.patch("incident_reports", "id=eq." + reportId, body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Update report status and assign a team (Respond action). */
    public void respond(String reportId, String teamName, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "ongoing");
                body.put("assigned_team", teamName);
                client.patch("incident_reports", "id=eq." + reportId, body.toString());
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Fetch a single report by its ID (useful for status polling). */
    public void getReportById(String reportId, Callback<IncidentReport> cb) {
        executor.execute(() -> {
            try {
                String response = client.get("incident_reports", "id=eq." + reportId + "&select=*");
                JSONArray arr = new JSONArray(response);
                if (arr.length() > 0) {
                    cb.onSuccess(fromJson(arr.getJSONObject(0)));
                } else {
                    cb.onError("Report not found");
                }
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<IncidentReport> parseList(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<IncidentReport> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(fromJson(arr.getJSONObject(i)));
        return list;
    }

    private IncidentReport fromJson(JSONObject o) throws JSONException {
        IncidentReport r = new IncidentReport();
        r.setId(o.optString("id"));
        r.setSubmittedBy(o.optString("submitted_by"));
        r.setReportType(o.optString("report_type", "flood"));
        r.setTitle(o.optString("title"));
        r.setDescription(o.optString("description"));
        r.setFloodLevel(o.optString("flood_level"));
        r.setLatitude(o.optDouble("latitude", 0));
        r.setLongitude(o.optDouble("longitude", 0));
        r.setLandmark(o.optString("landmark"));
        r.setPhotoUrl(o.optString("photo_url"));
        r.setStatus(o.optString("status", "pending"));
        r.setAssignedTeam(o.optString("assigned_team"));
        r.setCreatedAt(o.optString("created_at"));
        r.setDuplicate(o.optBoolean("is_duplicate", false));
        r.setParentReportId(o.optString("parent_report_id"));
        return r;
    }
}
