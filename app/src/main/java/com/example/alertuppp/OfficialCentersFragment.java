package com.example.alertuppp;

import android.Manifest;
import android.content.Intent;
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alertuppp.adapter.OfficialCenterAdapter;
import com.example.alertuppp.model.EvacuationCenter;
import com.example.alertuppp.network.CenterRepository;
import com.example.alertuppp.network.RoutingRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
import java.util.Locale;

public class OfficialCentersFragment extends Fragment {

    private static final int LOCATION_PERM = 201;

    private static final String[] MUNICIPALITIES = {
            "Capalonga", "Daet", "Jose Panganiban", "Labo", "Mercedes",
            "Paracale", "San Lorenzo Ruiz", "San Vicente", "Santa Elena",
            "Talisay", "Vinzons"
    };

    // Repos
    private CenterRepository repo;
    private RoutingRepository routingRepo;
    private SessionManager session;

    // Data
    private List<EvacuationCenter> allCenters = new ArrayList<>();
    private EvacuationCenter selectedCenter;

    // List pane
    private OfficialCenterAdapter adapter;
    private RecyclerView rvCenters;
    private TextView tvEmpty, tvCentersCount;

    // Map pane
    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private Polyline routeLine;
    private TextView tvMapCenterName, tvMapCenterInfo;
    private FrameLayout paneList;
    private ConstraintLayout paneMap;
    private TextView tabList, tabMap;

    // Edit dialog state
    private double pickedLat = 0, pickedLng = 0;
    private TextView tvPickedLocation;
    private AlertDialog editDialog;
    private EvacuationCenter editingCenter; // null = add new

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK
                                && result.getData() != null) {
                            pickedLat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0);
                            pickedLng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0);
                            if (tvPickedLocation != null) {
                                tvPickedLocation.setText(String.format(Locale.US,
                                        "✅  %.5f°N,  %.5f°E", pickedLat, pickedLng));
                                tvPickedLocation.setTextColor(0xFF388E3C);
                            }
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        View view = inflater.inflate(R.layout.fragment_official_centers, container, false);

        repo       = new CenterRepository(requireContext());
        routingRepo = new RoutingRepository();
        session    = new SessionManager(requireContext());

        // List pane
        paneList       = view.findViewById(R.id.paneList);
        paneMap        = view.findViewById(R.id.paneMap);
        rvCenters      = view.findViewById(R.id.rvCenters);
        tvEmpty        = view.findViewById(R.id.tvEmpty);
        tvCentersCount = view.findViewById(R.id.tvCentersCount);

        // Map pane
        mapView         = view.findViewById(R.id.mapView);

        // Tabs
        tabList = view.findViewById(R.id.tabList);
        tabMap  = view.findViewById(R.id.tabMap);

        setupSearchAndFilters(view);
        setupAdapter();
        setupMap();
        loadCenters();

        view.findViewById(R.id.fabAddCenter).setOnClickListener(v -> showEditDialog(null));

        tabList.setOnClickListener(v -> showPane(true));
        tabMap.setOnClickListener(v -> showPane(false));

        return view;
    }

    // ── Search & Filters ──────────────────────────────────────────────────────

    private String currentSearchQuery = "";
    private String currentStatusFilter = "All"; // All, Available, Full, Closed

    private void setupSearchAndFilters(View view) {
        android.widget.EditText etSearch = view.findViewById(R.id.etSearchCenters);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        View fAll = view.findViewById(R.id.filterAll);
        View fOpen = view.findViewById(R.id.filterOpen);
        View fFull = view.findViewById(R.id.filterFull);
        View fClosed = view.findViewById(R.id.filterClosed);

        View.OnClickListener filterClick = v -> {
            // Reset all
            fAll.setBackgroundResource(R.drawable.bg_chip_unselected);
            ((TextView)fAll).setTextColor(0xFF757575);
            fOpen.setBackgroundResource(R.drawable.bg_chip_unselected);
            ((TextView)fOpen).setTextColor(0xFF757575);
            fFull.setBackgroundResource(R.drawable.bg_chip_unselected);
            ((TextView)fFull).setTextColor(0xFF757575);
            fClosed.setBackgroundResource(R.drawable.bg_chip_unselected);
            ((TextView)fClosed).setTextColor(0xFF757575);

            // Select clicked
            v.setBackgroundResource(R.drawable.bg_chip_selected);
            ((TextView)v).setTextColor(Color.WHITE);

            if (v.getId() == R.id.filterAll) currentStatusFilter = "All";
            else if (v.getId() == R.id.filterOpen) currentStatusFilter = "Available";
            else if (v.getId() == R.id.filterFull) currentStatusFilter = "Full";
            else if (v.getId() == R.id.filterClosed) currentStatusFilter = "Closed";

            applyFilters();
        };

        fAll.setOnClickListener(filterClick);
        fOpen.setOnClickListener(filterClick);
        fFull.setOnClickListener(filterClick);
        fClosed.setOnClickListener(filterClick);
    }

    private void applyFilters() {
        List<EvacuationCenter> filtered = new ArrayList<>();
        for (EvacuationCenter c : allCenters) {
            boolean matchesSearch = TextUtils.isEmpty(currentSearchQuery) ||
                    c.getName().toLowerCase().contains(currentSearchQuery) ||
                    c.getMunicipality().toLowerCase().contains(currentSearchQuery) ||
                    c.getAddress().toLowerCase().contains(currentSearchQuery);

            boolean matchesStatus = "All".equals(currentStatusFilter);
            if (!matchesStatus) {
                if ("Available".equals(currentStatusFilter)) {
                    matchesStatus = !"closed".equalsIgnoreCase(c.getStatus()) && !c.isFull();
                } else if ("Full".equals(currentStatusFilter)) {
                    matchesStatus = c.isFull();
                } else if ("Closed".equals(currentStatusFilter)) {
                    matchesStatus = "closed".equalsIgnoreCase(c.getStatus());
                }
            }

            if (matchesSearch && matchesStatus) {
                filtered.add(c);
            }
        }
        adapter.setData(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(filtered.isEmpty() ? "No centers match your search/filter." : "");
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private boolean mapInitialized = false;

    private void showPane(boolean listVisible) {
        paneList.setVisibility(listVisible ? View.VISIBLE : View.GONE);
        paneMap.setVisibility(listVisible ? View.GONE : View.VISIBLE);

        tabList.setBackgroundColor(listVisible ? Color.WHITE : Color.TRANSPARENT);
        tabList.setTextColor(listVisible ? getResources().getColor(R.color.primary) : 0xFF757575);
        
        tabMap.setBackgroundColor(listVisible ? Color.TRANSPARENT : Color.WHITE);
        tabMap.setTextColor(listVisible ? 0xFF757575 : getResources().getColor(R.color.primary));

        if (!listVisible && mapView != null) {
            mapView.onResume();
            mapView.post(() -> {
                mapView.invalidate();
                if (!mapInitialized) {
                    mapInitialized = true;
                    mapView.getController().setZoom(12.0);
                    mapView.getController().setCenter(new GeoPoint(14.1165, 122.9551));
                    plotMarkers(allCenters);
                }
            });
        }
    }

    private void setupAdapter() {
        adapter = new OfficialCenterAdapter(new OfficialCenterAdapter.Listener() {
            @Override public void onEdit(EvacuationCenter c)      { showEditDialog(c); }
            @Override public void onDelete(EvacuationCenter c)    { confirmDelete(c); }
            @Override public void onViewOnMap(EvacuationCenter c) { focusOnMap(c); }
        });
        rvCenters.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCenters.setAdapter(adapter);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void loadCenters() {
        repo.loadAll(new CenterRepository.Callback<List<EvacuationCenter>>() {
            @Override public void onSuccess(List<EvacuationCenter> centers) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allCenters = centers;
                    applyFilters(); // Use filtered data
                    tvCentersCount.setText(centers.size() + " centers");
                    plotMarkers(centers);
                });
            }
            @Override public void onError(String msg) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Load failed: " + msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ── Map ───────────────────────────────────────────────────────────────────

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);
        mapView.getController().setZoom(12.0);
        mapView.getController().setCenter(new GeoPoint(14.1165, 122.9551));

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERM);
        }
    }

    private void enableMyLocation() {
        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
    }

    private void plotMarkers(List<EvacuationCenter> list) {
        // Remove old center markers (keep myLocation overlay and route line)
        mapView.getOverlays().removeIf(o -> o instanceof Marker);
        if (myLocationOverlay != null && !mapView.getOverlays().contains(myLocationOverlay))
            mapView.getOverlays().add(myLocationOverlay);

        BitmapDrawable shelterIcon = makeShelterIcon(false);
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

    /**
     * Draws a shelter/house icon as a Bitmap:
     * - triangular roof on top
     * - rectangular body below
     * - small door cutout
     * Green = available, Red = full
     */
    private BitmapDrawable makeShelterIcon(boolean full) {
        int size = 96; // px
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int bodyColor  = full ? Color.parseColor("#D32F2F") : Color.parseColor("#1565C0");
        int roofColor  = full ? Color.parseColor("#B71C1C") : Color.parseColor("#0D47A1");
        int doorColor  = Color.parseColor("#FFFFFF");
        int strokeColor = Color.parseColor("#FFFFFF");

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── Roof (triangle) ──
        paint.setColor(roofColor);
        paint.setStyle(Paint.Style.FILL);
        Path roof = new Path();
        roof.moveTo(size / 2f, 4);          // apex
        roof.lineTo(size - 6, size * 0.48f); // right eave
        roof.lineTo(6, size * 0.48f);        // left eave
        roof.close();
        canvas.drawPath(roof, paint);

        // roof outline
        paint.setColor(strokeColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        canvas.drawPath(roof, paint);

        // ── Body (rectangle) ──
        float bodyTop = size * 0.44f;
        float bodyLeft = size * 0.12f;
        float bodyRight = size * 0.88f;
        float bodyBottom = size * 0.88f;

        paint.setColor(bodyColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bodyLeft, bodyTop, bodyRight, bodyBottom, paint);

        paint.setColor(strokeColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        canvas.drawRect(bodyLeft, bodyTop, bodyRight, bodyBottom, paint);

        // ── Door ──
        float doorW = size * 0.22f;
        float doorH = size * 0.26f;
        float doorLeft = size / 2f - doorW / 2f;
        float doorTop  = bodyBottom - doorH;
        paint.setColor(doorColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(doorLeft, doorTop, doorLeft + doorW, bodyBottom, paint);

        // ── Drop shadow pin stem ──
        paint.setColor(Color.parseColor("#80000000"));
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3f);
        canvas.drawLine(size / 2f, bodyBottom, size / 2f, size - 2, paint);

        return new BitmapDrawable(getResources(), bmp);
    }

    private void focusOnMap(EvacuationCenter c) {
        showPane(false);
        selectCenter(c);
        if (c.getLatitude() != 0)
            mapView.getController().animateTo(new GeoPoint(c.getLatitude(), c.getLongitude()), 15.0, 600L);
    }

    private void selectCenter(EvacuationCenter c) {
        selectedCenter = c;
        
        View dv = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_center_details_premium, null);

        TextView tvName = dv.findViewById(R.id.tvDialogCenterName);
        TextView tvStatus = dv.findViewById(R.id.tvDialogCenterStatus);
        TextView tvMuni = dv.findViewById(R.id.tvDialogMunicipality);
        TextView tvAddr = dv.findViewById(R.id.tvDialogAddress);
        TextView tvCap = dv.findViewById(R.id.tvDialogCapacityInfo);
        MaterialButton btnEdit = dv.findViewById(R.id.btnDialogEdit);

        tvName.setText(c.getName());
        tvMuni.setText(c.getMunicipality());
        tvAddr.setText(c.getAddress());
        tvCap.setText(String.format(Locale.US, "%d / %d occupants", c.getCurrentOccupancy(), c.getMaxCapacity()));

        if (c.isFull()) {
            tvStatus.setText("🔴 FULL");
            tvStatus.setTextColor(0xFFD32F2F);
            tvStatus.setBackgroundResource(R.drawable.bg_status_missing);
        } else {
            tvStatus.setText("🟢 AVAILABLE");
            tvStatus.setTextColor(0xFF388E3C);
            tvStatus.setBackgroundResource(R.drawable.bg_status_safe);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dv)
                .create();

        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            showEditDialog(c);
        });

        dialog.show();
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    private void getRoute() {
        if (selectedCenter == null) {
            Toast.makeText(requireContext(), "Tap a center pin first", Toast.LENGTH_SHORT).show();
            return;
        }
        GeoPoint myLoc = getMyLocation();
        if (myLoc == null) {
            Toast.makeText(requireContext(), "Location not available yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCenter.getLatitude() == 0) {
            Toast.makeText(requireContext(), "Center has no coordinates", Toast.LENGTH_SHORT).show();
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
                            tvMapCenterInfo.setText(String.format(
                                    "🚗 %s · %s · %s",
                                    RoutingRepository.formatDistance(distMetres),
                                    RoutingRepository.formatDuration(durSecs),
                                    selectedCenter.getMunicipality()));
                            drawRoute(polyline);
                        });
                    }
                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "Routing failed: " + message, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void drawRoute(List<double[]> points) {
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

    // ── Edit / Add dialog ─────────────────────────────────────────────────────

    private void showEditDialog(@Nullable EvacuationCenter center) {
        editingCenter = center;
        pickedLat = center != null ? center.getLatitude() : 0;
        pickedLng = center != null ? center.getLongitude() : 0;

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
        tvPickedLocation            = dv.findViewById(R.id.tvPickedLocation);

        // Pre-fill when editing
        if (center != null) {
            etName.setText(center.getName());
            etAddr.setText(center.getAddress());
            spMuni.setText(center.getMunicipality(), false);
            etCap.setText(String.valueOf(center.getMaxCapacity()));
            if (center.getLatitude() != 0) {
                tvPickedLocation.setText(String.format(Locale.US,
                        "✅  %.5f°N,  %.5f°E", center.getLatitude(), center.getLongitude()));
                tvPickedLocation.setTextColor(0xFF388E3C);
            }
        }

        spMuni.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, MUNICIPALITIES));

        dv.findViewById(R.id.btnPickOnMap).setOnClickListener(v ->
                mapPickerLauncher.launch(new Intent(requireContext(), MapPickerActivity.class)));

        String title = center == null ? "Add Evacuation Center" : "Edit Center";
        editDialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(dv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> editDialog = null)
                .create();

        editDialog.setOnShowListener(d -> {
            editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name    = txt(etName);
                String addr    = txt(etAddr);
                String muni    = spMuni.getText().toString().trim();
                String capStr  = txt(etCap);

                tilName.setError(null); tilAddr.setError(null);
                tilMuni.setError(null); tilCap.setError(null);

                if (TextUtils.isEmpty(name))  { tilName.setError("Required"); return; }
                if (TextUtils.isEmpty(addr))  { tilAddr.setError("Required"); return; }
                if (TextUtils.isEmpty(muni))  { tilMuni.setError("Required"); return; }
                if (TextUtils.isEmpty(capStr)){ tilCap.setError("Required");  return; }

                int capacity;
                try { capacity = Integer.parseInt(capStr); }
                catch (NumberFormatException e) { tilCap.setError("Enter a valid number"); return; }
                if (capacity <= 0) { tilCap.setError("Must be > 0"); return; }

                if (pickedLat == 0 && pickedLng == 0) {
                    Toast.makeText(requireContext(),
                            "Pick a location on the map first", Toast.LENGTH_SHORT).show();
                    return;
                }

                editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Saving…");

                if (editingCenter == null) {
                    // Add new
                    EvacuationCenter nc = new EvacuationCenter();
                    nc.setName(name); nc.setAddress(addr); nc.setMunicipality(muni);
                    nc.setMaxCapacity(capacity);
                    nc.setLatitude(pickedLat); nc.setLongitude(pickedLng);
                    repo.addCenter(nc, session.getUserId(), new CenterRepository.Callback<EvacuationCenter>() {
                        @Override public void onSuccess(EvacuationCenter saved) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                editDialog.dismiss(); editDialog = null;
                                Toast.makeText(requireContext(),
                                        "Center added: " + saved.getName(), Toast.LENGTH_LONG).show();
                                loadCenters();
                            });
                        }
                        @Override public void onError(String msg) { handleSaveError(msg); }
                    });
                } else {
                    // Update existing
                    editingCenter.setName(name); editingCenter.setAddress(addr);
                    editingCenter.setMunicipality(muni); editingCenter.setMaxCapacity(capacity);
                    editingCenter.setLatitude(pickedLat); editingCenter.setLongitude(pickedLng);
                    repo.updateCenter(editingCenter, new CenterRepository.Callback<Void>() {
                        @Override public void onSuccess(Void r) {
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(() -> {
                                editDialog.dismiss(); editDialog = null;
                                Toast.makeText(requireContext(),
                                        "Center updated", Toast.LENGTH_SHORT).show();
                                loadCenters();
                            });
                        }
                        @Override public void onError(String msg) { handleSaveError(msg); }
                    });
                }
            });
        });

        editDialog.show();
    }

    private void handleSaveError(String msg) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (editDialog != null) {
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Save");
            }
            Toast.makeText(requireContext(), "Failed: " + msg, Toast.LENGTH_LONG).show();
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void confirmDelete(EvacuationCenter c) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Center")
                .setMessage("Delete \"" + c.getName() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        repo.deleteCenter(c, new CenterRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(),
                                            "Deleted: " + c.getName(), Toast.LENGTH_SHORT).show();
                                    loadCenters();
                                });
                            }
                            @Override public void onError(String msg) {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(),
                                                "Delete failed: " + msg, Toast.LENGTH_SHORT).show());
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private GeoPoint getMyLocation() {
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null)
            return myLocationOverlay.getMyLocation();
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

    private String txt(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERM && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    @Override public void onResume()  { super.onResume();  if (mapView != null) mapView.onResume(); }
    @Override public void onPause()   { super.onPause();   if (mapView != null) mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); if (routingRepo != null) routingRepo.shutdown(); }
}
