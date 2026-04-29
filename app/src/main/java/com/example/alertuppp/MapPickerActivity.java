package com.example.alertuppp;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen OSMDroid map picker restricted to Camarines Norte.
 *
 * - Tap anywhere on the map to drop/move the pin.
 * - Search box uses Nominatim geocoding, biased to Camarines Norte.
 * - Camera is clamped to the province bounding box.
 * - Confirm returns lat/lng to the caller.
 *
 * No API key required — OSMDroid uses OpenStreetMap tiles (free).
 * Nominatim is free; please set a descriptive User-Agent.
 */
public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "latitude";
    public static final String EXTRA_LNG = "longitude";

    // Camarines Norte bounding box
    private static final double BOUNDS_N = 14.55;
    private static final double BOUNDS_S = 13.60;
    private static final double BOUNDS_W = 122.30;
    private static final double BOUNDS_E = 123.35;
    private static final BoundingBox CAMNORTE_BOUNDS =
            new BoundingBox(BOUNDS_N, BOUNDS_E, BOUNDS_S, BOUNDS_W);

    // Default center: Daet
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(14.1165, 122.9551);
    private static final double   DEFAULT_ZOOM   = 12.0;

    private MapView   mapView;
    private Marker    pin;
    private TextView  tvCoords;
    private EditText  etSearch;

    private GeoPoint pickedPoint = DEFAULT_CENTER;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid requires a user-agent before setContentView
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map_picker);

        tvCoords = findViewById(R.id.tvCoords);
        etSearch = findViewById(R.id.etSearch);
        mapView  = findViewById(R.id.mapView);

        setupMap();
        setupSearch();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnConfirmLocation).setOnClickListener(v -> confirmLocation());
    }

    // ── Map setup ─────────────────────────────────────────────────────────────

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK); // OpenStreetMap standard tiles
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);
        mapView.setMinZoomLevel(9.0);   // can't zoom out past province
        mapView.setMaxZoomLevel(19.0);

        // Restrict scrollable area to Camarines Norte
        mapView.setScrollableAreaLimitDouble(CAMNORTE_BOUNDS);

        IMapController ctrl = mapView.getController();
        ctrl.setZoom(DEFAULT_ZOOM);
        ctrl.setCenter(DEFAULT_CENTER);

        // Drop initial pin at Daet
        pin = new Marker(mapView);
        pin.setPosition(DEFAULT_CENTER);
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        pin.setTitle("Evacuation Center Location");
        pin.setDraggable(true);
        pin.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDrag(Marker m) { updateCoordsLabel(m.getPosition()); }
            @Override public void onMarkerDragEnd(Marker m) {
                pickedPoint = m.getPosition();
                updateCoordsLabel(pickedPoint);
            }
            @Override public void onMarkerDragStart(Marker m) {}
        });
        mapView.getOverlays().add(pin);

        // Tap anywhere → move pin there
        mapView.getOverlays().add(new Overlay() {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e, MapView mv) {
                GeoPoint tapped = (GeoPoint) mv.getProjection()
                        .fromPixels((int) e.getX(), (int) e.getY());
                // Clamp to province
                double lat = Math.max(BOUNDS_S, Math.min(BOUNDS_N, tapped.getLatitude()));
                double lng = Math.max(BOUNDS_W, Math.min(BOUNDS_E, tapped.getLongitude()));
                pickedPoint = new GeoPoint(lat, lng);
                pin.setPosition(pickedPoint);
                mv.getController().animateTo(pickedPoint);
                updateCoordsLabel(pickedPoint);
                mv.invalidate();
                return true;
            }

            @Override
            public void draw(Canvas c, MapView mv, boolean shadow) {}
        });

        updateCoordsLabel(DEFAULT_CENTER);
    }

    private void updateCoordsLabel(GeoPoint pt) {
        tvCoords.setText(String.format(Locale.US,
                "📍  %.5f°N,  %.5f°E", pt.getLatitude(), pt.getLongitude()));
    }

    // ── Search (Nominatim) ────────────────────────────────────────────────────

    private void setupSearch() {
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });
        findViewById(R.id.btnSearch).setOnClickListener(v ->
                performSearch(etSearch.getText().toString().trim()));
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;
        hideKeyboard();

        executor.execute(() -> {
            try {
                // Nominatim — bias to Camarines Norte bounding box
                String encoded = URLEncoder.encode(
                        query + ", Camarines Norte, Philippines", "UTF-8");
                String url = "https://nominatim.openstreetmap.org/search"
                        + "?q=" + encoded
                        + "&format=json"
                        + "&limit=5"
                        + "&bounded=1"
                        + "&viewbox=" + BOUNDS_W + "," + BOUNDS_N
                        + "," + BOUNDS_E + "," + BOUNDS_S;

                String response = httpGet(url, "AlertUp-Android/" + getPackageName());
                JSONArray arr = new JSONArray(response);

                if (arr.length() == 0) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "No results found in Camarines Norte", Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONObject first = arr.getJSONObject(0);
                double lat = first.getDouble("lat");
                double lng = first.getDouble("lon");
                String displayName = first.optString("display_name", query);

                GeoPoint target = new GeoPoint(lat, lng);
                runOnUiThread(() -> {
                    pickedPoint = target;
                    pin.setPosition(target);
                    mapView.getController().animateTo(target, 16.0, 800L);
                    updateCoordsLabel(target);
                    mapView.invalidate();
                    // Show short name in search box
                    String shortName = displayName.contains(",")
                            ? displayName.substring(0, displayName.indexOf(",")) : displayName;
                    etSearch.setText(shortName);
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    private void confirmLocation() {
        Intent result = new Intent();
        result.putExtra(EXTRA_LAT, pickedPoint.getLatitude());
        result.putExtra(EXTRA_LNG, pickedPoint.getLongitude());
        setResult(RESULT_OK, result);
        finish();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { super.onPause();   mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String httpGet(String urlStr, String userAgent) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", userAgent);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null)
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }
}
