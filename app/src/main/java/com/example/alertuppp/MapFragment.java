package com.example.alertuppp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.model.IncidentReport;
import com.example.alertuppp.model.Family;
import com.example.alertuppp.model.HouseholdProfile;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.FamilyRepository;
import com.example.alertuppp.network.HouseholdRepository;
import com.example.alertuppp.network.ReportRepository;
import com.example.alertuppp.network.RoutingRepository;
import com.google.android.material.textfield.TextInputEditText;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment {

    private static final int LOCATION_PERM = 102;

    public static final String ARG_CENTER_ID = "center_id";
    public static final String ARG_SHOW_DIRECTIONS = "show_directions";

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private CenterRepository centerRepo;
    private HouseholdRepository householdRepo;
    private FamilyRepository familyRepo;
    private ReportRepository reportRepo;
    private RoutingRepository routingRepo;
    private List<EvacuationCenter> centers = new ArrayList<>();
    private List<IncidentReport> reports = new ArrayList<>();
    private EvacuationCenter selectedCenter;
    private Polyline routeLine;
    private Map<String, BitmapDrawable> iconCache = new HashMap<>();

    // UI Views
    private TextView tvCenterCount, tvGpsStatus;
    private AlertDialog centerDialog;

    public static MapFragment newInstance(String centerId, boolean showDirections) {
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CENTER_ID, centerId);
        args.putBoolean(ARG_SHOW_DIRECTIONS, showDirections);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View view = inflater.inflate(R.layout.fragment_map, container, false);

        centerRepo  = new CenterRepository(requireContext());
        householdRepo = new HouseholdRepository(requireContext());
        familyRepo = new FamilyRepository(requireContext());
        reportRepo  = new ReportRepository(requireContext());
        routingRepo = new RoutingRepository();

        mapView         = view.findViewById(R.id.mapView);
        tvGpsStatus     = view.findViewById(R.id.tvGpsStatus);
        tvCenterCount   = view.findViewById(R.id.tvCenterCount);

        setupMap();
        loadCenters();
        loadReports();

        view.findViewById(R.id.btnMyLocation).setOnClickListener(v -> centerOnMyLocation());
        view.findViewById(R.id.btnRefreshMap).setOnClickListener(v -> {
            loadCenters();
            loadReports();
            Toast.makeText(requireContext(), "Refreshing map data…", Toast.LENGTH_SHORT).show();
        });

        // Search
        TextInputEditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterCenters(s.toString().toLowerCase().trim());
            }
        });

        // Check for arguments
        if (getArguments() != null) {
            String targetId = getArguments().getString(ARG_CENTER_ID);
            boolean showDirs = getArguments().getBoolean(ARG_SHOW_DIRECTIONS);
            if (targetId != null) {
                centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
                    @Override public void onSuccess(List<EvacuationCenter> result) {
                        for (EvacuationCenter c : result) {
                            if (targetId.equals(c.getId())) {
                                requireActivity().runOnUiThread(() -> {
                                    selectCenter(c);
                                    if (showDirs) getDirections();
                                    if (c.getLatitude() != 0) {
                                        mapView.getController().animateTo(new GeoPoint(c.getLatitude(), c.getLongitude()), 15.0, 800L);
                                    }
                                });
                                break;
                            }
                        }
                    }
                    @Override public void onError(String msg) {}
                });
            }
        }

        return view;
    }

    // ── Map setup ─────────────────────────────────────────────────────────────

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getController().setZoom(12.0);
        mapView.getController().setCenter(new GeoPoint(14.1165, 122.9551)); // Daet

        // My-location overlay
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERM);
        }
    }

    private void enableMyLocation() {
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
        tvGpsStatus.setText("📍 Locating…");
    }

    // ── Load centers ──────────────────────────────────────────────────────────

    private void loadCenters() {
        centerRepo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override public void onSuccess(List<EvacuationCenter> result) {
                if (!isAdded()) return;
                centers = result;
                requireActivity().runOnUiThread(() -> {
                    if (result != null) {
                        tvCenterCount.setText(result.size() + " centers");
                        plotCenterMarkers(result);
                        
                        // Check if we should auto-select (only if NOT checked in)
                        SessionManager session = new SessionManager(requireContext());
                        String uid = session.getUserId();
                        if (uid != null && !result.isEmpty()) {
                            familyRepo.loadFamiliesByResident(uid, new FamilyRepository.Callback<List<Family>>() {
                                @Override public void onSuccess(List<Family> families) {
                                    boolean checkedIn = false;
                                    for (Family f : families) {
                                        if (f.getCenterId() != null && !f.getCenterId().isEmpty() && !"null".equals(f.getCenterId())) {
                                            checkedIn = true;
                                            break;
                                        }
                                    }
                                    if (!checkedIn) {
                                        requireActivity().runOnUiThread(() -> selectCenter(findNearest(result)));
                                    }
                                }
                                @Override public void onError(String msg) {
                                    requireActivity().runOnUiThread(() -> selectCenter(findNearest(result)));
                                }
                            });
                        } else if (!result.isEmpty()) {
                            selectCenter(findNearest(result));
                        }
                    }
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

    private void plotCenterMarkers(List<EvacuationCenter> list) {
        // Clear existing markers (distinguished by related object or tag if possible)
        // For OSMDroid, we can iterate and remove.
        List<org.osmdroid.views.overlay.Overlay> overlays = mapView.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i) instanceof Marker) {
                Marker m = (Marker) overlays.get(i);
                if (m.getRelatedObject() instanceof EvacuationCenter) {
                    overlays.remove(i);
                }
            }
        }

        BitmapDrawable shelterIcon     = makeShelterIcon(false);
        BitmapDrawable shelterIconFull = makeShelterIcon(true);
        for (EvacuationCenter c : list) {
            if (c.getLatitude() == 0) continue;
            final EvacuationCenter finalC = c; // Effectively final for lambda
            Marker m = new Marker(mapView);
            m.setPosition(new GeoPoint(c.getLatitude(), c.getLongitude()));
            m.setTitle(c.getName());
            m.setSnippet(c.getCapacityLabel() + " · " + (c.isFull() ? "FULL" : "Available"));
            m.setIcon(c.isFull() ? shelterIconFull : shelterIcon);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setRelatedObject(c); 
            m.setOnMarkerClickListener((marker, mv) -> {
                selectCenter(finalC);
                return true;
            });
            mapView.getOverlays().add(m);
        }
        mapView.invalidate();
    }

    private BitmapDrawable makeShelterIcon(boolean full) {
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int bodyColor  = full ? Color.parseColor("#D32F2F") : Color.parseColor("#1565C0");
        int roofColor  = full ? Color.parseColor("#B71C1C") : Color.parseColor("#0D47A1");
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Roof
        paint.setColor(roofColor); paint.setStyle(Paint.Style.FILL);
        Path roof = new Path();
        roof.moveTo(size / 2f, 4); roof.lineTo(size - 6, size * 0.48f); roof.lineTo(6, size * 0.48f); roof.close();
        canvas.drawPath(roof, paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        canvas.drawPath(roof, paint);
        // Body
        float bT = size * 0.44f, bL = size * 0.12f, bR = size * 0.88f, bB = size * 0.88f;
        paint.setColor(bodyColor); paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bL, bT, bR, bB, paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        canvas.drawRect(bL, bT, bR, bB, paint);
        // Door
        float dW = size * 0.22f, dH = size * 0.26f, dL = size / 2f - dW / 2f;
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(dL, bB - dH, dL + dW, bB, paint);
        // Stem
        paint.setColor(Color.parseColor("#80000000")); paint.setStyle(Paint.Style.FILL); paint.setStrokeWidth(3f);
        canvas.drawLine(size / 2f, bB, size / 2f, size - 2, paint);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void loadReports() {
        reportRepo.loadAll("all", null, new ReportRepository.Callback<List<IncidentReport>>() {
            @Override public void onSuccess(List<IncidentReport> result) {
                if (!isAdded()) return;
                reports = result;
                requireActivity().runOnUiThread(() -> {
                    plotReportMarkers(result);
                });
            }
            @Override public void onError(String message) {
                // Ignore silently or log
            }
        });
    }

    private void plotReportMarkers(List<IncidentReport> list) {
        List<org.osmdroid.views.overlay.Overlay> overlays = mapView.getOverlays();
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i) instanceof Marker) {
                Marker m = (Marker) overlays.get(i);
                if (m.getRelatedObject() instanceof IncidentReport) {
                    overlays.remove(i);
                }
            }
        }

        SessionManager session = new SessionManager(requireContext());
        boolean isOfficial = "official".equals(session.getRole());

        for (IncidentReport r : list) {
            if (r.getLatitude() == 0 || "resolved".equals(r.getStatus()) || r.isDuplicate()) continue;
            
            // For residents, don't show unverified (pending) reports
            if (!isOfficial && "pending".equals(r.getStatus())) continue;

            Marker m = new Marker(mapView);
            m.setPosition(new GeoPoint(r.getLatitude(), r.getLongitude()));
            m.setTitle(r.getTitle());
            String status = r.getStatus() != null ? r.getStatus().toUpperCase() : "PENDING";
            m.setSnippet(r.getTypeLabel() + " · " + status);
            m.setIcon(makeReportIcon(r));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setRelatedObject(r);
            m.setOnMarkerClickListener((marker, mv) -> {
                marker.showInfoWindow();
                return true;
            });
            mapView.getOverlays().add(m);
        }
        mapView.invalidate();
    }

    private BitmapDrawable makeReportIcon(IncidentReport r) {
        String emoji = r.getTypeEmoji();
        if (iconCache.containsKey(emoji)) return iconCache.get(emoji);

        int size = 80;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background circle
        paint.setColor(Color.parseColor("#FF9800")); // Orange for reports
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint);

        // White border
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint);

        // Text (Emoji)
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size * 0.45f);
        paint.setTextAlign(Paint.Align.CENTER);
        
        // Vertical alignment calculation
        Paint.FontMetrics fm = paint.getFontMetrics();
        float yOffset = (fm.descent + fm.ascent) / 2;
        canvas.drawText(emoji, size / 2f, size / 2f - yOffset, paint);

        // Stem
        paint.setColor(Color.parseColor("#80000000")); 
        paint.setStyle(Paint.Style.FILL); 
        paint.setStrokeWidth(3f);
        canvas.drawLine(size / 2f, size / 2f + size / 2.2f, size / 2f, size - 2, paint);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        iconCache.put(emoji, drawable);
        return drawable;
    }

    private void selectCenter(EvacuationCenter c) {
        selectedCenter = c;
        showCenterDetailsPopup(c);
    }

    private void showCenterDetailsPopup(EvacuationCenter c) {
        if (centerDialog != null && centerDialog.isShowing()) centerDialog.dismiss();

        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_center_details, null);

        TextView tvName = dv.findViewById(R.id.tvPopupCenterName);
        TextView tvDist = dv.findViewById(R.id.tvPopupCenterDistance);
        TextView tvStat = dv.findViewById(R.id.tvPopupCenterStatus);
        TextView tvOcc  = dv.findViewById(R.id.tvPopupCenterOccupancy);
        TextView tvAddr = dv.findViewById(R.id.tvPopupCenterAddress);

        tvName.setText(c.getName());
        tvAddr.setText(c.getAddress());
        tvStat.setText(c.isFull() ? "FULL" : "Available");
        tvStat.setBackgroundResource(c.isFull() ? R.drawable.badge_full_bg : R.drawable.badge_available_bg);
        tvOcc.setText(String.format("Capacity: %d | Occupied: %d (%s available)",
                c.getMaxCapacity(), c.getCurrentOccupancy(), c.getAvailableSlots()));

        // Distance from my location
        GeoPoint myLoc = getMyLocation();
        if (myLoc != null && c.getLatitude() != 0) {
            float[] r = new float[1];
            Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(),
                    c.getLatitude(), c.getLongitude(), r);
            tvDist.setText(String.format("📍 %s away · %s",
                    RoutingRepository.formatDistance(r[0]), c.getMunicipality()));
        } else {
            tvDist.setText("📍 " + c.getMunicipality());
        }

        dv.findViewById(R.id.btnPopupGetDirections).setOnClickListener(v -> {
            centerDialog.dismiss();
            getDirections();
        });
        dv.findViewById(R.id.btnPopupCheckIn).setOnClickListener(v -> checkInToSelectedCenter());
        dv.findViewById(R.id.btnPopupClose).setOnClickListener(v -> centerDialog.dismiss());

        centerDialog = new AlertDialog.Builder(requireContext())
                .setView(dv)
                .create();
        
        // Transparent background for rounded corners
        if (centerDialog.getWindow() != null) {
            centerDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        centerDialog.show();
    }

    private void checkInToSelectedCenter() {
        if (selectedCenter == null) return;
        if (selectedCenter.isFull()) {
            Toast.makeText(requireContext(), "This center is already full", Toast.LENGTH_SHORT).show();
            return;
        }

        SessionManager session = new SessionManager(requireContext());
        if (session.getUserId() == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Load Household Profile
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
                            if (selectedCenter.getId().equals(f.getCenterId())) alreadyHere = true;
                        }

                        if (alreadyHere) {
                            requireActivity().runOnUiThread(() -> 
                                Toast.makeText(requireContext(), "You are already checked in to " + selectedCenter.getName(), Toast.LENGTH_SHORT).show());
                            return;
                        }

                        if (totalMembers == 0) totalMembers = 1;
                        
                        final int count = totalMembers;
                        requireActivity().runOnUiThread(() -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Check In")
                                    .setMessage("Check " + count + " members into " + selectedCenter.getName() + "?")
                                    .setPositiveButton("Check In", (d, w) -> performCheckIn(selectedCenter, count))
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
        SessionManager session = new SessionManager(requireContext());
        centerRepo.checkInHousehold(center.getId(), session.getUserId(), memberCount, new CenterRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String msg;
                    switch (result) {
                        case "ALREADY_HERE":
                            msg = "You are already checked in to " + center.getName();
                            break;
                        case "TRANSFERRED":
                            msg = "Successfully transferred to " + center.getName();
                            loadCenters(); // Full refresh to update both old and new center UI
                            break;
                        default:
                            msg = "Successfully checked in " + memberCount + " members!";
                            center.setCurrentOccupancy(center.getCurrentOccupancy() + memberCount);
                            break;
                    }
                    
                    if (centerDialog != null && centerDialog.isShowing()) {
                        showCenterDetailsPopup(center); 
                    }
                    plotCenterMarkers(centers); 
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

    // ── Routing ───────────────────────────────────────────────────────────────

    private void getDirections() {
        if (selectedCenter == null) return;
        GeoPoint myLoc = getMyLocation();
        if (myLoc == null) {
            Toast.makeText(requireContext(), "Could not get your location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCenter.getLatitude() == 0) {
            Toast.makeText(requireContext(), "This center has no map coordinates", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Calculating fastest route…", Toast.LENGTH_SHORT).show();

        routingRepo.getRoute(
                myLoc.getLatitude(), myLoc.getLongitude(),
                selectedCenter.getLatitude(), selectedCenter.getLongitude(),
                new RoutingRepository.RouteCallback() {
                    @Override
                    public void onSuccess(double distMetres, double durSecs, List<double[]> polyline) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            drawRoute(polyline);
                            double adjustedDur = durSecs * 1.9;
                            Toast.makeText(requireContext(), 
                                    String.format("🚗 %s · ~%s to %s",
                                            RoutingRepository.formatDistance(distMetres),
                                            RoutingRepository.formatDuration(adjustedDur),
                                            selectedCenter.getName()), 
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> 
                            Toast.makeText(requireContext(), "Routing failed: " + message, Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void drawRoute(List<double[]> points) {
        // Remove previous route
        if (routeLine != null) mapView.getOverlays().remove(routeLine);

        routeLine = new Polyline();
        routeLine.setColor(Color.parseColor("#D32F2F"));
        routeLine.setWidth(8f);

        List<GeoPoint> geoPoints = new ArrayList<>();
        for (double[] pt : points) geoPoints.add(new GeoPoint(pt[0], pt[1]));
        routeLine.setPoints(geoPoints);

        // Insert at index 0 so it renders below markers
        mapView.getOverlays().add(0, routeLine);

        // Zoom map to fit the full route
        if (!geoPoints.isEmpty()) {
            BoundingBox box = BoundingBox.fromGeoPoints(geoPoints);
            mapView.post(() -> mapView.zoomToBoundingBox(box, true, 120));
        }
        mapView.invalidate();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void centerOnMyLocation() {
        GeoPoint loc = getMyLocation();
        if (loc != null) {
            mapView.getController().animateTo(loc, 15.0, 800L);
            tvGpsStatus.setText(String.format("📍 %.4f, %.4f",
                    loc.getLatitude(), loc.getLongitude()));
        } else {
            Toast.makeText(requireContext(),
                    "Location not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private GeoPoint getMyLocation() {
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            return myLocationOverlay.getMyLocation();
        }
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null;
        LocationManager lm = (LocationManager) requireContext()
                .getSystemService(android.content.Context.LOCATION_SERVICE);
        if (lm == null) return null;
        Location loc = null;
        try { loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        if (loc == null) {
            try { loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        }
        if (loc == null) {
            try { loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER); } catch (Exception ignored) {}
        }
        return loc != null ? new GeoPoint(loc.getLatitude(), loc.getLongitude()) : null;
    }

    private EvacuationCenter findNearest(List<EvacuationCenter> list) {
        GeoPoint myLoc = getMyLocation();
        if (myLoc == null || list == null || list.isEmpty()) return (list != null && !list.isEmpty()) ? list.get(0) : null;
        EvacuationCenter nearest = list.get(0);
        double minDist = Double.MAX_VALUE;
        for (EvacuationCenter c : list) {
            if (c.getLatitude() == 0) continue;
            float[] r = new float[1];
            Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(),
                    c.getLatitude(), c.getLongitude(), r);
            if (r[0] < minDist) { minDist = r[0]; nearest = c; }
        }
        return nearest;
    }

    private void filterCenters(String query) {
        if (centers == null || centers.isEmpty()) return;
        for (EvacuationCenter c : centers) {
            String name = c.getName() != null ? c.getName().toLowerCase() : "";
            String muni = c.getMunicipality() != null ? c.getMunicipality().toLowerCase() : "";
            if (name.contains(query) || muni.contains(query)) {
                selectCenter(c);
                if (c.getLatitude() != 0)
                    mapView.getController().animateTo(
                            new GeoPoint(c.getLatitude(), c.getLongitude()), 15.0, 600L);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERM
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    @Override public void onResume()  { super.onResume();  if (mapView != null) mapView.onResume(); }
    @Override public void onPause()   { super.onPause();   if (mapView != null) mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); if (routingRepo != null) routingRepo.shutdown(); }
}
