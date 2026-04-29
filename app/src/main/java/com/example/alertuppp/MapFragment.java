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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.network.CenterRepository;
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
import java.util.List;

public class MapFragment extends Fragment {

    private static final int LOCATION_PERM = 102;

    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private CenterRepository centerRepo;
    private RoutingRepository routingRepo;
    private List<EvacuationCenter> centers = new ArrayList<>();
    private EvacuationCenter selectedCenter;
    private Polyline routeLine;

    // Bottom sheet views
    private TextView tvCenterName, tvCenterDistance, tvCenterStatus,
            tvCenterContact, tvCenterCount, tvGpsStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View view = inflater.inflate(R.layout.fragment_map, container, false);

        centerRepo  = new CenterRepository(requireContext());
        routingRepo = new RoutingRepository();

        mapView         = view.findViewById(R.id.mapView);
        tvGpsStatus     = view.findViewById(R.id.tvGpsStatus);
        tvCenterName    = view.findViewById(R.id.tvMapCenterName);
        tvCenterDistance = view.findViewById(R.id.tvMapCenterDistance);
        tvCenterStatus  = view.findViewById(R.id.tvMapCenterStatus);
        tvCenterContact = view.findViewById(R.id.tvMapCenterContact);
        tvCenterCount   = view.findViewById(R.id.tvCenterCount);

        setupMap();
        loadCenters();

        view.findViewById(R.id.btnMyLocation).setOnClickListener(v -> centerOnMyLocation());
        view.findViewById(R.id.btnGetDirections).setOnClickListener(v -> getDirections());
        view.findViewById(R.id.btnCheckInMap).setOnClickListener(v -> {
            if (selectedCenter != null)
                Toast.makeText(requireContext(),
                        "Checked in to " + selectedCenter.getName(), Toast.LENGTH_SHORT).show();
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
                    tvCenterCount.setText(result.size() + " centers");
                    plotCenterMarkers(result);
                    if (!result.isEmpty()) {
                        selectCenter(findNearest(result));
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
        BitmapDrawable shelterIcon     = makeShelterIcon(false);
        BitmapDrawable shelterIconFull = makeShelterIcon(true);
        for (EvacuationCenter c : list) {
            if (c.getLatitude() == 0) continue;
            Marker m = new Marker(mapView);
            m.setPosition(new GeoPoint(c.getLatitude(), c.getLongitude()));
            m.setTitle(c.getName());
            m.setSnippet(c.getCapacityLabel() + " · " + (c.isFull() ? "FULL" : "Available"));
            m.setIcon(c.isFull() ? shelterIconFull : shelterIcon);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setOnMarkerClickListener((marker, mv) -> {
                selectCenter(c);
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

    private void selectCenter(EvacuationCenter c) {
        selectedCenter = c;
        tvCenterName.setText(c.getName());
        tvCenterStatus.setText(c.isFull() ? "FULL" : "Available");
        tvCenterStatus.setBackgroundResource(
                c.isFull() ? R.drawable.badge_full_bg : R.drawable.badge_available_bg);
        tvCenterContact.setText("");

        // Distance from my location
        GeoPoint myLoc = getMyLocation();
        if (myLoc != null && c.getLatitude() != 0) {
            float[] r = new float[1];
            Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(),
                    c.getLatitude(), c.getLongitude(), r);
            tvCenterDistance.setText(String.format("📍 %s away · %s",
                    RoutingRepository.formatDistance(r[0]), c.getMunicipality()));
        } else {
            tvCenterDistance.setText("📍 " + c.getMunicipality());
        }
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private void getDirections() {
        if (selectedCenter == null) return;
        GeoPoint myLoc = getMyLocation();
        if (myLoc == null) {
            Toast.makeText(requireContext(),
                    "Could not get your location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCenter.getLatitude() == 0) {
            Toast.makeText(requireContext(),
                    "This center has no map coordinates", Toast.LENGTH_SHORT).show();
            return;
        }

        tvCenterDistance.setText("🔄 Calculating route…");

        routingRepo.getRoute(
                myLoc.getLatitude(), myLoc.getLongitude(),
                selectedCenter.getLatitude(), selectedCenter.getLongitude(),
                new RoutingRepository.RouteCallback() {
                    @Override
                    public void onSuccess(double distMetres, double durSecs,
                                          List<double[]> polyline) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            // Update distance label with routed distance + ETA
                            tvCenterDistance.setText(String.format(
                                    "🚗 %s · %s · %s",
                                    RoutingRepository.formatDistance(distMetres),
                                    RoutingRepository.formatDuration(durSecs),
                                    selectedCenter.getMunicipality()));

                            // Draw route polyline on map
                            drawRoute(polyline);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            tvCenterDistance.setText("📍 " + selectedCenter.getMunicipality());
                            Toast.makeText(requireContext(),
                                    "Routing failed: " + message, Toast.LENGTH_SHORT).show();
                        });
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
        if (myLoc == null || list.isEmpty()) return list.get(0);
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
        if (centers.isEmpty()) return;
        for (EvacuationCenter c : centers) {
            if (c.getName().toLowerCase().contains(query)
                    || c.getMunicipality().toLowerCase().contains(query)) {
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
