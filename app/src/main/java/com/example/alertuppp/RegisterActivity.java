package com.example.alertuppp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.alertuppp.network.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextInputLayout tilFullName, tilEmail, tilPhone, tilPassword, tilConfirm;
    private TextInputEditText etFullName, etEmail, etPhone, etPassword, etConfirm;
    private TextInputLayout tilMunicipality;
    private AutoCompleteTextView spinnerMunicipality;
    private MaterialButton btnRegister;

    // Selected role — carried from RoleSelectActivity or defaulted to "resident"
    private String selectedRole = "resident";

    private AuthRepository authRepo;

    private static final String[] MUNICIPALITIES = {
            "Capalonga", "Daet", "Jose Panganiban", "Labo", "Mercedes",
            "Paracale", "San Lorenzo Ruiz", "San Vicente", "Santa Elena",
            "Talisay", "Vinzons"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authRepo = new AuthRepository(this);

        // Role may be passed from RoleSelectActivity
        selectedRole = getIntent().getStringExtra("role") != null
                ? getIntent().getStringExtra("role") : "resident";

        bindViews();
        setupMunicipalityDropdown();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void bindViews() {
        tilFullName  = findViewById(R.id.tilFullName);
        tilEmail     = findViewById(R.id.tilEmail);
        tilPhone     = findViewById(R.id.tilPhone);
        tilMunicipality = findViewById(R.id.tilMunicipality);
        tilPassword  = findViewById(R.id.tilPassword);
        tilConfirm   = findViewById(R.id.tilConfirmPassword);

        etFullName   = findViewById(R.id.etFullName);
        etEmail      = findViewById(R.id.etEmail);
        etPhone      = findViewById(R.id.etPhone);
        spinnerMunicipality = findViewById(R.id.spinnerMunicipality);
        etPassword   = findViewById(R.id.etPassword);
        etConfirm    = findViewById(R.id.etConfirmPassword);

        btnRegister  = findViewById(R.id.btnRegister);
    }

    private void setupMunicipalityDropdown() {
        spinnerMunicipality.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, MUNICIPALITIES));
    }

    private void attemptRegister() {
        // Clear errors
        tilFullName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
        if (tilConfirm != null) tilConfirm.setError(null);

        String fullName     = text(etFullName);
        String email        = text(etEmail);
        String phone        = text(etPhone);
        String municipality = spinnerMunicipality.getText().toString().trim();
        String password     = text(etPassword);
        String confirm      = etConfirm != null ? text(etConfirm) : password;

        // Validate
        if (TextUtils.isEmpty(fullName)) {
            tilFullName.setError("Full name is required");
            etFullName.requestFocus(); return;
        }
        if (TextUtils.isEmpty(email)
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address");
            etEmail.requestFocus(); return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus(); return;
        }
        if (!password.equals(confirm)) {
            if (tilConfirm != null) tilConfirm.setError("Passwords do not match");
            return;
        }

        setLoading(true);

        authRepo.signUp(email, password, fullName, phone, municipality, selectedRole,
                new AuthRepository.Callback<AuthRepository.AuthSession>() {
                    @Override
                    public void onSuccess(AuthRepository.AuthSession authSession) {
                        runOnUiThread(() -> {
                            setLoading(false);

                            // Do NOT save a session here — the user must confirm
                            // their email first. Role is already stored in the
                            // profiles table; it will be read on first sign-in.

                            Intent intent = new Intent(RegisterActivity.this,
                                    VerifyEmailActivity.class);
                            intent.putExtra("email", email);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            // Show the exact error from Supabase so nothing is hidden
                            android.util.Log.e("RegisterActivity", "Sign-up error: " + message);
                            Toast.makeText(RegisterActivity.this,
                                    message, Toast.LENGTH_LONG).show();
                            // Also highlight the relevant field if we can tell
                            if (message != null) {
                                String lower = message.toLowerCase();
                                if (lower.contains("email")) {
                                    tilEmail.setError(message);
                                    etEmail.requestFocus();
                                } else if (lower.contains("password")) {
                                    tilPassword.setError(message);
                                    etPassword.requestFocus();
                                }
                            }
                        });
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "Creating account…" : getString(R.string.register));
    }

    private String text(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }
}
