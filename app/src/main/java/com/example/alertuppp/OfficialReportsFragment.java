package com.example.alertuppp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.ReportAdapter;
import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.ReportRepository;

import java.util.List;

public class OfficialReportsFragment extends Fragment {

    private ReportAdapter adapter;
    private ReportRepository repo;
    private String activeFilter = "all";

    private TextView tvStatPending, tvStatOngoing, tvStatResolved, tvStatTotal;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_official_reports, container, false);

        repo = new ReportRepository(requireContext());

        tvStatPending  = view.findViewById(R.id.tvStatPending);
        tvStatOngoing  = view.findViewById(R.id.tvStatOngoing);
        tvStatResolved = view.findViewById(R.id.tvStatResolved);
        tvStatTotal    = view.findViewById(R.id.tvStatTotal);

        RecyclerView rv = view.findViewById(R.id.rvReports);
        adapter = new ReportAdapter(true); // show official actions
        adapter.setListener(new ReportAdapter.OnReportActionListener() {
            @Override
            public void onVerify(IncidentReport report) {
                updateStatus(report, "verified");
            }

            @Override
            public void onAssign(IncidentReport report) {
                showAssignDialog(report);
            }

            @Override
            public void onResolve(IncidentReport report) {
                confirmResolve(report);
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        setupFilterChips(view);
        loadReports();

        return view;
    }

    private void setupFilterChips(View view) {
        int[] chipIds = {
                R.id.filterAll, R.id.filterFlood, R.id.filterRescue,
                R.id.filterDamage, R.id.filterMedical, R.id.filterMissing
        };
        String[] filters = {"all", "flood", "rescue", "damage", "medical", "missing"};

        for (int i = 0; i < chipIds.length; i++) {
            final String filter = filters[i];
            view.findViewById(chipIds[i]).setOnClickListener(v -> {
                activeFilter = filter;
                for (int id : chipIds) {
                    view.findViewById(id).setBackgroundResource(R.drawable.bg_chip_unselected);
                    ((TextView) view.findViewById(id)).setTextColor(0xFF757575);
                }
                v.setBackgroundResource(R.drawable.bg_chip_selected);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                loadReports();
            });
        }
    }

    private void loadReports() {
        repo.loadAll(activeFilter, new ReportRepository.Callback<List<IncidentReport>>() {
            @Override
            public void onSuccess(List<IncidentReport> reports) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    adapter.setData(reports);
                    updateStats(reports);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Could not load reports: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateStats(List<IncidentReport> reports) {
        int pending = 0, ongoing = 0, resolved = 0;
        for (IncidentReport r : reports) {
            switch (r.getStatus() != null ? r.getStatus() : "pending") {
                case "pending":  pending++;  break;
                case "ongoing":  ongoing++;  break;
                case "resolved": resolved++; break;
            }
        }
        tvStatPending.setText(String.valueOf(pending));
        tvStatOngoing.setText(String.valueOf(ongoing));
        tvStatResolved.setText(String.valueOf(resolved));
        tvStatTotal.setText(String.valueOf(reports.size()));
    }

    private void updateStatus(IncidentReport report, String newStatus) {
        repo.updateStatus(report.getId(), newStatus, new ReportRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void r) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            "Report marked as " + newStatus, Toast.LENGTH_SHORT).show();
                    loadReports();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Update failed: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showAssignDialog(IncidentReport report) {
        String[] teams = {"MDRRMO Team A", "MDRRMO Team B", "BFP", "PNP", "Barangay Tanod"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Assign Response Team")
                .setItems(teams, (d, which) -> {
                    // TODO: update assigned_to field in Supabase
                    Toast.makeText(requireContext(),
                            "Assigned to " + teams[which], Toast.LENGTH_SHORT).show();
                    updateStatus(report, "ongoing");
                })
                .show();
    }

    private void confirmResolve(IncidentReport report) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Mark as Resolved")
                .setMessage("Confirm that \"" + report.getTitle() + "\" has been resolved?")
                .setPositiveButton("Resolve", (d, w) -> updateStatus(report, "resolved"))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
