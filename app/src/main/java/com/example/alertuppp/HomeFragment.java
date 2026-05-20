package com.example.alertuppp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;

import com.example.alertuppp.model.Alert;
import com.example.alertuppp.network.AlertRepository;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.HouseholdRepository;

import java.util.List;

public class HomeFragment extends Fragment {

    private AlertRepository alertRepo;
    private CenterRepository centerRepo;
    private HouseholdRepository householdRepo;
    private com.example.alertuppp.network.FamilyRepository familyRepo;
    private com.example.alertuppp.network.WeatherRepository weatherRepo;
    private com.example.alertuppp.network.NewsRepository newsRepo;
    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_resident_home, container, false);

        session       = new SessionManager(requireContext());
        alertRepo     = new AlertRepository(requireContext());
        centerRepo    = new CenterRepository(requireContext());
        householdRepo = new HouseholdRepository(requireContext());
        familyRepo    = new com.example.alertuppp.network.FamilyRepository(requireContext());
        weatherRepo   = new com.example.alertuppp.network.WeatherRepository();
        newsRepo      = new com.example.alertuppp.network.NewsRepository();

        // Greet user
        TextView tvName = view.findViewById(R.id.tvUserName);
        tvName.setText(session.getUserName());

        TextView tvLocation = view.findViewById(R.id.tvUserLocation);
        tvLocation.setText("📍 " + session.getMunicipality() + ", Camarines Norte");

        loadLatestAlert(view);
        loadHouseholdStatus(view);
        loadNearbyCenters(view);
        loadWeather(view);
        loadGlobalNews(view);

        // Navigation
        view.findViewById(R.id.btnFindCenters).setOnClickListener(v -> navigateTo(new MapFragment()));
        view.findViewById(R.id.btnCheckIn).setOnClickListener(v -> navigateTo(new CentersFragment()));
        view.findViewById(R.id.btnReportIncident).setOnClickListener(v -> navigateTo(new ReportFragment()));
        
        view.findViewById(R.id.tvManageHousehold).setOnClickListener(v -> navigateTo(new HouseholdProfileFragment()));
        view.findViewById(R.id.btnUpdateStatus).setOnClickListener(v -> navigateTo(new HouseholdFragment()));
        view.findViewById(R.id.tvSeeAll).setOnClickListener(v -> navigateTo(new CentersFragment()));
        view.findViewById(R.id.tvNotifBell).setOnClickListener(v -> navigateTo(new AlertsFragment()));
        view.findViewById(R.id.bannerAlert).setOnClickListener(v -> navigateTo(new AlertsFragment()));

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
            @Override public void onError(String msg) {}
        });
    }

    private void loadHouseholdStatus(View view) {
        String uid = session.getUserId();
        if (uid == null) return;

        householdRepo.loadProfile(uid, new HouseholdRepository.Callback<com.example.alertuppp.model.HouseholdProfile>() {
            @Override
            public void onSuccess(com.example.alertuppp.model.HouseholdProfile profile) {
                if (!isAdded() || profile == null) return;
                requireActivity().runOnUiThread(() -> {
                    ((TextView)view.findViewById(R.id.tvHouseholdName)).setText(profile.getHouseholdName() + " Family");
                    
                    // Load families to count members
                    familyRepo.loadFamilies(profile.getId(), new com.example.alertuppp.network.FamilyRepository.Callback<List<com.example.alertuppp.model.Family>>() {
                        @Override
                        public void onSuccess(List<com.example.alertuppp.model.Family> families) {
                            if (!isAdded()) return;
                            int total = 0;
                            boolean evacuated = false;
                            for (com.example.alertuppp.model.Family f : families) {
                                total += f.getMemberCount();
                                if (f.getCenterId() != null && !f.getCenterId().isEmpty() && !"null".equals(f.getCenterId())) evacuated = true;
                            }
                            
                            final int memberCount = total;
                            final boolean isEvacuated = evacuated;
                            requireActivity().runOnUiThread(() -> {
                                TextView tvStatus = view.findViewById(R.id.tvHouseholdStatus);
                                tvStatus.setText(isEvacuated ? "Evacuated" : "Safe at Home");
                                tvStatus.setBackgroundResource(isEvacuated ? R.drawable.badge_full_bg : R.drawable.badge_available_bg);
                                tvStatus.setTextColor(android.graphics.Color.WHITE);
                                
                                LinearLayout container = view.findViewById(R.id.containerMembers);
                                container.removeAllViews();
                                addMemberRow(container, profile.getHouseholdName() + " Household", memberCount + " members registered");
                            });
                        }
                        @Override public void onError(String msg) {}
                    });
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void addMemberRow(LinearLayout container, String title, String status) {
        View row = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, container, false);
        TextView tv1 = row.findViewById(android.R.id.text1);
        TextView tv2 = row.findViewById(android.R.id.text2);
        tv1.setText(title);
        tv1.setTextSize(13);
        tv1.setTextColor(android.graphics.Color.BLACK);
        tv2.setText(status);
        tv2.setTextSize(11);
        container.addView(row);
    }

    private void loadNearbyCenters(View view) {
        centerRepo.loadAll(new CenterRepository.Callback<List<com.example.alertuppp.model.EvacuationCenter>>() {
            @Override
            public void onSuccess(List<com.example.alertuppp.model.EvacuationCenter> centers) {
                if (!isAdded() || centers == null || centers.isEmpty()) return;
                
                android.location.Location myLoc = getMyLocation();
                if (myLoc != null) {
                    // Sort by distance
                    java.util.Collections.sort(centers, (c1, c2) -> {
                        float[] r1 = new float[1];
                        float[] r2 = new float[1];
                        android.location.Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(), c1.getLatitude(), c1.getLongitude(), r1);
                        android.location.Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(), c2.getLatitude(), c2.getLongitude(), r2);
                        return Float.compare(r1[0], r2[0]);
                    });
                }

                requireActivity().runOnUiThread(() -> {
                    LinearLayout container = view.findViewById(R.id.linearLayoutCenters); 
                    container.removeAllViews();
                    
                    for (int i = 0; i < Math.min(centers.size(), 2); i++) {
                        com.example.alertuppp.model.EvacuationCenter c = centers.get(i);
                        View cv = getLayoutInflater().inflate(R.layout.item_center_card, container, false);
                        
                        ((TextView)cv.findViewById(R.id.tvCenterName)).setText(c.getName());
                        
                        String distStr = "";
                        if (myLoc != null && c.getLatitude() != 0) {
                            float[] r = new float[1];
                            android.location.Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(), c.getLatitude(), c.getLongitude(), r);
                            distStr = " · " + com.example.alertuppp.network.RoutingRepository.formatDistance(r[0]);
                        }
                        ((TextView)cv.findViewById(R.id.tvCenterAddress)).setText("📍 " + c.getMunicipality() + distStr);
                        
                        TextView tvStatus = cv.findViewById(R.id.tvCenterStatus);
                        tvStatus.setText(c.isFull() ? "FULL" : "Available");
                        tvStatus.setBackgroundResource(c.isFull() ? R.drawable.badge_full_bg : R.drawable.badge_available_bg);
                        
                        ((TextView)cv.findViewById(R.id.tvCapacityCount)).setText(c.getCurrentOccupancy() + "/" + c.getMaxCapacity());
                        
                        // Update Capacity Bar
                        View fill = cv.findViewById(R.id.capacityFill);
                        fill.post(() -> {
                            int parentWidth = ((View)fill.getParent()).getWidth();
                            double pct = (double)c.getCurrentOccupancy() / c.getMaxCapacity();
                            if (pct > 1.0) pct = 1.0;
                            
                            ViewGroup.LayoutParams lp = fill.getLayoutParams();
                            lp.width = (int)(parentWidth * pct);
                            fill.setLayoutParams(lp);
                            
                            // Color based on occupancy
                            if (pct > 0.9) fill.setBackgroundColor(android.graphics.Color.RED);
                            else if (pct > 0.7) fill.setBackgroundColor(android.graphics.Color.parseColor("#FF9800")); // Orange
                            else fill.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")); // Green
                        });
                        
                        cv.findViewById(R.id.btnDirections).setOnClickListener(v -> navigateTo(MapFragment.newInstance(c.getId(), true)));
                        
                        View btnCheckIn = cv.findViewById(R.id.btnCheckInCenter);
                        btnCheckIn.setEnabled(!c.isFull());
                        btnCheckIn.setAlpha(c.isFull() ? 0.5f : 1.0f);
                        btnCheckIn.setOnClickListener(v -> confirmCheckIn(c));

                        cv.findViewById(R.id.btnViewFamilies).setOnClickListener(v -> 
                            Toast.makeText(requireContext(), "Family list for " + c.getName() + " coming soon", Toast.LENGTH_SHORT).show());

                        cv.setOnClickListener(v -> navigateTo(MapFragment.newInstance(c.getId(), false)));
                        container.addView(cv);
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private android.location.Location getMyLocation() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        if (lm == null) return null;
        android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
        if (loc == null) loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
        return loc;
    }

    private void loadGlobalNews(View view) {
        newsRepo.fetchGlobalNews(new com.example.alertuppp.network.NewsRepository.Callback<List<com.example.alertuppp.network.NewsRepository.NewsArticle>>() {
            @Override
            public void onSuccess(List<com.example.alertuppp.network.NewsRepository.NewsArticle> articles) {
                if (!isAdded() || articles == null) return;
                requireActivity().runOnUiThread(() -> {
                    LinearLayout container = view.findViewById(R.id.linearLayoutGlobalNews);
                    container.removeAllViews();
                    for (com.example.alertuppp.network.NewsRepository.NewsArticle art : articles) {
                        View nv = getLayoutInflater().inflate(R.layout.item_news_card, container, false);
                        ((TextView)nv.findViewById(R.id.tvNewsTitle)).setText(art.title);
                        ((TextView)nv.findViewById(R.id.tvNewsDesc)).setText(art.description);
                        ((TextView)nv.findViewById(R.id.tvNewsSource)).setText(art.source);
                        ((TextView)nv.findViewById(R.id.tvNewsTime)).setText("Latest Update");
                        
                        // Action: open URL in browser
                        nv.setOnClickListener(v -> {
                            android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(art.url));
                            startActivity(browserIntent);
                        });
                        
                        container.addView(nv);
                    }
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void loadWeather(View view) {
        weatherRepo.fetchWeather(session.getMunicipality(), new com.example.alertuppp.network.WeatherRepository.Callback<com.example.alertuppp.network.WeatherRepository.WeatherData>() {
            @Override
            public void onSuccess(com.example.alertuppp.network.WeatherRepository.WeatherData data) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    ((TextView)view.findViewById(R.id.tvWeatherTemp)).setText(data.temp);
                    ((TextView)view.findViewById(R.id.tvWeatherDesc)).setText(data.description + " · " + data.city);
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void confirmCheckIn(com.example.alertuppp.model.EvacuationCenter center) {
        if (center.isFull()) {
            Toast.makeText(requireContext(), "Center is full", Toast.LENGTH_SHORT).show();
            return;
        }
        householdRepo.loadProfile(session.getUserId(), new HouseholdRepository.Callback<com.example.alertuppp.model.HouseholdProfile>() {
            @Override
            public void onSuccess(com.example.alertuppp.model.HouseholdProfile profile) {
                if (profile == null) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Complete profile first", Toast.LENGTH_SHORT).show());
                    return;
                }
                familyRepo.loadFamilies(profile.getId(), new com.example.alertuppp.network.FamilyRepository.Callback<List<com.example.alertuppp.model.Family>>() {
                    @Override
                    public void onSuccess(List<com.example.alertuppp.model.Family> families) {
                        int total = 0;
                        boolean here = false;
                        for (com.example.alertuppp.model.Family f : families) {
                            total += f.getMemberCount();
                            if (center.getId().equals(f.getCenterId())) here = true;
                        }
                        if (here) {
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Already checked in", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        final int count = total > 0 ? total : 1;
                        requireActivity().runOnUiThread(() -> {
                            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Check In")
                                .setMessage("Check " + count + " members into " + center.getName() + "?")
                                .setPositiveButton("Check In", (d, w) -> performCheckIn(center, count))
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    }
                    @Override public void onError(String msg) {}
                });
            }
            @Override public void onError(String msg) {}
        });
    }

    private void performCheckIn(com.example.alertuppp.model.EvacuationCenter center, int count) {
        centerRepo.checkInHousehold(center.getId(), session.getUserId(), count, new CenterRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Checked in successfully!", Toast.LENGTH_SHORT).show();
                    loadNearbyCenters(getView()); // Refresh
                    loadHouseholdStatus(getView()); // Update status badge
                });
            }
            @Override public void onError(String msg) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Error: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void navigateTo(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
}
