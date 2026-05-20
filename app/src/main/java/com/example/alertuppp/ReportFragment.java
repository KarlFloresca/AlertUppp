package com.example.alertuppp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import androidx.core.content.FileProvider;

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
    private android.widget.ImageView ivPhotoPreview;
    private com.google.android.material.button.MaterialButton btnRemovePhoto;
    private ReportRepository repo;
    private SessionManager session;

    // Type button views
    private View lastSelectedType;

    private Uri selectedPhotoUri = null;
    private Uri currentPhotoUri = null;
    private ActivityResultLauncher<Uri> takePicture;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                selectedPhotoUri = currentPhotoUri;
                if (ivPhotoPreview != null) {
                    ivPhotoPreview.setImageURI(selectedPhotoUri);
                    ivPhotoPreview.setVisibility(View.VISIBLE);
                }
                if (btnRemovePhoto != null) btnRemovePhoto.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), "Photo captured", Toast.LENGTH_SHORT).show();
            } else {
                currentPhotoUri = null;
            }
        });
    }

    private Uri createImageFileUri() {
        File imagePath = new File(requireContext().getCacheDir(), "camera_images");
        if (!imagePath.exists()) imagePath.mkdirs();
        File newFile = new File(imagePath, "report_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(requireContext(), 
                requireContext().getPackageName() + ".fileprovider", newFile);
    }

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
        ivPhotoPreview    = view.findViewById(R.id.ivPhotoPreview);
        btnRemovePhoto    = view.findViewById(R.id.btnRemovePhoto);

        if (btnRemovePhoto != null) {
            btnRemovePhoto.setOnClickListener(v -> {
                selectedPhotoUri = null;
                ivPhotoPreview.setVisibility(View.GONE);
                btnRemovePhoto.setVisibility(View.GONE);
            });
        }

        setupTypeSelection(view);
        setupFloodLevelDropdown(view);
        fetchLocation(view);

        view.findViewById(R.id.btnRefreshLocation).setOnClickListener(v -> fetchLocation(view));
        view.findViewById(R.id.btnAttachPhoto).setOnClickListener(v -> {
            currentPhotoUri = createImageFileUri();
            takePicture.launch(currentPhotoUri);
        });

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

        if (selectedPhotoUri != null) {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(selectedPhotoUri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                byte[] photoBytes = baos.toByteArray();
                is.close();

                String mimeType = requireContext().getContentResolver().getType(selectedPhotoUri);
                if (mimeType == null) mimeType = "image/jpeg";

                repo.submitWithPhoto(report, session.getUserId(), photoBytes, mimeType, new ReportRepository.Callback<IncidentReport>() {
                    @Override
                    public void onSuccess(IncidentReport result) {
                        handleSuccess(view, result);
                    }
                    @Override
                    public void onError(String message) {
                        handleError(message);
                    }
                });
            } catch (IOException e) {
                setLoading(false);
                Toast.makeText(requireContext(), "Failed to read photo", Toast.LENGTH_SHORT).show();
            }
        } else {
            repo.submit(report, session.getUserId(), new ReportRepository.Callback<IncidentReport>() {
                @Override
                public void onSuccess(IncidentReport result) {
                    handleSuccess(view, result);
                }
                @Override
                public void onError(String message) {
                    handleError(message);
                }
            });
        }
    }

    private void handleSuccess(View view, IncidentReport result) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            setLoading(false);
            Toast.makeText(requireContext(), "Report submitted successfully!", Toast.LENGTH_SHORT).show();
            clearForm(view);

            if (result != null && result.getId() != null) {
                // Wait 4 seconds for AI to process, then check for duplicates
                new android.os.Handler().postDelayed(() -> {
                    fetchReportStatus(result.getId());
                }, 4000);
            }
        });
    }

    private void fetchReportStatus(String reportId) {
        if (!isAdded()) return;
        repo.getReportById(reportId, new ReportRepository.Callback<IncidentReport>() {
            @Override
            public void onSuccess(IncidentReport updatedReport) {
                if (!isAdded()) return;
                if (updatedReport.isDuplicate()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "AI verified: Your report matches an existing incident. Thank you for the update!",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
            @Override public void onError(String message) {}
        });
    }

    private void handleError(String message) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            setLoading(false);
            Toast.makeText(requireContext(), "Submit failed: " + message, Toast.LENGTH_LONG).show();
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
        selectedPhotoUri = null;
    }

    private void setLoading(boolean loading) {
        if (btnSubmit != null) btnSubmit.setEnabled(!loading);
        if (btnSubmit != null) btnSubmit.setText(loading ? "Submitting…" : "Submit Report");
    }
}
