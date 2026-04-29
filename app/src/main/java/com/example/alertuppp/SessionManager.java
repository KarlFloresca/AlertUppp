package com.example.alertuppp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lightweight session store backed by SharedPreferences.
 * Replace with a proper auth token flow when integrating Supabase Auth.
 */
public class SessionManager {

    private static final String PREF_NAME  = "alertup_session";
    private static final String KEY_USER_ID   = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_ROLE      = "role";       // "resident" | "official"
    private static final String KEY_MUNICIPALITY = "municipality";
    private static final String KEY_TOKEN     = "token";

    private final SharedPreferences prefs;

    public SessionManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void save(String userId, String name, String role, String municipality, String token) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_ROLE, role)
                .putString(KEY_MUNICIPALITY, municipality)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return prefs.contains(KEY_USER_ID) && !prefs.getString(KEY_USER_ID, "").isEmpty();
    }

    public String getUserId()      { return prefs.getString(KEY_USER_ID, ""); }
    public String getUserName()    { return prefs.getString(KEY_USER_NAME, "User"); }
    public String getRole()        { return prefs.getString(KEY_ROLE, "resident"); }
    public String getMunicipality(){ return prefs.getString(KEY_MUNICIPALITY, ""); }
    public String getToken()       { return prefs.getString(KEY_TOKEN, ""); }

    public boolean isOfficial()    { return "official".equals(getRole()); }

    /** Convenience: sign out and clear session. */
    public void logout(android.content.Context ctx) {
        // Fire-and-forget network sign-out (best effort)
        new com.example.alertuppp.network.AuthRepository(ctx)
                .signOut(getToken(), new com.example.alertuppp.network.AuthRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {}
                    @Override public void onError(String m) {}
                });
        clear();
    }
}
