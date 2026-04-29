package com.example.alertuppp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.ReportRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ReportFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 101;

    private String selectedType = "flood";
    private double currentLat = 0, currentLng = 0;

    private TextView tvCurrentLocation;
    private ProgressBar progressBar;
    private MaterialButton btnSubmit;
    private ReportRepository repo;
    private SessionManager session;

    // Type button views
    private View lastSelectedType;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        repo    = new ReportRepository(requireContext());
        session = new SessionManager(requireContext());

        tvCurrentLocation = view.findViewById(R.id.tvCurrentLocation);
        btnSubmit         = view.findViewById(R.id.btnSubmitReport);

        setupTypeSelection(view);
        setupFloodLevelDropdown(view);
        fetchLocation(view);

        view.findViewById(R.id.btnRefreshLocation).setOnClickListener(v -> fetchLocation(view));
        view.findViewById(R.id.btnAttachPhoto).setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Camera/gallery integration — add ActivityResultLauncher here",
                        Toast.LENGTH_SHORT).show());

        btnSubmit.setOnClickListener(v -> submitReport(view));

        return view;
    }

    // ── Type selection ────────────────────────────────────────────────────────

    private void setupTypeSelection(View view) {
        int[] typeViewIds = {
                R.id.typeFlood, R.id.typeRoad, R.id.typeDamage, R.id.typeUnsafe,
                R.id.typeMissing, R.id.typeRescue, R.id.typeMedical, R.id.typeSupply
        };
        String[] typeNames = {
                "flood", "road", "damage", "unsafe",
                "missing", "rescue", "medical", "supply"
        };

        // Select flood by default
        lastSelectedType = view.findViewById(R.id.typeFlood);
        highlightType(lastSelectedType);

        for (int i = 0; i < typeViewIds.length; i++) {
            final String type = typeNames[i];
            View btn = view.findViewById(typeViewIds[i]);
            btn.setOnClickListener(v -> {
                selectedType = type;
                if (lastSelectedType != null) lastSelectedType.setAlpha(1f);
                v.setAlpha(0.6f);
                lastSelectedType = v;
                view.findViewById(R.id.layoutFloodLevel).setVisibility(
                        "flood".equals(type) ? View.VISIBLE : View.GONE);
            });
        }
    }

    private void highlightType(View v) {
        if (v != null) v.setAlpha(0.6f);
    }

    // ── Flood level dropdown ──────────────────────────────────────────────────

    private void setupFloodLevelDropdown(View view) {
        AutoCompleteTextView spinner = view.findViewById(R.id.spinnerFloodLevel);
        String[] levels = {"Ankle-level", "Knee-level", "Waist-level", "Chest-level", "Above head"};
        spinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, levels));
    }

    // ── GPS location ──────────────────────────────────────────────────────────

    private void fetchLocation(View view) {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            tvCurrentLocation.setText("📍 Location permission required");
            return;
        }

        LocationManager lm = (LocationManager) requireContext()
                .getSystemService(android.content.Context.LOCATION_SERVICE);
        Location loc = null;
        if (lm != null) {
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (loc != null) {
            currentLat = loc.getLatitude();
            currentLng = loc.getLongitude();
            tvCurrentLocation.setText(String.format("📍 %.5f, %.5f", currentLat, currentLng));
        } else {
            tvCurrentLocation.setText("📍 Could not get location — enter landmark manually");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation(requireView());
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private void submitReport(View view) {
        TextInputEditText etTitle    = view.findViewById(R.id.etReportTitle);
        TextInputEditText etDesc     = view.findViewById(R.id.etReportDescription);
        TextInputEditText etLandmark = view.findViewById(R.id.etLandmark);
        AutoCompleteTextView spFlood = view.findViewById(R.id.spinnerFloodLevel);

        String title    = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String desc     = etDesc.getText()  != null ? etDesc.getText().toString().trim()  : "";
        String landmark = etLandmark.getText() != null ? etLandmark.getText().toString().trim() : "";
        String flood    = spFlood.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title is required");
            etTitle.requestFocus();
            return;
        }

        IncidentReport report = new IncidentReport();
        report.setReportType(selectedType);
        report.setTitle(title);
        report.setDescription(desc);
        report.setLatitude(currentLat);
        report.setLongitude(currentLng);
        report.setLandmark(landmark);
        if ("flood".equals(selectedType) && !flood.isEmpty()) report.setFloodLevel(flood);

        setLoading(true);
        repo.submit(report, session.getUserId(), new ReportRepository.Callback<IncidentReport>() {
            @Override
            public void onSuccess(IncidentReport result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            "Report submitted successfully!", Toast.LENGTH_LONG).show();
                    clearForm(view);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            "Submit failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void clearForm(View view) {
        TextInputEditText etTitle    = view.findViewById(R.id.etReportTitle);
        TextInputEditText etDesc     = view.findViewById(R.id.etReportDescription);
        TextInputEditText etLandmark = view.findViewById(R.id.etLandmark);
        etTitle.setText("");
        etDesc.setText("");
        etLandmark.setText("");
        selectedType = "flood";
    }

    private void setLoading(boolean loading) {
        if (btnSubmit != null) btnSubmit.setEnabled(!loading);
        if (btnSubmit != null) btnSubmit.setText(loading ? "Submitting…" : "Submit Report");
    }
}
