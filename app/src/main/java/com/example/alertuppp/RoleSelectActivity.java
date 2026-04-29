package com.example.alertuppp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shown before registration so the user picks Resident or Official.
 * Passes the chosen role to RegisterActivity.
 */
public class RoleSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_select);

        findViewById(R.id.cardResident).setOnClickListener(v ->
                openRegister("resident"));

        findViewById(R.id.cardOfficial).setOnClickListener(v ->
                openRegister("official"));
    }

    private void openRegister(String role) {
        Intent intent = new Intent(this, RegisterActivity.class);
        intent.putExtra("role", role);
        startActivity(intent);
    }
}
