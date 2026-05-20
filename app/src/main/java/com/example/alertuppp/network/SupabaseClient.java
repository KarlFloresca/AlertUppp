package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.R;
import com.example.alertuppp.SessionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Supabase REST client using HttpURLConnection (no extra dependencies).
 * All calls are blocking — run them off the main thread.
 */
public class SupabaseClient {

    private final String baseUrl;
    private final String anonKey;
    private String authToken; // set after login

    public SupabaseClient(Context ctx) {
        baseUrl = ctx.getString(R.string.supabase_url);
        anonKey = ctx.getString(R.string.supabase_anon_key);
        // Load saved token from session
        SessionManager session = new SessionManager(ctx);
        authToken = session.getToken().isEmpty() ? anonKey : session.getToken();
    }

    /** GET /rest/v1/{table}?{query} */
    public String get(String table, String query) throws IOException {
        String urlStr = baseUrl + "/rest/v1/" + table + (query != null ? "?" + query : "");
        HttpURLConnection conn = open(urlStr, "GET");
        return readResponse(conn);
    }

    /** POST /rest/v1/{table} with JSON body. Returns response body. */
    public String post(String table, String json) throws IOException {
        HttpURLConnection conn = open(baseUrl + "/rest/v1/" + table, "POST");
        conn.setRequestProperty("Prefer", "return=representation");
        writeBody(conn, json);
        return readResponse(conn);
    }

    /** PATCH /rest/v1/{table}?{query} with JSON body. */
    public String patch(String table, String query, String json) throws IOException {
        String urlStr = baseUrl + "/rest/v1/" + table + (query != null ? "?" + query : "");
        HttpURLConnection conn = open(urlStr, "PATCH");
        conn.setRequestProperty("Prefer", "return=representation");
        writeBody(conn, json);
        return readResponse(conn);
    }

    /** DELETE /rest/v1/{table}?{query} */
    public void delete(String table, String query) throws IOException {
        String urlStr = baseUrl + "/rest/v1/" + table + (query != null ? "?" + query : "");
        HttpURLConnection conn = open(urlStr, "DELETE");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            // Read error body if available
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            } catch (Exception ignored) {}
            conn.disconnect();
            throw new IOException("HTTP " + code + (sb.length() > 0 ? ": " + sb : ""));
        }
        conn.disconnect();
    }

    /**
     * Upload raw bytes to Supabase Storage.
     * PUT /storage/v1/object/{bucket}/{path}
     * Returns the public URL of the uploaded file.
     */
    public String uploadStorage(String bucket, String path, byte[] data, String mimeType)
            throws IOException {
        String urlStr = baseUrl + "/storage/v1/object/" + bucket + "/" + path;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", anonKey);
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setRequestProperty("Content-Type", mimeType);
        conn.setRequestProperty("x-upsert", "true");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }
        int code = conn.getResponseCode();
        conn.disconnect();
        if (code < 200 || code >= 300) throw new IOException("Storage upload failed: HTTP " + code);
        // Return the public URL
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private HttpURLConnection open(String urlStr, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", anonKey);
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String json) throws IOException {
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        boolean ok = code >= 200 && code < 300;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                ok ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            if (!ok) throw new IOException("HTTP " + code + ": " + sb);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
