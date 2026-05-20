package com.example.alertuppp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.AlertAdapter;
import com.example.alertuppp.data.CamNorteLocations;
import com.example.alertuppp.model.Alert;
import com.example.alertuppp.network.AlertRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class OfficialAlertsFragment extends Fragment {

    private AlertRepository repo;
    private AlertAdapter activeAdapter;
    private AlertAdapter historyAdapter;

    // Targeting views
    private CheckBox cbTargetAll, cbTargetBarangay;
    private TextInputLayout tilAlertMunicipality, tilAlertBarangay;
    private AutoCompleteTextView spinnerAlertMunicipality, spinnerAlertBarangay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_official_alerts, container, false);

        repo = new AlertRepository(requireContext());

        // Alert level dropdown
        AutoCompleteTextView spinnerLevel = view.findViewById(R.id.spinnerAlertLevel);
        spinnerLevel.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"🔴 Danger", "🟠 Warning", "🔵 Info"}));

        // Targeting
        cbTargetAll          = view.findViewById(R.id.cbTargetAll);
        cbTargetBarangay     = view.findViewById(R.id.cbTargetBarangay);
        tilAlertMunicipality = view.findViewById(R.id.tilAlertMunicipality);
        tilAlertBarangay     = view.findViewById(R.id.tilAlertBarangay);
        spinnerAlertMunicipality = view.findViewById(R.id.spinnerAlertMunicipality);
        spinnerAlertBarangay     = view.findViewById(R.id.spinnerAlertBarangay);

        setupTargeting();

        // 1. Active alerts list
        RecyclerView rvActive = view.findViewById(R.id.rvActiveAlerts);
        activeAdapter = new AlertAdapter(true); // Official mode
        activeAdapter.setListener(alert -> confirmDeactivate(alert));
        rvActive.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvActive.setAdapter(activeAdapter);

        // 2. Alert History list
        RecyclerView rvHistory = view.findViewById(R.id.rvAlertHistory);
        historyAdapter = new AlertAdapter(false); // Read-only mode
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(historyAdapter);

        view.findViewById(R.id.btnSendAlert).setOnClickListener(v -> sendAlert(view));
        view.findViewById(R.id.fabNewAlert).setOnClickListener(v -> clearForm(view));

        loadAlerts();
        return view;
    }

    // ── Targeting setup ───────────────────────────────────────────────────────

    private void setupTargeting() {
        // Mutual exclusion between the two checkboxes
        cbTargetAll.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                cbTargetBarangay.setChecked(false);
                tilAlertMunicipality.setVisibility(View.GONE);
                tilAlertBarangay.setVisibility(View.GONE);
            }
        });

        cbTargetBarangay.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                cbTargetAll.setChecked(false);
                tilAlertMunicipality.setVisibility(View.VISIBLE);
                tilAlertBarangay.setVisibility(View.VISIBLE);
            } else {
                tilAlertMunicipality.setVisibility(View.GONE);
                tilAlertBarangay.setVisibility(View.GONE);
            }
        });

        // Municipality dropdown
        spinnerAlertMunicipality.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, com.example.alertuppp.data.CamNorteLocations.MUNICIPALITIES));

        // Cascade: municipality → barangay
        spinnerAlertMunicipality.setOnItemClickListener((parent, v, pos, id) -> {
            String muni = com.example.alertuppp.data.CamNorteLocations.MUNICIPALITIES[pos];
            java.util.List<String> brgy = com.example.alertuppp.data.CamNorteLocations.getBarangays(muni);
            // Add "All barangays" as first option
            String[] options = new String[brgy.size() + 1];
            options[0] = "All barangays";
            for (int i = 0; i < brgy.size(); i++) options[i + 1] = brgy.get(i);
            spinnerAlertBarangay.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, options));
            spinnerAlertBarangay.setText("All barangays", false);
            tilAlertBarangay.setEnabled(true);
        });

        tilAlertBarangay.setEnabled(false);
    }

    /** Builds the area string from the targeting selection. */
    private String buildAreaString() {
        if (cbTargetAll.isChecked()) {
            return "Camarines Norte";
        }
        String muni = spinnerAlertMunicipality.getText().toString().trim();
        String brgy = spinnerAlertBarangay.getText().toString().trim();
        if (muni.isEmpty()) return "Camarines Norte";
        if (brgy.isEmpty() || brgy.equals("All barangays")) return muni + ", Camarines Norte";
        return "Brgy. " + brgy + ", " + muni + ", Camarines Norte";
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadAlerts() {
        repo.loadAll(new AlertRepository.Callback<List<Alert>>() {
            @Override public void onSuccess(List<Alert> alerts) {
                if (!isAdded()) return;
                
                java.util.List<Alert> activeList = new java.util.ArrayList<>();
                java.util.List<Alert> historyList = new java.util.ArrayList<>();
                
                for (Alert a : alerts) {
                    if (a.isActive()) activeList.add(a);
                    else historyList.add(a);
                }

                requireActivity().runOnUiThread(() -> {
                    activeAdapter.setData(activeList);
                    historyAdapter.setData(historyList);
                });
            }
            @Override public void onError(String message) { /* silent */ }
        });
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendAlert(View view) {
        TextInputEditText etTitle = view.findViewById(R.id.etAlertTitle);
        TextInputEditText etBody  = view.findViewById(R.id.etAlertBody);
        AutoCompleteTextView spLevel = view.findViewById(R.id.spinnerAlertLevel);

        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String body  = etBody.getText()  != null ? etBody.getText().toString().trim()  : "";
        String level = spLevel.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(body)) {
            etBody.setError("Message is required");
            etBody.requestFocus();
            return;
        }

        // Validate specific location selection
        if (cbTargetBarangay.isChecked()) {
            String muni = spinnerAlertMunicipality.getText().toString().trim();
            if (muni.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Please select a municipality", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String area = buildAreaString();

        view.findViewById(R.id.btnSendAlert).setEnabled(false);

        repo.postAlert(title, body, level, area, new AlertRepository.Callback<Alert>() {
            @Override public void onSuccess(Alert alert) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    view.findViewById(R.id.btnSendAlert).setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Alert sent → " + area, Toast.LENGTH_LONG).show();
                    clearForm(view);
                    loadAlerts();
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    view.findViewById(R.id.btnSendAlert).setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Deactivate ────────────────────────────────────────────────────────────

    private void confirmDeactivate(Alert alert) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Deactivate Alert")
                .setMessage("Deactivate \"" + alert.getTitle() + "\"?")
                .setPositiveButton("Deactivate", (d, w) ->
                        repo.deactivate(alert.getId(), new AlertRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(),
                                            "Alert deactivated", Toast.LENGTH_SHORT).show();
                                    loadAlerts();
                                });
                            }
                            @Override public void onError(String message) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(),
                                                "Failed: " + message, Toast.LENGTH_SHORT).show());
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    private void clearForm(View view) {
        TextInputEditText etTitle = view.findViewById(R.id.etAlertTitle);
        TextInputEditText etBody  = view.findViewById(R.id.etAlertBody);
        if (etTitle != null) etTitle.setText("");
        if (etBody  != null) etBody.setText("");
        cbTargetAll.setChecked(true);
        spinnerAlertMunicipality.setText("", false);
        spinnerAlertBarangay.setText("", false);
        tilAlertMunicipality.setVisibility(View.GONE);
        tilAlertBarangay.setVisibility(View.GONE);
    }
}
