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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.alertuppp.adapter.DashboardCenterAdapter;
import com.example.alertuppp.adapter.ReportAdapter;
import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.AlertRepository;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.FamilyRepository;
import com.example.alertuppp.network.ReportRepository;
import com.example.alertuppp.util.ReportGenerator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class OfficialDashboardFragment extends Fragment {

    private CenterRepository centerRepo;
    private ReportRepository reportRepo;
    private AlertRepository alertRepo;
    private FamilyRepository familyRepo;

    private TextView tvTotalEvacuees, tvActiveCenters, tvPendingReports, tvActiveAlerts, tvGreeting;
    private RecyclerView rvOccupancy, rvReports;
    private ReportAdapter reportAdapter;
    private DashboardCenterAdapter occupancyAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private com.airbnb.lottie.LottieAnimationView loadingAnim;
    


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        dashboardView = inflater.inflate(R.layout.fragment_official_dashboard, container, false);

        centerRepo = new CenterRepository(requireContext());
        reportRepo = new ReportRepository(requireContext());
        alertRepo  = new AlertRepository(requireContext());
        familyRepo = new FamilyRepository(requireContext());
        newsRepo   = new com.example.alertuppp.network.NewsRepository();

        tvTotalEvacuees  = dashboardView.findViewById(R.id.tvTotalEvacuees);
        tvActiveCenters  = dashboardView.findViewById(R.id.tvActiveCenters);
        tvPendingReports = dashboardView.findViewById(R.id.tvPendingReports);
        tvActiveAlerts   = dashboardView.findViewById(R.id.tvActiveAlerts);
        tvGreeting       = dashboardView.findViewById(R.id.tvGreeting);

        swipeRefresh     = dashboardView.findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::loadDashboardData);
        swipeRefresh.setColorSchemeResources(R.color.primary);

        rvOccupancy = dashboardView.findViewById(R.id.rvCenterOccupancy);
        rvReports   = dashboardView.findViewById(R.id.rvPendingReports);
        loadingAnim = dashboardView.findViewById(R.id.loadingAnim);
        
        updateGreeting();
        setupRecyclerViews();
        
        dashboardView.findViewById(R.id.btnManageAllCenters).setOnClickListener(v -> navigateTo(new OfficialCentersFragment()));
        dashboardView.findViewById(R.id.btnViewAllReports).setOnClickListener(v -> navigateTo(new OfficialReportsFragment()));
        dashboardView.findViewById(R.id.btnGeneratePdfReport).setOnClickListener(v -> startReportGeneration());

        loadDashboardData();
        return dashboardView;
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String msg = (hour < 12) ? "Good morning," : (hour < 18) ? "Good afternoon," : "Good evening,";
        tvGreeting.setText(msg);
    }

    private void setupRecyclerViews() {
        rvOccupancy.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        occupancyAdapter = new DashboardCenterAdapter();
        rvOccupancy.setAdapter(occupancyAdapter);

        rvReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        reportAdapter = new ReportAdapter(true);
        rvReports.setAdapter(reportAdapter);
    }

    private void loadDashboardData() {
        boolean isSwiping = swipeRefresh.isRefreshing();
        if (!isSwiping) loadingAnim.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(true);
        loadCount = 0;

        centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override
            public void onSuccess(List<EvacuationCenter> centers) {
                if (!isAdded()) return;
                int totalEvacuees = 0;
                int activeCount = 0;
                for (EvacuationCenter c : centers) {
                    totalEvacuees += c.getCurrentOccupancy();
                    if (!"closed".equals(c.getStatus())) activeCount++;
                }
                final int evacuees = totalEvacuees;
                final int active = activeCount;
                requireActivity().runOnUiThread(() -> {
                    tvTotalEvacuees.setText(String.valueOf(evacuees));
                    tvActiveCenters.setText(String.valueOf(active));
                    occupancyAdapter.setData(centers);
                    checkLoadingDone();
                });
            }
            @Override public void onError(String message) { checkLoadingDone(); }
        });

        reportRepo.loadAll("all", null, new ReportRepository.Callback<List<IncidentReport>>() {
            @Override
            public void onSuccess(List<IncidentReport> reports) {
                if (!isAdded()) return;
                int pCount = 0, flood = 0, rescue = 0, risk = 0;
                for (IncidentReport r : reports) {
                    if ("pending".equals(r.getStatus())) pCount++;
                    if ("flood".equalsIgnoreCase(r.getReportType())) flood++;
                    else if ("rescue".equalsIgnoreCase(r.getReportType())) rescue++;
                    else if ("unsafe".equalsIgnoreCase(r.getReportType())) risk++;
                }
                final int p = pCount, f = flood, res = rescue, rsk = risk;
                requireActivity().runOnUiThread(() -> {
                    tvPendingReports.setText(String.valueOf(p));
                    
                    if (requireActivity() instanceof OfficialMainActivity) {
                        ((OfficialMainActivity) requireActivity()).refreshPendingBadge();
                    }

                    List<IncidentReport> pendingList = new java.util.ArrayList<>();
                    for (IncidentReport r : reports) if ("pending".equals(r.getStatus())) pendingList.add(r);
                    reportAdapter.setData(pendingList.subList(0, Math.min(3, pendingList.size())));
                    checkLoadingDone();
                });
            }
            @Override public void onError(String message) { checkLoadingDone(); }
        });

        alertRepo.loadAll(new AlertRepository.Callback<List<com.example.alertuppp.model.Alert>>() {
            @Override
            public void onSuccess(List<com.example.alertuppp.model.Alert> alerts) {
                if (!isAdded()) return;
                int active = 0;
                for (com.example.alertuppp.model.Alert a : alerts) if (a.isActive()) active++;
                final int count = active;
                requireActivity().runOnUiThread(() -> {
                    tvActiveAlerts.setText(String.valueOf(count));
                    checkLoadingDone();
                });
            }
            @Override public void onError(String message) { checkLoadingDone(); }
        });

        loadGlobalNews();
    }



    private int loadCount = 0;
    private com.example.alertuppp.network.NewsRepository newsRepo;
    private View dashboardView;

    private void checkLoadingDone() {
        loadCount++;
        if (loadCount >= 4) {
            loadCount = 0;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    loadingAnim.setVisibility(View.GONE);
                });
            }
        }
    }

    private void loadGlobalNews() {
        newsRepo.fetchGlobalNews(new com.example.alertuppp.network.NewsRepository.Callback<List<com.example.alertuppp.network.NewsRepository.NewsArticle>>() {
            @Override
            public void onSuccess(List<com.example.alertuppp.network.NewsRepository.NewsArticle> articles) {
                if (!isAdded() || articles == null) {
                    checkLoadingDone();
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    android.widget.LinearLayout container = dashboardView.findViewById(R.id.linearLayoutGlobalNews);
                    if (container != null) {
                        container.removeAllViews();
                        for (com.example.alertuppp.network.NewsRepository.NewsArticle art : articles) {
                            View nv = getLayoutInflater().inflate(R.layout.item_news_card, container, false);
                            ((TextView)nv.findViewById(R.id.tvNewsTitle)).setText(art.title);
                            ((TextView)nv.findViewById(R.id.tvNewsDesc)).setText(art.description);
                            ((TextView)nv.findViewById(R.id.tvNewsSource)).setText(art.source);
                            ((TextView)nv.findViewById(R.id.tvNewsTime)).setText("Latest Update");
                            
                            nv.setOnClickListener(v -> {
                                android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(art.url));
                                startActivity(browserIntent);
                            });
                            container.addView(nv);
                        }
                    }
                    checkLoadingDone();
                });
            }
            @Override public void onError(String msg) { checkLoadingDone(); }
        });
    }

    private void startReportGeneration() {
        Toast.makeText(requireContext(), "Preparing report data...", Toast.LENGTH_SHORT).show();
        
        // 1. Load Centers
        centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override
            public void onSuccess(List<EvacuationCenter> centers) {
                // 2. Load Evacuated Families
                familyRepo.loadAllEvacuatedFamilies(new FamilyRepository.Callback<List<Family>>() {
                    @Override
                    public void onSuccess(List<Family> families) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            ReportGenerator.generateCenterReport(requireContext(), centers, families);
                        });
                    }
                    @Override
                    public void onError(String message) {
                        if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Error loading families: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
            @Override
            public void onError(String message) {
                if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Error loading centers: " + message, Toast.LENGTH_SHORT).show());
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
