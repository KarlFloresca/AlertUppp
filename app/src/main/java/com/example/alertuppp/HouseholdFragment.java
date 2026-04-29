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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.data.CamNorteLocations;
import com.example.alertuppp.adapter.FamilyAdapter;
import com.example.alertuppp.adapter.FamilyMemberAdapter;
import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.FamilyMember;
import com.example.alertuppp.model.HouseholdProfile;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.FamilyRepository;
import com.example.alertuppp.network.HouseholdRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class HouseholdFragment extends Fragment {

    private static final String[] HOUSE_TYPES = {
            "Concrete", "Wood", "Mixed (Concrete & Wood)", "Makeshift / Light Materials"
    };

    private FamilyRepository familyRepo;
    private HouseholdRepository householdRepo;
    private CenterRepository centerRepo;
    private SessionManager session;

    private List<Family> families = new ArrayList<>();
    private List<EvacuationCenter> centers = new ArrayList<>();
    private HouseholdProfile householdProfile;
    private boolean isHead = false;

    // ── Pane A — registration ─────────────────────────────────────────────────
    private View paneRegister;
    private TextInputLayout tilRegName, tilRegAddress, tilRegBarangay, tilRegMunicipality;
    private TextInputEditText etRegName, etRegAddress;
    private AutoCompleteTextView spinnerRegMunicipality, spinnerRegBarangay, spinnerRegHouseType;
    private CheckBox cbRegFlood, cbRegLandslide;
    private ProgressBar progressRegister;
    private MaterialButton btnRegisterHousehold;

    // ── Pane B — dashboard ────────────────────────────────────────────────────
    private View paneDashboard;
    private TextView tvHouseholdName, tvHeadBadge, tvEmpty, tvStatFamilies, tvStatMembers,
            tvHouseholdAddress, tvHouseholdDetails;
    private MaterialButton btnEditHousehold;
    private FloatingActionButton fabAddFamily;
    private RecyclerView rvFamilies;
    private FamilyAdapter adapter;

    // ── Loading spinner ───────────────────────────────────────────────────────
    private ProgressBar progressLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_household, container, false);

        familyRepo    = new FamilyRepository(requireContext());
        householdRepo = new HouseholdRepository(requireContext());
        centerRepo    = new CenterRepository(requireContext());
        session       = new SessionManager(requireContext());

        // Pane A
        paneRegister          = view.findViewById(R.id.paneRegister);
        tilRegName            = view.findViewById(R.id.tilRegName);
        tilRegAddress         = view.findViewById(R.id.tilRegAddress);
        tilRegBarangay        = view.findViewById(R.id.tilRegBarangay);
        tilRegMunicipality    = view.findViewById(R.id.tilRegMunicipality);
        etRegName             = view.findViewById(R.id.etRegName);
        etRegAddress          = view.findViewById(R.id.etRegAddress);
        spinnerRegMunicipality = view.findViewById(R.id.spinnerRegMunicipality);
        spinnerRegBarangay    = view.findViewById(R.id.spinnerRegBarangay);
        spinnerRegHouseType   = view.findViewById(R.id.spinnerRegHouseType);
        cbRegFlood            = view.findViewById(R.id.cbRegFlood);
        cbRegLandslide        = view.findViewById(R.id.cbRegLandslide);
        progressRegister      = view.findViewById(R.id.progressRegister);
        btnRegisterHousehold  = view.findViewById(R.id.btnRegisterHousehold);

        // Pane B
        paneDashboard    = view.findViewById(R.id.paneDashboard);
        tvHouseholdName    = view.findViewById(R.id.tvHouseholdName);
        tvHeadBadge        = view.findViewById(R.id.tvHeadBadge);
        tvEmpty            = view.findViewById(R.id.tvEmpty);
        tvStatFamilies     = view.findViewById(R.id.tvStatFamilies);
        tvStatMembers      = view.findViewById(R.id.tvStatMembers);
        tvHouseholdAddress = view.findViewById(R.id.tvHouseholdAddress);
        tvHouseholdDetails = view.findViewById(R.id.tvHouseholdDetails);
        btnEditHousehold = view.findViewById(R.id.btnEditHousehold);
        fabAddFamily     = view.findViewById(R.id.fabAddFamily);
        rvFamilies       = view.findViewById(R.id.rvFamilies);

        progressLoading = view.findViewById(R.id.progressLoading);

        setupDropdowns();
        setupAdapter();
        setupListeners();
        loadData();

        return view;
    }

    // ── Dropdowns ─────────────────────────────────────────────────────────────

    private void setupDropdowns() {
        spinnerRegMunicipality.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, CamNorteLocations.MUNICIPALITIES));

        // Cascade: when municipality is picked, populate barangay dropdown
        spinnerRegMunicipality.setOnItemClickListener((parent, v, pos, id) -> {
            String muni = CamNorteLocations.MUNICIPALITIES[pos];
            spinnerRegBarangay.setText("", false);
            spinnerRegBarangay.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    CamNorteLocations.getBarangays(muni)));
            if (tilRegBarangay != null) tilRegBarangay.setEnabled(true);
        });

        // Barangay starts disabled
        if (tilRegBarangay != null) tilRegBarangay.setEnabled(false);

        spinnerRegHouseType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, HOUSE_TYPES));
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private void setupAdapter() {
        adapter = new FamilyAdapter(families, new FamilyAdapter.Listener() {
            @Override public void onEditFamily(Family f)   { showEditFamilyDialog(f); }
            @Override public void onDeleteFamily(Family f) { confirmDeleteFamily(f); }
            @Override public void onEditMember(Family f, FamilyMember m, int pos) {
                showMemberDialogWithRefresh(f, m, pos, null, null);
            }
            @Override public void onDeleteMember(Family f, FamilyMember m, int pos) {
                confirmDeleteMember(f, m, pos);
            }
        });
        rvFamilies.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFamilies.setAdapter(adapter);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        btnRegisterHousehold.setOnClickListener(v -> registerHousehold());

        fabAddFamily.setOnClickListener(v -> showAddFamilyDialog());

        btnEditHousehold.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new HouseholdProfileFragment())
                        .addToBackStack(null)
                        .commit());
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadData() {
        showLoading(true);

        centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override public void onSuccess(List<EvacuationCenter> c) { if (isAdded()) centers = c; }
            @Override public void onError(String msg) { /* silent */ }
        });

        householdRepo.loadProfile(session.getUserId(),
                new HouseholdRepository.Callback<HouseholdProfile>() {
                    @Override public void onSuccess(HouseholdProfile p) {
                        if (!isAdded()) return;
                        householdProfile = p;
                        isHead = p != null && session.getUserId().equals(p.getHeadResidentId());

                        if (p == null) {
                            // No household — show registration form
                            requireActivity().runOnUiThread(() -> {
                                showLoading(false);
                                showPane(false);
                            });
                            return;
                        }

                        // Has household — load families then show dashboard
                        familyRepo.loadFamilies(p.getId(),
                                new FamilyRepository.Callback<List<Family>>() {
                                    @Override public void onSuccess(List<Family> result) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() -> {
                                            families.clear();
                                            families.addAll(result);
                                            adapter.notifyDataSetChanged();
                                            adapter.setHeadMode(isHead);
                                            showLoading(false);
                                            showPane(true);
                                            refreshDashboard();
                                        });
                                    }
                                    @Override public void onError(String msg) {
                                        if (!isAdded()) return;
                                        requireActivity().runOnUiThread(() -> {
                                            showLoading(false);
                                            showPane(true);
                                            refreshDashboard();
                                            Toast.makeText(requireContext(),
                                                    "Could not load families: " + msg,
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                });
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            showLoading(false);
                            showPane(false); // fall back to registration
                            Toast.makeText(requireContext(),
                                    "Could not load household: " + msg,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    // ── Pane switching ────────────────────────────────────────────────────────

    /** @param dashboard true = show dashboard, false = show registration form */
    private void showPane(boolean dashboard) {
        progressLoading.setVisibility(View.GONE);
        paneRegister.setVisibility(dashboard ? View.GONE : View.VISIBLE);
        paneDashboard.setVisibility(dashboard ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean loading) {
        progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            paneRegister.setVisibility(View.GONE);
            paneDashboard.setVisibility(View.GONE);
        }
    }

    // ── Dashboard refresh ─────────────────────────────────────────────────────

    private void refreshDashboard() {
        if (householdProfile == null) return;
        String name = householdProfile.getHouseholdName();
        tvHouseholdName.setText("🏠  " + (name != null && !name.isEmpty() ? name : "My Household"));

        // Address + barangay
        String addr = householdProfile.getAddress();
        String brgy = householdProfile.getBarangay();
        String muni = householdProfile.getMunicipality();
        StringBuilder addrLine = new StringBuilder("📍 ");
        if (addr != null && !addr.isEmpty()) addrLine.append(addr);
        if (brgy != null && !brgy.isEmpty()) addrLine.append(", Brgy. ").append(brgy);
        if (muni != null && !muni.isEmpty()) addrLine.append(", ").append(muni);
        tvHouseholdAddress.setText(addrLine.toString());

        // House type + risk zones
        String houseType = householdProfile.getHouseType();
        StringBuilder details = new StringBuilder();
        if (houseType != null && !houseType.isEmpty()) details.append("🏡 ").append(houseType);
        if (householdProfile.isNearFloodZone())     details.append("  🌊 Flood Zone");
        if (householdProfile.isNearLandslideZone()) details.append("  ⛰ Landslide Zone");
        tvHouseholdDetails.setText(details.toString());

        if (isHead) {
            tvHeadBadge.setText("👑 Household Head");
            tvHeadBadge.setVisibility(View.VISIBLE);
            fabAddFamily.setVisibility(View.VISIBLE);
            btnEditHousehold.setVisibility(View.VISIBLE);
        } else {
            tvHeadBadge.setText("👤 Member");
            tvHeadBadge.setVisibility(View.VISIBLE);
            fabAddFamily.setVisibility(View.GONE);
            btnEditHousehold.setVisibility(View.GONE);
        }

        int totalMembers = 0;
        for (Family f : families) totalMembers += f.getMemberCount();
        tvStatFamilies.setText(String.valueOf(families.size()));
        tvStatMembers.setText(String.valueOf(totalMembers));
        tvEmpty.setVisibility(families.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Register household (Pane A submit) ────────────────────────────────────

    private void registerHousehold() {
        String name       = txt(etRegName);
        String address    = txt(etRegAddress);
        String barangay   = spinnerRegBarangay.getText().toString().trim();
        String muni       = spinnerRegMunicipality.getText().toString().trim();
        String houseType  = spinnerRegHouseType.getText().toString().trim();

        tilRegName.setError(null);
        tilRegAddress.setError(null);
        tilRegMunicipality.setError(null);

        if (TextUtils.isEmpty(name))     { tilRegName.setError("Required"); return; }
        if (TextUtils.isEmpty(address))  { tilRegAddress.setError("Required"); return; }
        if (TextUtils.isEmpty(muni))     { tilRegMunicipality.setError("Required"); return; }
        if (TextUtils.isEmpty(barangay)) {
            Toast.makeText(requireContext(), "Please select a barangay", Toast.LENGTH_SHORT).show();
            return;
        }

        HouseholdProfile p = new HouseholdProfile();
        p.setHouseholdName(name);
        p.setAddress(address);
        p.setBarangay(barangay);
        p.setMunicipality(muni);
        p.setHouseType(TextUtils.isEmpty(houseType) ? "Concrete" : houseType);
        p.setNearFloodZone(cbRegFlood.isChecked());
        p.setNearLandslideZone(cbRegLandslide.isChecked());

        progressRegister.setVisibility(View.VISIBLE);
        btnRegisterHousehold.setEnabled(false);

        householdRepo.saveProfile(p, session.getUserId(),
                new HouseholdRepository.Callback<HouseholdProfile>() {
                    @Override public void onSuccess(HouseholdProfile saved) {
                        if (!isAdded()) return;
                        householdProfile = saved;
                        isHead = true;
                        requireActivity().runOnUiThread(() -> {
                            progressRegister.setVisibility(View.GONE);
                            btnRegisterHousehold.setEnabled(true);
                            adapter.setHeadMode(true);
                            showPane(true);
                            refreshDashboard();
                            Toast.makeText(requireContext(),
                                    "Household registered!", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            progressRegister.setVisibility(View.GONE);
                            btnRegisterHousehold.setEnabled(true);
                            Toast.makeText(requireContext(),
                                    "Registration failed: " + msg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ── Add Family dialog ─────────────────────────────────────────────────────

    private void showAddFamilyDialog() {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_family_member, null); // reuse simple layout
        // We only need a name field — repurpose etMemberName
        TextInputEditText etName = dv.findViewById(R.id.etMemberName);
        dv.findViewById(R.id.tilMemberAge).setVisibility(View.GONE);
        dv.findViewById(R.id.tilMemberNotes).setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Add Family")
                .setMessage("Give this family a name (e.g. \"Dela Cruz Family\")")
                .setView(dv)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                if (TextUtils.isEmpty(name)) {
                    etName.setError("Family name is required");
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Adding…");

                familyRepo.addFamily(householdProfile.getId(), session.getUserId(), name,
                        new FamilyRepository.Callback<Family>() {
                            @Override public void onSuccess(Family f) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    dialog.dismiss();
                                    families.add(0, f);
                                    adapter.notifyItemInserted(0);
                                    refreshDashboard();
                                    Toast.makeText(requireContext(),
                                            f.getFamilyName() + " added", Toast.LENGTH_SHORT).show();
                                });
                            }
                            @Override public void onError(String msg) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Add");
                                    Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
            });
        });
        dialog.show();
    }

    // ── Edit Family dialog (name + center + members) ──────────────────────────

    private void showEditFamilyDialog(Family family) {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_family, null);

        TextInputEditText etFamilyName = dv.findViewById(R.id.etFamilyName);
        AutoCompleteTextView spinnerCenter = dv.findViewById(R.id.spinnerFamilyCenter);
        RecyclerView rvMembers = dv.findViewById(R.id.rvEditMembers);
        TextView tvNoMembers = dv.findViewById(R.id.tvEditNoMembers);

        etFamilyName.setText(family.getFamilyName());

        // Center dropdown — "None" + loaded centers
        List<String> centerOptions = new ArrayList<>();
        centerOptions.add("None");
        for (EvacuationCenter c : centers) centerOptions.add(c.getName());
        spinnerCenter.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, centerOptions));
        if (family.getCenterName() != null && !family.getCenterName().isEmpty())
            spinnerCenter.setText(family.getCenterName(), false);
        else
            spinnerCenter.setText("None", false);

        // Members list inside dialog — use array wrapper so lambdas can reference it
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        FamilyMemberAdapter[] memberAdapterRef = {null};
        Runnable[] refreshRef = {null};

        memberAdapterRef[0] = new FamilyMemberAdapter(family.getMembers(),
                new FamilyMemberAdapter.Listener() {
                    @Override public void onEdit(FamilyMember m, int pos) {
                        showMemberDialogWithRefresh(family, m, pos, memberAdapterRef[0], refreshRef[0]);
                    }
                    @Override public void onDelete(FamilyMember m, int pos) {
                        confirmDeleteMember(family, m, pos);
                    }
                });
        rvMembers.setAdapter(memberAdapterRef[0]);

        refreshRef[0] = () -> {
            memberAdapterRef[0].notifyDataSetChanged();
            tvNoMembers.setVisibility(family.getMemberCount() == 0 ? View.VISIBLE : View.GONE);
            rvMembers.setVisibility(family.getMemberCount() == 0 ? View.GONE : View.VISIBLE);
        };
        refreshRef[0].run();

        dv.findViewById(R.id.btnAddMemberInEdit).setOnClickListener(v ->
                showMemberDialogWithRefresh(family, null, -1, memberAdapterRef[0], refreshRef[0]));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit Family")
                .setView(dv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newName = etFamilyName.getText() != null
                        ? etFamilyName.getText().toString().trim() : "";
                if (TextUtils.isEmpty(newName)) {
                    etFamilyName.setError("Required");
                    return;
                }

                // Resolve selected center id
                String selectedCenterText = spinnerCenter.getText().toString().trim();
                String newCenterId = null;
                String newCenterName = null;
                for (EvacuationCenter c : centers) {
                    if (c.getName().equals(selectedCenterText)) {
                        newCenterId = c.getId();
                        newCenterName = c.getName();
                        break;
                    }
                }

                family.setFamilyName(newName);
                family.setCenterId(newCenterId);
                family.setCenterName(newCenterName);

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Saving…");

                familyRepo.updateFamily(family, new FamilyRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            dialog.dismiss();
                            adapter.notifyDataSetChanged();
                            refreshDashboard();
                        });
                    }
                    @Override public void onError(String msg) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Save");
                            Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    // ── Delete Family ─────────────────────────────────────────────────────────

    private void confirmDeleteFamily(Family f) {
        String familyLabel = f.getFamilyName() != null ? f.getFamilyName() : "this family";
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Family")
                .setMessage("Remove \"" + familyLabel + "\"? All members will be deleted.")
                .setPositiveButton("Remove", (d, w) ->
                        familyRepo.deleteFamily(f.getId(), new FamilyRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    int idx = families.indexOf(f);
                                    if (idx >= 0) { families.remove(idx); adapter.notifyItemRemoved(idx); }
                                    refreshDashboard();
                                });
                            }
                            @Override public void onError(String msg) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_SHORT).show());
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMemberDialogWithRefresh(Family family, @Nullable FamilyMember existing,
            int editPos, @Nullable FamilyMemberAdapter inlineAdapter, @Nullable Runnable onRefresh) {
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_family_member, null);

        TextInputEditText etName  = dv.findViewById(R.id.etMemberName);
        TextInputEditText etAge   = dv.findViewById(R.id.etMemberAge);
        TextInputEditText etNotes = dv.findViewById(R.id.etMemberNotes);

        boolean isEdit = existing != null;
        if (isEdit) {
            etName.setText(existing.getFullName());
            etAge.setText(existing.getAge() > 0 ? String.valueOf(existing.getAge()) : "");
            etNotes.setText(existing.getNotes());
        }

        AlertDialog memberDialog = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "Edit Member" : "Add Member")
                .setView(dv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        memberDialog.setOnShowListener(d -> {
            memberDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                if (TextUtils.isEmpty(name)) {
                    etName.setError("Name is required");
                    return; // keeps dialog open
                }
                int age = 0;
                try { age = Integer.parseInt(
                        etAge.getText() != null ? etAge.getText().toString().trim() : "0");
                } catch (NumberFormatException ignored) {}
                String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

                memberDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                if (isEdit) {
                    existing.setFullName(name); existing.setAge(age); existing.setNotes(notes);
                    familyRepo.updateMember(existing, new FamilyRepository.Callback<Void>() {
                        @Override public void onSuccess(Void r) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                memberDialog.dismiss();
                                if (inlineAdapter != null) inlineAdapter.notifyDataSetChanged();
                                if (onRefresh != null) onRefresh.run();
                                adapter.notifyDataSetChanged();
                                refreshDashboard();
                            });
                        }
                        @Override public void onError(String msg) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                memberDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                Toast.makeText(requireContext(), "Update failed: " + msg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                } else {
                    FamilyMember m = new FamilyMember(name, age);
                    m.setNotes(notes);
                    familyRepo.addMember(family.getId(), m, new FamilyRepository.Callback<FamilyMember>() {
                        @Override public void onSuccess(FamilyMember saved) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                memberDialog.dismiss();
                                family.addMember(saved);
                                if (inlineAdapter != null) inlineAdapter.notifyDataSetChanged();
                                if (onRefresh != null) onRefresh.run();
                                adapter.notifyDataSetChanged();
                                refreshDashboard();
                            });
                        }
                        @Override public void onError(String msg) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                memberDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }
            });
        });
        memberDialog.show();
    }

    // ── Delete Member ─────────────────────────────────────────────────────────

    private void confirmDeleteMember(Family family, FamilyMember member, int pos) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Member")
                .setMessage("Remove " + member.getFullName() + "?")
                .setPositiveButton("Remove", (d, w) ->
                        familyRepo.deleteMember(member.getId(), new FamilyRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    family.removeMember(pos);
                                    adapter.notifyDataSetChanged();
                                    refreshDashboard();
                                });
                            }
                            @Override public void onError(String msg) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_SHORT).show());
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String txt(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (familyRepo != null) familyRepo.shutdown();
    }
}
