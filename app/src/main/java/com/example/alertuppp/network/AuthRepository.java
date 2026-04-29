package com.example.alertuppp.network;

import android.content.Context;

import com.example.alertuppp.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles Supabase Auth (sign-up, sign-in, sign-out) via the GoTrue REST API.
 * All network calls run on a background thread; results are delivered via Callback.
 */
public class AuthRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    /** Holds the auth session returned after sign-in / sign-up. */
    public static class AuthSession {
        public final String accessToken;
        public final String refreshToken;
        public final String userId;       // UUID from auth.users
        public final String email;

        public AuthSession(String accessToken, String refreshToken,
                           String userId, String email) {
            this.accessToken  = accessToken;
            this.refreshToken = refreshToken;
            this.userId       = userId;
            this.email        = email;
        }
    }

    private final String baseUrl;
    private final String anonKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AuthRepository(Context ctx) {
        baseUrl = ctx.getString(R.string.supabase_url);
        anonKey = ctx.getString(R.string.supabase_anon_key);
    }

    // ── Sign Up ───────────────────────────────────────────────────────────────

    /**
     * Creates a new Supabase Auth user, then inserts a row into public.profiles.
     *
     * @param email        user email
     * @param password     plain-text password (min 6 chars)
     * @param fullName     display name
     * @param phone        phone number (optional)
     * @param municipality municipality name
     * @param role         "resident" or "official"
     */
    public void signUp(String email, String password,
                       String fullName, String phone,
                       String municipality, String role,
                       Callback<AuthSession> cb) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject();
                data.put("full_name", fullName);
                data.put("phone", phone != null ? phone : "");
                data.put("municipality", municipality != null ? municipality : "");
                data.put("role", role);

                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("data", data);

                String response = post(baseUrl + "/auth/v1/signup", body.toString(), null);
                android.util.Log.d("AuthRepository", "signup response: " + response);
                JSONObject json = new JSONObject(response);

                // Only call onError if Supabase explicitly returned an error field
                if (json.has("error_description")) {
                    cb.onError(json.optString("error_description"));
                    return;
                }
                if (json.has("code") && json.has("msg")) {
                    cb.onError(json.optString("msg"));
                    return;
                }

                // Auth user was created — navigate to verify screen regardless
                // of whether the token is present (email confirmation pending)
                String userId      = "";
                JSONObject userObj = json.optJSONObject("user");
                if (userObj != null) userId = userObj.optString("id", "");

                cb.onSuccess(new AuthSession(
                        json.optString("access_token", ""),
                        json.optString("refresh_token", ""),
                        userId,
                        email));

            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Sign In ───────────────────────────────────────────────────────────────

    /**
     * Signs in with email + password. Returns an AuthSession on success.
     */
    public void signIn(String email, String password, Callback<AuthSession> cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                String url = baseUrl + "/auth/v1/token?grant_type=password";
                String response = post(url, body.toString(), null);
                JSONObject json = new JSONObject(response);

                if (json.has("error") || json.has("error_description")) {
                    String msg = json.optString("error_description",
                            json.optString("error", "Invalid email or password"));
                    cb.onError(msg);
                    return;
                }

                String accessToken  = json.optString("access_token");
                String refreshToken = json.optString("refresh_token");
                JSONObject userObj  = json.optJSONObject("user");
                String userId       = userObj != null ? userObj.optString("id") : "";

                cb.onSuccess(new AuthSession(accessToken, refreshToken, userId, email));

            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Fetch Profile ─────────────────────────────────────────────────────────

    /**
     * Fetches the public.profiles row for the given userId.
     * Falls back gracefully if the row is missing or RLS blocks access —
     * extracts name/role from the auth user object instead so login never fails.
     */
    public void fetchProfile(String userId, String accessToken,
                             Callback<JSONObject> cb) {
        executor.execute(() -> {
            try {
                String url = baseUrl + "/rest/v1/profiles?id=eq." + userId + "&select=*";
                String response = get(url, accessToken);
                org.json.JSONArray arr = new org.json.JSONArray(response);
                if (arr.length() > 0) {
                    cb.onSuccess(arr.getJSONObject(0));
                    return;
                }
                // Row missing — return a minimal object so login still works.
                // The trigger will create the real row on next sign-up; for existing
                // users after a schema reset, this keeps them logged in.
                JSONObject fallback = new JSONObject();
                fallback.put("id", userId);
                fallback.put("full_name", "User");
                fallback.put("role", "resident");
                fallback.put("municipality", "");
                cb.onSuccess(fallback);
            } catch (IOException | JSONException e) {
                // Network or parse error — still don't block login
                try {
                    JSONObject fallback = new JSONObject();
                    fallback.put("id", userId);
                    fallback.put("full_name", "User");
                    fallback.put("role", "resident");
                    fallback.put("municipality", "");
                    cb.onSuccess(fallback);
                } catch (JSONException ignored) {
                    cb.onError(e.getMessage());
                }
            }
        });
    }

    // ── Sign Out ──────────────────────────────────────────────────────────────

    public void signOut(String accessToken, Callback<Void> cb) {
        executor.execute(() -> {
            try {
                post(baseUrl + "/auth/v1/logout", "{}", accessToken);
                cb.onSuccess(null);
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String post(String urlStr, String json, String token)
            throws IOException, JSONException {
        HttpURLConnection conn = openConn(urlStr, "POST", token);
        writeBody(conn, json);
        return readResponse(conn);
    }

    private String get(String urlStr, String token) throws IOException {
        HttpURLConnection conn = openConn(urlStr, "GET", token);
        return readResponse(conn);
    }

    private HttpURLConnection openConn(String urlStr, String method, String token)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", anonKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        // Always send JWT when available — required for RLS; fall back to anon key
        String bearer = (token != null && !token.isEmpty()) ? token : anonKey;
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
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
        // Read whichever stream is available
        java.io.InputStream stream = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) stream = conn.getInputStream(); // fallback
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String body = sb.toString();
            android.util.Log.d("AuthRepository", "HTTP " + code + " body: " + body);
            if (code < 200 || code >= 300) {
                // Extract the most specific error message from the JSON body
                try {
                    JSONObject err = new JSONObject(body);
                    String msg = err.optString("error_description",
                            err.optString("msg",
                            err.optString("message",
                            err.optString("error", "HTTP " + code))));
                    throw new IOException(msg);
                } catch (JSONException e) {
                    throw new IOException(body.isEmpty() ? "HTTP " + code : body);
                }
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }
}
