package com.example.alertuppp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.alertuppp.data.CamNorteLocations;
import com.example.alertuppp.model.HouseholdProfile;
import com.example.alertuppp.network.HouseholdRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class HouseholdProfileFragment extends Fragment {

    private SessionManager session;
    private HouseholdProfile profile;
    private HouseholdRepository repository;

    private TextInputEditText etHouseholdName, etAddress;
    private AutoCompleteTextView spinnerMunicipality, spinnerBarangay, spinnerHouseType;
    private TextInputLayout tilBarangay;
    private CheckBox cbFloodZone, cbLandslideZone;
    private MaterialButton btnSaveProfile;
    private ProgressBar progressBar;

    private static final String[] HOUSE_TYPES = {
            "Concrete", "Wood", "Mixed (Concrete & Wood)", "Makeshift / Light Materials"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_household_profile, container, false);

        repository = new HouseholdRepository(requireContext());
        session    = new SessionManager(requireContext());
        profile    = new HouseholdProfile();

        etHouseholdName     = view.findViewById(R.id.etHouseholdName);
        etAddress           = view.findViewById(R.id.etAddress);
        spinnerMunicipality = view.findViewById(R.id.spinnerMunicipality);
        spinnerBarangay     = view.findViewById(R.id.spinnerBarangay);
        tilBarangay         = view.findViewById(R.id.tilBarangay);
        spinnerHouseType    = view.findViewById(R.id.spinnerHouseType);
        cbFloodZone         = view.findViewById(R.id.cbFloodZone);
        cbLandslideZone     = view.findViewById(R.id.cbLandslideZone);
        btnSaveProfile      = view.findViewById(R.id.btnSaveProfile);
        progressBar         = view.findViewById(R.id.progressBar);

        setupDropdowns();
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        loadExistingProfile();
        return view;
    }

    // ── Dropdowns ─────────────────────────────────────────────────────────────

    private void setupDropdowns() {
        // Municipality
        spinnerMunicipality.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, CamNorteLocations.MUNICIPALITIES));

        // When municipality changes, repopulate barangay list
        spinnerMunicipality.setOnItemClickListener((parent, v, pos, id) -> {
            String muni = CamNorteLocations.MUNICIPALITIES[pos];
            populateBarangays(muni);
            spinnerBarangay.setText("", false); // clear previous selection
        });

        // House type
        spinnerHouseType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, HOUSE_TYPES));

        // Barangay starts disabled until municipality is chosen
        tilBarangay.setEnabled(false);
    }

    private void populateBarangays(String municipality) {
        List<String> brgy = CamNorteLocations.getBarangays(municipality);
        spinnerBarangay.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, brgy));
        tilBarangay.setEnabled(true);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadExistingProfile() {
        setLoading(true);
        repository.loadProfile(session.getUserId(), new HouseholdRepository.Callback<HouseholdProfile>() {
            @Override public void onSuccess(HouseholdProfile result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    if (result != null) populateForm(result);
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            "Could not load profile: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void populateForm(HouseholdProfile p) {
        profile = p;
        etHouseholdName.setText(p.getHouseholdName());
        etAddress.setText(p.getAddress());
        spinnerHouseType.setText(p.getHouseType(), false);
        cbFloodZone.setChecked(p.isNearFloodZone());
        cbLandslideZone.setChecked(p.isNearLandslideZone());

        // Set municipality first, then populate and set barangay
        String muni = p.getMunicipality();
        if (muni != null && !muni.isEmpty()) {
            spinnerMunicipality.setText(muni, false);
            populateBarangays(muni);
        }
        String brgy = p.getBarangay();
        if (brgy != null && !brgy.isEmpty()) {
            spinnerBarangay.setText(brgy, false);
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveProfile() {
        String name      = txt(etHouseholdName);
        String address   = txt(etAddress);
        String muni      = spinnerMunicipality.getText().toString().trim();
        String barangay  = spinnerBarangay.getText().toString().trim();
        String houseType = spinnerHouseType.getText().toString().trim();

        if (TextUtils.isEmpty(name))    { etHouseholdName.setError("Required"); return; }
        if (TextUtils.isEmpty(address)) { etAddress.setError("Required"); return; }
        if (TextUtils.isEmpty(muni))    {
            Toast.makeText(requireContext(), "Please select a municipality", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(barangay)) {
            Toast.makeText(requireContext(), "Please select a barangay", Toast.LENGTH_SHORT).show();
            return;
        }

        profile.setHouseholdName(name);
        profile.setAddress(address);
        profile.setMunicipality(muni);
        profile.setBarangay(barangay);
        profile.setHouseType(TextUtils.isEmpty(houseType) ? "Concrete" : houseType);
        profile.setNearFloodZone(cbFloodZone.isChecked());
        profile.setNearLandslideZone(cbLandslideZone.isChecked());

        setLoading(true);
        repository.saveProfile(profile, session.getUserId(),
                new HouseholdRepository.Callback<HouseholdProfile>() {
                    @Override public void onSuccess(HouseholdProfile saved) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            setLoading(false);
                            profile = saved;
                            Toast.makeText(requireContext(), "Profile saved!", Toast.LENGTH_SHORT).show();
                            requireActivity().getSupportFragmentManager().popBackStack();
                        });
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    "Save failed: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSaveProfile != null) btnSaveProfile.setEnabled(!loading);
    }

    private String txt(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }
}
