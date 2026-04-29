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

import com.example.alertuppp.adapter.AlertAdapter;
import com.example.alertuppp.model.Alert;
import com.example.alertuppp.network.AlertRepository;

import java.util.ArrayList;
import java.util.List;

public class AlertsFragment extends Fragment {

    private AlertAdapter adapter;
    private AlertRepository repo;
    private List<Alert> allAlerts = new ArrayList<>();
    private String activeTab = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alerts, container, false);

        repo = new AlertRepository(requireContext());

        RecyclerView rv = view.findViewById(R.id.rvAlerts);
        adapter = new AlertAdapter(false); // residents don't see official actions
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        setupTabs(view);
        loadAlerts();

        return view;
    }

    private void setupTabs(View view) {
        int[] tabIds = {R.id.tabAll, R.id.tabDanger, R.id.tabWarning, R.id.tabInfo};
        String[] levels = {"all", "danger", "warning", "info"};

        for (int i = 0; i < tabIds.length; i++) {
            final String level = levels[i];
            view.findViewById(tabIds[i]).setOnClickListener(v -> {
                activeTab = level;
                // Reset all tab styles
                for (int id : tabIds) {
                    TextView tv = view.findViewById(id);
                    tv.setBackgroundResource(0);
                    tv.setTextColor(0xFF757575);
                }
                // Highlight selected
                TextView selected = view.findViewById(tabIds[indexOf(levels, level)]);
                selected.setBackgroundResource(R.drawable.bg_primary_gradient);
                selected.setTextColor(0xFFFFFFFF);
                applyFilter();
            });
        }
    }

    private void loadAlerts() {
        repo.loadActive(new AlertRepository.Callback<List<Alert>>() {
            @Override
            public void onSuccess(List<Alert> alerts) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allAlerts = alerts != null ? alerts : new ArrayList<>();
                    applyFilter();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Could not load alerts: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void applyFilter() {
        if ("all".equals(activeTab)) {
            adapter.setData(allAlerts);
            return;
        }
        List<Alert> filtered = new ArrayList<>();
        for (Alert a : allAlerts) {
            if (activeTab.equals(a.getLevel())) filtered.add(a);
        }
        adapter.setData(filtered);
    }

    private int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return 0;
    }
}
