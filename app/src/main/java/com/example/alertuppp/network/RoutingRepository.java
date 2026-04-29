package com.example.alertuppp.network;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free routing via the public OSRM demo server.
 * Uses the "driving" profile — suitable for evacuation routing.
 *
 * No API key required.
 * For production, host your own OSRM instance or use a paid provider.
 */
public class RoutingRepository {

    public interface RouteCallback {
        /** @param distanceMetres  straight-line or routed distance in metres
         *  @param durationSeconds estimated travel time in seconds
         *  @param polyline        list of lat/lng points for drawing the route line */
        void onSuccess(double distanceMetres, double durationSeconds,
                       List<double[]> polyline);
        void onError(String message);
    }

    private static final String OSRM_BASE =
            "https://router.project-osrm.org/route/v1/driving/";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Get a driving route between two points.
     *
     * @param fromLat  origin latitude
     * @param fromLng  origin longitude
     * @param toLat    destination latitude
     * @param toLng    destination longitude
     */
    public void getRoute(double fromLat, double fromLng,
                         double toLat,   double toLng,
                         RouteCallback cb) {
        executor.execute(() -> {
            try {
                // OSRM expects lng,lat order
                String url = OSRM_BASE
                        + fromLng + "," + fromLat + ";"
                        + toLng   + "," + toLat
                        + "?overview=full&geometries=geojson&steps=false";

                String response = httpGet(url);
                JSONObject json = new JSONObject(response);

                String code = json.optString("code", "");
                if (!"Ok".equals(code)) {
                    cb.onError("OSRM error: " + json.optString("message", code));
                    return;
                }

                JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                double distance = route.getDouble("distance"); // metres
                double duration = route.getDouble("duration"); // seconds

                // Decode GeoJSON polyline into list of [lat, lng] pairs
                JSONArray coords = route
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<double[]> polyline = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray pt = coords.getJSONArray(i);
                    // GeoJSON is [lng, lat]
                    polyline.add(new double[]{pt.getDouble(1), pt.getDouble(0)});
                }

                cb.onSuccess(distance, duration, polyline);

            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        });
    }

    /** Convenience: format metres as "X.X km" or "X m". */
    public static String formatDistance(double metres) {
        if (metres >= 1000) return String.format("%.1f km", metres / 1000.0);
        return String.format("%.0f m", metres);
    }

    /** Convenience: format seconds as "X min" or "X hr Y min". */
    public static String formatDuration(double seconds) {
        int mins = (int) (seconds / 60);
        if (mins < 60) return mins + " min";
        return (mins / 60) + " hr " + (mins % 60) + " min";
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "AlertUp-Android");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
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

    public void shutdown() { executor.shutdownNow(); }
}
