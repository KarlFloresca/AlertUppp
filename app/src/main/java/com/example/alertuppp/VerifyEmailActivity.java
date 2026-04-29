package com.example.alertuppp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class VerifyEmailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        String email = getIntent().getStringExtra("email");
        if (email != null) {
            ((TextView) findViewById(R.id.tvEmail)).setText(email);
        }

        findViewById(R.id.btnReturnToSignIn).setOnClickListener(v -> goToLogin());
    }

    @Override
    public void onBackPressed() {
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
