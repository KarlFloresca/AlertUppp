package com.example.alertuppp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.ReportAdapter;
import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.AlertRepository;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.ReportRepository;

import java.util.List;

public class OfficialDashboardFragment extends Fragment {

    private CenterRepository centerRepo;
    private ReportRepository reportRepo;
    private AlertRepository alertRepo;

    private TextView tvTotalEvacuees, tvActiveCenters, tvPendingReports, tvActiveAlerts;
    private ReportAdapter reportAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_official_dashboard, container, false);

        centerRepo = new CenterRepository(requireContext());
        reportRepo = new ReportRepository(requireContext());
        alertRepo  = new AlertRepository(requireContext());

        tvTotalEvacuees  = view.findViewById(R.id.tvTotalEvacuees);
        tvActiveCenters  = view.findViewById(R.id.tvActiveCenters);
        tvPendingReports = view.findViewById(R.id.tvPendingReports);
        tvActiveAlerts   = view.findViewById(R.id.tvActiveAlerts);

        setupQuickActions(view);
        loadDashboardData();

        return view;
    }

    private void setupQuickActions(View view) {
        view.findViewById(R.id.btnMassAlert).setOnClickListener(v ->
                navigateTo(new OfficialAlertsFragment()));

        view.findViewById(R.id.btnManageCenters).setOnClickListener(v ->
                navigateTo(new OfficialCentersFragment()));

        view.findViewById(R.id.btnViewReports).setOnClickListener(v ->
                navigateTo(new OfficialReportsFragment()));

        view.findViewById(R.id.btnPostAlert).setOnClickListener(v ->
                navigateTo(new OfficialAlertsFragment()));

        view.findViewById(R.id.btnAnalytics).setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Analytics dashboard — coming soon", Toast.LENGTH_SHORT).show());
    }

    private void loadDashboardData() {
        // Load centers → compute total evacuees + active count
        centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override
            public void onSuccess(List<EvacuationCenter> centers) {
                if (!isAdded()) return;
                int totalEvacuees = 0;
                int activeCount   = 0;
                for (EvacuationCenter c : centers) {
                    totalEvacuees += c.getCurrentOccupancy();
                    if (!"closed".equals(c.getStatus())) activeCount++;
                }
                final int evacuees = totalEvacuees;
                final int active   = activeCount;
                requireActivity().runOnUiThread(() -> {
                    tvTotalEvacuees.setText(String.valueOf(evacuees));
                    tvActiveCenters.setText(String.valueOf(active));
                });
            }
            @Override public void onError(String message) { /* silent */ }
        });

        // Load pending reports count
        reportRepo.loadAll("all", new ReportRepository.Callback<List<IncidentReport>>() {
            @Override
            public void onSuccess(List<IncidentReport> reports) {
                if (!isAdded()) return;
                long pending = 0;
                for (IncidentReport r : reports) if ("pending".equals(r.getStatus())) pending++;
                final long p = pending;
                requireActivity().runOnUiThread(() ->
                        tvPendingReports.setText(String.valueOf(p)));
            }
            @Override public void onError(String message) { /* silent */ }
        });

        // Load active alerts count
        alertRepo.loadActive(new AlertRepository.Callback<List<com.example.alertuppp.model.Alert>>() {
            @Override
            public void onSuccess(List<com.example.alertuppp.model.Alert> alerts) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        tvActiveAlerts.setText(String.valueOf(alerts.size())));
            }
            @Override public void onError(String message) { /* silent */ }
        });
    }

    private void navigateTo(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
}
