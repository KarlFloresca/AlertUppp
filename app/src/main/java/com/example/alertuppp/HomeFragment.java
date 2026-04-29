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

import com.example.alertuppp.model.Alert;
import com.example.alertuppp.network.AlertRepository;

import java.util.List;

public class HomeFragment extends Fragment {

    private AlertRepository alertRepo;
    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_resident_home, container, false);

        session   = new SessionManager(requireContext());
        alertRepo = new AlertRepository(requireContext());

        // Greet user
        TextView tvName = view.findViewById(R.id.tvUserName);
        tvName.setText(session.getUserName());

        TextView tvLocation = view.findViewById(R.id.tvUserLocation);
        tvLocation.setText("📍 " + session.getMunicipality() + ", Camarines Norte");

        // Load latest active alert for banner
        loadLatestAlert(view);

        // Quick action buttons — navigate to the right tab
        view.findViewById(R.id.btnFindCenters).setOnClickListener(v -> navigateTo(new CentersFragment()));
        view.findViewById(R.id.btnCheckIn).setOnClickListener(v -> navigateTo(new HouseholdFragment()));
        view.findViewById(R.id.btnReportIncident).setOnClickListener(v -> navigateTo(new ReportFragment()));
        view.findViewById(R.id.btnSuggestCenter).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Suggest a new evacuation point — coming soon", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.tvManageHousehold).setOnClickListener(v ->
                navigateTo(new HouseholdProfileFragment()));

        view.findViewById(R.id.btnUpdateStatus).setOnClickListener(v ->
                navigateTo(new HouseholdFragment()));

        view.findViewById(R.id.tvSeeAll).setOnClickListener(v ->
                navigateTo(new CentersFragment()));

        view.findViewById(R.id.tvNotifBell).setOnClickListener(v ->
                navigateTo(new AlertsFragment()));

        view.findViewById(R.id.bannerAlert).setOnClickListener(v ->
                navigateTo(new AlertsFragment()));

        return view;
    }

    private void loadLatestAlert(View view) {
        alertRepo.loadActive(new AlertRepository.Callback<List<Alert>>() {
            @Override
            public void onSuccess(List<Alert> alerts) {
                if (!isAdded() || alerts == null || alerts.isEmpty()) return;
                requireActivity().runOnUiThread(() -> {
                    Alert a = alerts.get(0);
                    TextView tvTitle    = view.findViewById(R.id.tvAlertTitle);
                    TextView tvSubtitle = view.findViewById(R.id.tvAlertSubtitle);
                    tvTitle.setText(a.getTitle());
                    tvSubtitle.setText(a.getArea() != null ? a.getArea() : "");
                    view.findViewById(R.id.bannerAlert).setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                // Banner stays hidden on error — no crash
            }
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
