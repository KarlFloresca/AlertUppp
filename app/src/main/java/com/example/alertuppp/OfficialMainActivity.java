package com.example.alertuppp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.ReportRepository;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class OfficialMainActivity extends AppCompatActivity {

    private ReportRepository reportRepo;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_official_main);

        bottomNav = findViewById(R.id.bottomNav);
        reportRepo = new ReportRepository(this);

        if (savedInstanceState == null) {
            loadFragment(new OfficialDashboardFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();

            if (id == R.id.nav_dashboard) {
                fragment = new OfficialDashboardFragment();
            } else if (id == R.id.nav_centers) {
                fragment = new OfficialCentersFragment();
            } else if (id == R.id.nav_reports) {
                fragment = new OfficialReportsFragment();
            } else if (id == R.id.nav_alerts) {
                fragment = new OfficialAlertsFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                return false;
            }

            loadFragment(fragment);
            return true;
        });

        refreshPendingBadge();
    }

    public void refreshPendingBadge() {
        reportRepo.loadAll("all", "pending", new ReportRepository.Callback<List<IncidentReport>>() {
            @Override
            public void onSuccess(List<IncidentReport> reports) {
                int count = reports.size();
                runOnUiThread(() -> {
                    BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_reports);
                    if (count > 0) {
                        badge.setVisible(true);
                        badge.setNumber(count);
                        badge.setBackgroundColor(getColor(R.color.danger));
                        badge.setBadgeTextColor(getColor(R.color.white));
                    } else {
                        badge.setVisible(false);
                    }
                });
            }
            @Override public void onError(String message) {}
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
