package com.example.alertuppp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        SessionManager session = new SessionManager(requireContext());

        // Populate header
        String name = session.getUserName();
        String role = session.getRole();
        String municipality = session.getMunicipality();

        TextView tvAvatar       = view.findViewById(R.id.tvAvatarInitial);
        TextView tvName         = view.findViewById(R.id.tvProfileName);
        TextView tvRoleBadge    = view.findViewById(R.id.tvProfileRole);
        TextView tvMunicipality = view.findViewById(R.id.tvProfileMunicipality);
        TextView tvRoleDetail   = view.findViewById(R.id.tvProfileRoleDetail);

        // Avatar: first letter of name
        tvAvatar.setText(name != null && !name.isEmpty()
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?");
        tvName.setText(name);
        tvRoleBadge.setText("official".equals(role) ? "Official" : "Resident");
        tvMunicipality.setText(municipality.isEmpty() ? "—" : municipality);
        tvRoleDetail.setText("official".equals(role) ? "Local Official" : "Resident");

        // Row shortcuts
        view.findViewById(R.id.rowHousehold).setOnClickListener(v ->
                navigateTo(new HouseholdProfileFragment()));

        view.findViewById(R.id.rowFamily).setOnClickListener(v ->
                navigateTo(new HouseholdFragment()));

        view.findViewById(R.id.rowMyReports).setOnClickListener(v ->
                navigateTo(new ReportFragment()));

        // Sign Out
        MaterialButton btnSignOut = view.findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> confirmSignOut(session));

        return view;
    }

    private void confirmSignOut(SessionManager session) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    session.logout(requireContext());
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void navigateTo(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
}
