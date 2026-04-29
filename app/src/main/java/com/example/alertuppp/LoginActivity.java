package com.example.alertuppp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.alertuppp.network.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoRegister;

    private AuthRepository authRepo;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepo      = new AuthRepository(this);
        session       = new SessionManager(this);

        tilEmail      = findViewById(R.id.tilEmail);
        tilPassword   = findViewById(R.id.tilPassword);
        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        btnLogin      = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RoleSelectActivity.class)));

        TextView tvForgot = findViewById(R.id.tvForgotPassword);
        if (tvForgot != null) {
            tvForgot.setOnClickListener(v ->
                    Toast.makeText(this,
                            "Enter your email above then contact support for a reset link.",
                            Toast.LENGTH_LONG).show());
        }
    }

    // ── Attempt login ─────────────────────────────────────────────────────────

    private void attemptLogin() {
        tilEmail.setError(null);
        tilPassword.setError(null);

        String email    = txt(etEmail);
        String password = txt(etPassword);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        authRepo.signIn(email, password, new AuthRepository.Callback<AuthRepository.AuthSession>() {
            @Override
            public void onSuccess(AuthRepository.AuthSession authSession) {
                authRepo.fetchProfile(authSession.userId, authSession.accessToken,
                        new AuthRepository.Callback<JSONObject>() {
                            @Override
                            public void onSuccess(JSONObject profile) {
                                runOnUiThread(() -> {
                                    setLoading(false);

                                    String fullName    = profile.optString("full_name", "User");
                                    String role        = profile.optString("role", "resident");
                                    String municipality = profile.optString("municipality", "");

                                    session.save(authSession.userId, fullName, role,
                                            municipality, authSession.accessToken);

                                    navigateHome(role);
                                    registerFcmToken(authSession.userId);
                                    finish();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                // fetchProfile is fault-tolerant — fall back to defaults
                                runOnUiThread(() -> {
                                    setLoading(false);
                                    session.save(authSession.userId, "User",
                                            "resident", "", authSession.accessToken);
                                    navigateHome("resident");
                                    registerFcmToken(authSession.userId);
                                    finish();
                                });
                            }
                        });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    String msg = friendlyError(message);
                    tilPassword.setError(msg);
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateHome(String role) {
        Intent intent = "official".equals(role)
                ? new Intent(this, OfficialMainActivity.class)
                : new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // ── FCM token ─────────────────────────────────────────────────────────────

    private void registerFcmToken(String userId) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.isEmpty()) return;
            getSharedPreferences("alertup_fcm", MODE_PRIVATE)
                    .edit().putString("fcm_token", token).apply();
            new Thread(() -> {
                try {
                    com.example.alertuppp.network.SupabaseClient client =
                            new com.example.alertuppp.network.SupabaseClient(getApplicationContext());
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("user_id", userId);
                    body.put("fcm_token", token);
                    client.post("device_tokens", body.toString());
                } catch (Exception e) {
                    android.util.Log.w("FCM", "Token save failed: " + e.getMessage());
                }
            }).start();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnGoRegister.setEnabled(!loading);
        btnLogin.setText(loading ? "Signing in…" : getString(R.string.login));
    }

    private String txt(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String friendlyError(String raw) {
        if (raw == null) return "Login failed. Please try again.";
        String lower = raw.toLowerCase();
        if (lower.contains("email not confirmed"))
            return "Please verify your email first. Check your inbox.";
        if (lower.contains("invalid login") || lower.contains("invalid credentials")
                || lower.contains("wrong password"))
            return "Incorrect email or password.";
        if (lower.contains("network") || lower.contains("timeout") || lower.contains("connect"))
            return "Network error. Check your connection.";
        return "Login failed: " + raw;
    }
}
