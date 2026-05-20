package com.example.alertuppp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.CenterAdapter;
import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.HouseholdProfile;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.FamilyRepository;
import com.example.alertuppp.network.HouseholdRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CentersFragment extends Fragment {

    private CenterAdapter adapter;
    private CenterRepository repo;
    private HouseholdRepository householdRepo;
    private FamilyRepository familyRepo;
    private SessionManager session;
    private List<EvacuationCenter> allCenters = new ArrayList<>();
    private String activeFilter = "all";

    private RecyclerView rvCenters;
    private LinearLayout bannerCapacity;
    private TextInputEditText etSearch;

    // ── Add-center dialog state ───────────────────────────────────────────────
    // Held here so the map-picker result can update them after returning
    private double pickedLat = 0, pickedLng = 0;
    private TextView tvPickedLocation;   // label inside the dialog
    private AlertDialog addCenterDialog; // kept open while map picker runs

    private static final String[] MUNICIPALITIES = {
            "Capalonga", "Daet", "Jose Panganiban", "Labo", "Mercedes",
            "Paracale", "San Lorenzo Ruiz", "San Vicente", "Santa Elena",
            "Talisay", "Vinzons"
    };

    // ── Map picker result launcher ────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK
                                && result.getData() != null) {
                            pickedLat = result.getData().getDoubleExtra(
                                    MapPickerActivity.EXTRA_LAT, 0);
                            pickedLng = result.getData().getDoubleExtra(
                                    MapPickerActivity.EXTRA_LNG, 0);

                            // Update the label inside the still-open dialog
                            if (tvPickedLocation != null) {
                                tvPickedLocation.setText(String.format(Locale.US,
                                        "✅  %.5f°N,  %.5f°E", pickedLat, pickedLng));
                                tvPickedLocation.setTextColor(0xFF388E3C);
                            }
                        }
                    });

    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_centers_list, container, false);

        repo    = new CenterRepository(requireContext());
        householdRepo = new HouseholdRepository(requireContext());
        familyRepo = new FamilyRepository(requireContext());
        session = new SessionManager(requireContext());

        rvCenters      = view.findViewById(R.id.rvCenters);
        bannerCapacity = view.findViewById(R.id.bannerCapacityAlert);
        etSearch       = view.findViewById(R.id.etSearch);

        setupAdapter();
        setupSearch();
        setupFilterChips(view);
        loadCenters();

        // FAB: officials → Add Center, residents → Suggest
        view.findViewById(R.id.fabSuggest).setOnClickListener(v -> {
            if (session.isOfficial()) showAddCenterDialog();
            else showSuggestDialog();
        });

        TextView tvSubtitle = view.findViewById(R.id.tvCentersSubtitle);
        if (session.isOfficial()) tvSubtitle.setText("Tap + to add a new center");

        return view;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private void setupAdapter() {
        adapter = new CenterAdapter(new CenterAdapter.OnCenterActionListener() {
            @Override public void onDirections(EvacuationCenter c) { openMaps(c); }
            @Override public void onCheckIn(EvacuationCenter c)    { confirmCheckIn(c); }
            @Override public void onViewFamilies(EvacuationCenter c) {
                Toast.makeText(requireContext(),
                        c.getName() + " — families list coming soon",
                        Toast.LENGTH_SHORT).show();
            }
        });
        rvCenters.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCenters.setAdapter(adapter);
    }

    // ── Search & filter ───────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips(View view) {
        int[] chipIds = {R.id.chipAll, R.id.chipAvailable, R.id.chipFull, R.id.chipNearby};
        String[] filters = {"all", "available", "full", "nearby"};
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
                applyFilter();
            });
        }
    }

    private void applyFilter() {
        String query = etSearch.getText() != null
                ? etSearch.getText().toString().toLowerCase().trim() : "";
        List<EvacuationCenter> filtered = new ArrayList<>();
        for (EvacuationCenter c : allCenters) {
            boolean matchSearch = query.isEmpty()
                    || c.getName().toLowerCase().contains(query)
                    || c.getMunicipality().toLowerCase().contains(query);
            boolean matchFilter;
            switch (activeFilter) {
                case "available": matchFilter = !c.isFull(); break;
                case "full":      matchFilter = c.isFull();  break;
                default:          matchFilter = true;        break;
            }
            if (matchSearch && matchFilter) filtered.add(c);
        }
        adapter.setData(filtered);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadCenters() {
        repo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override public void onSuccess(List<EvacuationCenter> centers) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allCenters = centers;
                    applyFilter();
                    checkCapacityAlerts();
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Could not load centers: " + message,
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkCapacityAlerts() {
        for (EvacuationCenter c : allCenters) {
            if (c.isFull()) { bannerCapacity.setVisibility(View.VISIBLE); return; }
        }
        bannerCapacity.setVisibility(View.GONE);
    }

    // ── Add Center dialog (officials) ─────────────────────────────────────────

    private void showAddCenterDialog() {
        // Reset picked location for each new dialog
        pickedLat = 0;
        pickedLng = 0;

        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_center, null);

        TextInputLayout tilName = dv.findViewById(R.id.tilCenterName);
        TextInputLayout tilAddr = dv.findViewById(R.id.tilCenterAddress);
        TextInputLayout tilMuni = dv.findViewById(R.id.tilCenterMunicipality);
        TextInputLayout tilCap  = dv.findViewById(R.id.tilCenterCapacity);

        TextInputEditText etName    = dv.findViewById(R.id.etCenterName);
        TextInputEditText etAddr    = dv.findViewById(R.id.etCenterAddress);
        AutoCompleteTextView spMuni = dv.findViewById(R.id.spinnerCenterMunicipality);
        TextInputEditText etCap     = dv.findViewById(R.id.etCenterCapacity);

        // Location label — updated when map picker returns
        tvPickedLocation = dv.findViewById(R.id.tvPickedLocation);

        // "Pick on Map" button — launches MapPickerActivity
        MaterialButton btnPickMap = dv.findViewById(R.id.btnPickOnMap);
        btnPickMap.setOnClickListener(v -> {
            // Keep dialog open; map picker returns via ActivityResultLauncher
            Intent intent = new Intent(requireContext(), MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        spMuni.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, MUNICIPALITIES));

        addCenterDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Add Evacuation Center")
                .setView(dv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> addCenterDialog = null)
                .create();

        addCenterDialog.setOnShowListener(d -> {
            addCenterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name    = txt(etName);
                String addr    = txt(etAddr);
                String muni    = spMuni.getText().toString().trim();
                String capStr  = txt(etCap);

                tilName.setError(null);
                tilAddr.setError(null);
                tilMuni.setError(null);
                tilCap.setError(null);

                if (TextUtils.isEmpty(name))  { tilName.setError("Name is required");         return; }
                if (TextUtils.isEmpty(addr))  { tilAddr.setError("Address is required");       return; }
                if (TextUtils.isEmpty(muni))  { tilMuni.setError("Municipality is required");  return; }
                if (TextUtils.isEmpty(capStr)){ tilCap.setError("Capacity is required");       return; }

                int capacity;
                try { capacity = Integer.parseInt(capStr); }
                catch (NumberFormatException e) { tilCap.setError("Enter a valid number"); return; }
                if (capacity <= 0) { tilCap.setError("Must be greater than 0"); return; }

                if (pickedLat == 0 && pickedLng == 0) {
                    Toast.makeText(requireContext(),
                            "Please pick a location on the map first",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                EvacuationCenter center = new EvacuationCenter();
                center.setName(name);
                center.setAddress(addr);
                center.setMunicipality(muni);
                center.setMaxCapacity(capacity);
                center.setLatitude(pickedLat);
                center.setLongitude(pickedLng);

                addCenterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                addCenterDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Saving…");

                repo.addCenter(center, session.getUserId(),
                        new CenterRepository.Callback<EvacuationCenter>() {
                            @Override public void onSuccess(EvacuationCenter saved) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    addCenterDialog.dismiss();
                                    addCenterDialog = null;
                                    Toast.makeText(requireContext(),
                                            "Center added: " + saved.getName(),
                                            Toast.LENGTH_LONG).show();
                                    loadCenters();
                                });
                            }
                            @Override public void onError(String message) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    addCenterDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setEnabled(true);
                                    addCenterDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setText("Save");
                                    Toast.makeText(requireContext(),
                                            "Failed: " + message, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
            });
        });

        addCenterDialog.show();
    }

    // ── Suggest Center (residents) ────────────────────────────────────────────

    private void showSuggestDialog() {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_suggest_center, null);
        new AlertDialog.Builder(requireContext())
                .setTitle("Suggest Evacuation Point")
                .setView(dv)
                .setPositiveButton("Submit", (d, w) ->
                        Toast.makeText(requireContext(),
                                "Suggestion submitted for review", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Maps & Check-in ───────────────────────────────────────────────────────

    private void openMaps(EvacuationCenter center) {
        navigateTo(MapFragment.newInstance(center.getId(), true));
    }

    private void confirmCheckIn(EvacuationCenter center) {
        if (center.isFull()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Center Full")
                    .setMessage(center.getName() + " has reached maximum capacity.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        // 1. Load Household Profile to get ID
        householdRepo.loadProfile(session.getUserId(), new HouseholdRepository.Callback<HouseholdProfile>() {
            @Override
            public void onSuccess(HouseholdProfile profile) {
                if (profile == null) {
                    requireActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "Please complete your household profile first", Toast.LENGTH_LONG).show());
                    return;
                }

                // 2. Load Families to count members
                familyRepo.loadFamilies(profile.getId(), new FamilyRepository.Callback<List<Family>>() {
                    @Override
                    public void onSuccess(List<Family> families) {
                        int totalMembers = 0;
                        boolean alreadyHere = false;
                        for (Family f : families) {
                            totalMembers += f.getMemberCount();
                            if (center.getId().equals(f.getCenterId())) alreadyHere = true;
                        }

                        if (alreadyHere) {
                            requireActivity().runOnUiThread(() -> 
                                Toast.makeText(requireContext(), "You are already checked in to " + center.getName(), Toast.LENGTH_SHORT).show());
                            return;
                        }
                        
                        if (totalMembers == 0) totalMembers = 1; // Default to 1 if no families added yet
                        
                        final int finalCount = totalMembers;
                        requireActivity().runOnUiThread(() -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Check In")
                                    .setMessage("Check " + finalCount + " members into " + center.getName() + "?")
                                    .setPositiveButton("Check In", (d, w) -> performCheckIn(center, finalCount))
                                    .setNegativeButton("Cancel", null).show();
                        });
                    }
                    @Override public void onError(String msg) { handleError(msg); }
                });
            }
            @Override public void onError(String msg) { handleError(msg); }
        });
    }

    private void performCheckIn(EvacuationCenter center, int memberCount) {
        repo.checkInHousehold(center.getId(), session.getUserId(), memberCount,
                new CenterRepository.Callback<String>() {
                    @Override public void onSuccess(String result) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            String msg;
                            if ("ALREADY_HERE".equals(result)) {
                                msg = "You are already checked in to " + center.getName();
                            } else if ("TRANSFERRED".equals(result)) {
                                msg = "Successfully transferred to " + center.getName();
                                loadCenters(); // Refresh all to update occupancy of both old and new
                            } else {
                                msg = "Successfully checked in " + memberCount + " members!";
                                loadCenters();
                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        });
                    }
                    @Override public void onError(String msg) { handleError(msg); }
                });
    }

    private void handleError(String message) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), "Error: " + message, Toast.LENGTH_SHORT).show());
    }

    private void navigateTo(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private String txt(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }
}
