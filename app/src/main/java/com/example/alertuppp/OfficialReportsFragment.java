package com.example.alertuppp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.ReportAdapter;
import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.network.ReportRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

public class OfficialReportsFragment extends Fragment {

    private ReportAdapter adapter;
    private ReportRepository repo;
    private String activeFilter = "all";
    private boolean isShowingResolved = false;
    private TextView btnTabActive, btnTabResolved;
    private Map<String, BitmapDrawable> iconCache = new HashMap<>();

    private TextView tvStatPending, tvStatOngoing, tvStatResolved, tvStatTotal;
    private MapView mapView;
    private View mapContainer;
    private List<IncidentReport> currentReports = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_official_reports, container, false);

        repo = new ReportRepository(requireContext());

        tvStatPending  = view.findViewById(R.id.tvStatPending);
        tvStatOngoing  = view.findViewById(R.id.tvStatOngoing);
        tvStatResolved = view.findViewById(R.id.tvStatResolved);
        tvStatTotal    = view.findViewById(R.id.tvStatTotal);

        mapContainer = view.findViewById(R.id.mapContainer);
        setupMap(view);
        view.findViewById(R.id.btnCloseMap).setOnClickListener(v -> mapContainer.setVisibility(View.GONE));

        btnTabActive = view.findViewById(R.id.btnTabActive);
        btnTabResolved = view.findViewById(R.id.btnTabResolved);

        btnTabActive.setOnClickListener(v -> switchTab(false));
        btnTabResolved.setOnClickListener(v -> switchTab(true));

        RecyclerView rv = view.findViewById(R.id.rvReports);
        adapter = new ReportAdapter(true); // show official actions
        adapter.setListener(new ReportAdapter.OnReportActionListener() {
            @Override
            public void onVerify(IncidentReport report) {
                updateStatus(report, "verified");
            }

            @Override
            public void onRespond(IncidentReport report) {
                showRespondDialog(report);
            }

            @Override
            public void onResolve(IncidentReport report) {
                confirmResolve(report);
            }

            @Override
            public void onReportClick(IncidentReport report) {
                showOnMap(report);
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        setupFilterChips(view);
        loadReports();

        return view;
    }

    private void setupFilterChips(View view) {
        int[] chipIds = {
                R.id.filterAll, R.id.filterFlood, R.id.filterRescue,
                R.id.filterDamage, R.id.filterMedical, R.id.filterMissing
        };
        String[] filters = {"all", "flood", "rescue", "damage", "medical", "missing"};

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
                loadReports();
            });
        }
    }

    private void switchTab(boolean resolved) {
        isShowingResolved = resolved;
        
        btnTabActive.setBackgroundResource(resolved ? 0 : R.drawable.bg_tab_selected);
        btnTabActive.setTextColor(resolved ? 0xFF757575 : 0xFFFFFFFF);
        btnTabActive.setTypeface(null, resolved ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);

        btnTabResolved.setBackgroundResource(resolved ? R.drawable.bg_tab_selected : 0);
        btnTabResolved.setTextColor(resolved ? 0xFFFFFFFF : 0xFF757575);
        btnTabResolved.setTypeface(null, resolved ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        loadReports();
    }

    private void loadReports() {
        String statusFilter = isShowingResolved ? "resolved" : "pending,verified,ongoing";
        repo.loadAll(activeFilter, statusFilter, new ReportRepository.Callback<List<IncidentReport>>() {
            @Override
            public void onSuccess(List<IncidentReport> reports) {
                if (!isAdded()) return;

                // Grouping Logic
                Map<String, IncidentReport> primaryMap = new HashMap<>();
                List<IncidentReport> duplicates = new ArrayList<>();

                for (IncidentReport r : reports) {
                    if (!r.isDuplicate()) {
                        primaryMap.put(r.getId(), r);
                    } else {
                        duplicates.add(r);
                    }
                }

                // Increment counts for parents
                for (IncidentReport d : duplicates) {
                    IncidentReport parent = primaryMap.get(d.getParentReportId());
                    if (parent != null) {
                        parent.setDuplicateCount(parent.getDuplicateCount() + 1);
                    }
                }

                List<IncidentReport> grouped = new ArrayList<>(primaryMap.values());
                // Sort by date again as map breaks order
                grouped.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

                currentReports = reports;
                requireActivity().runOnUiThread(() -> {
                    adapter.setData(grouped);
                    updateStats(reports);
                    if (mapContainer.getVisibility() == View.VISIBLE) {
                        plotMarkers(grouped);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Could not load reports: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateStats(List<IncidentReport> reports) {
        int pending = 0, ongoing = 0, resolved = 0;
        for (IncidentReport r : reports) {
            switch (r.getStatus() != null ? r.getStatus() : "pending") {
                case "pending":  pending++;  break;
                case "ongoing":  ongoing++;  break;
                case "resolved": resolved++; break;
            }
        }
        tvStatPending.setText(String.valueOf(pending));
        tvStatOngoing.setText(String.valueOf(ongoing));
        tvStatResolved.setText(String.valueOf(resolved));
        tvStatTotal.setText(String.valueOf(reports.size()));
    }

    private void updateStatus(IncidentReport report, String newStatus) {
        repo.updateStatus(report.getId(), newStatus, new ReportRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void r) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            "Report marked as " + newStatus, Toast.LENGTH_SHORT).show();
                    if (requireActivity() instanceof OfficialMainActivity) {
                        ((OfficialMainActivity) requireActivity()).refreshPendingBadge();
                    }
                    loadReports();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Update failed: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showRespondDialog(IncidentReport report) {
        String[] teams = {"MDRRMO Team A", "MDRRMO Team B", "BFP", "PNP", "Barangay Tanod"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Respond to Incident")
                .setItems(teams, (d, which) -> {
                    repo.respond(report.getId(), teams[which], new ReportRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(),
                                        "Assigned to " + teams[which], Toast.LENGTH_SHORT).show();
                                if (requireActivity() instanceof OfficialMainActivity) {
                                    ((OfficialMainActivity) requireActivity()).refreshPendingBadge();
                                }
                                loadReports();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(),
                                            "Failed to assign: " + message, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .show();
    }

    private void confirmResolve(IncidentReport report) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Mark as Resolved")
                .setMessage("Confirm that \"" + report.getTitle() + "\" has been resolved?")
                .setPositiveButton("Resolve", (d, w) -> updateStatus(report, "resolved"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOnMap(IncidentReport r) {
        if (r.getLatitude() == 0) {
            Toast.makeText(requireContext(), "No location coordinates for this report", Toast.LENGTH_SHORT).show();
            return;
        }

        mapContainer.setVisibility(View.VISIBLE);
        plotMarkers(currentReports);
        mapView.getController().animateTo(new GeoPoint(r.getLatitude(), r.getLongitude()), 16.5, 800L);
    }

    private void setupMap(View view) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        mapView = view.findViewById(R.id.officialReportsMap);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(12.5);
        mapView.getController().setCenter(new GeoPoint(14.1165, 122.9551)); // Daet
    }

    private void plotMarkers(List<IncidentReport> reports) {
        if (mapView == null) return;
        mapView.getOverlays().clear();

        List<GeoPoint> points = new ArrayList<>();
        for (IncidentReport r : reports) {
            if (r.getLatitude() == 0) continue;
            if (r.isDuplicate()) continue;
            if ("resolved".equals(r.getStatus()) && !isShowingResolved) continue;

            GeoPoint gp = new GeoPoint(r.getLatitude(), r.getLongitude());
            points.add(gp);

            Marker m = new Marker(mapView);
            m.setPosition(gp);
            m.setTitle(r.getTitle());
            m.setSnippet(r.getTypeLabel() + " · " + r.getStatus().toUpperCase());
            m.setIcon(makeReportIcon(r));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setOnMarkerClickListener((marker, mv) -> {
                marker.showInfoWindow();
                return true;
            });
            mapView.getOverlays().add(m);
        }

        if (!points.isEmpty()) {
            if (points.size() == 1) {
                mapView.getController().animateTo(points.get(0));
            } else {
                BoundingBox box = BoundingBox.fromGeoPoints(points);
                mapView.post(() -> mapView.zoomToBoundingBox(box, true, 100));
            }
        }
        mapView.invalidate();
    }

    private BitmapDrawable makeReportIcon(IncidentReport r) {
        String emoji = r.getTypeEmoji();
        if (iconCache.containsKey(emoji)) return iconCache.get(emoji);

        int size = 70;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.parseColor("#FF9800"));
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size * 0.45f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float yOffset = (fm.descent + fm.ascent) / 2;
        canvas.drawText(emoji, size / 2f, size / 2f - yOffset, paint);

        paint.setColor(Color.parseColor("#80000000"));
        paint.setStrokeWidth(2f);
        canvas.drawLine(size / 2f, size / 2f + size / 2.2f, size / 2f, size - 1, paint);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        iconCache.put(emoji, drawable);
        return drawable;
    }

    @Override public void onResume()  { super.onResume();  if (mapView != null) mapView.onResume(); }
    @Override public void onPause()   { super.onPause();   if (mapView != null) mapView.onPause(); }
}
