package com.example.alertuppp.network;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewsRepository {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class NewsArticle {
        public String title;
        public String description;
        public String source;
        public String url;

        public NewsArticle(String title, String description, String source, String url) {
            this.title = title;
            this.description = description;
            this.source = source;
            this.url = url;
        }
    }

    public void fetchGlobalNews(Callback<List<NewsArticle>> cb) {
        executor.execute(() -> {
            try {
                // Using Google News RSS for better reliability and real-time typhoon updates
                String query = "typhoon+philippines+weather";
                String rssUrl = "https://news.google.com/rss/search?q=" + query + "&hl=en-PH&gl=PH&ceid=PH:en";
                String apiUrl = "https://api.rss2json.com/v1/api.json?rss_url=" + java.net.URLEncoder.encode(rssUrl, "UTF-8");
                
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // Add User-Agent to avoid being blocked
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    cb.onError("Server returned code: " + responseCode);
                    return;
                }
                
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                if (!root.getString("status").equals("ok")) {
                    cb.onError("API Status Error: " + root.optString("message"));
                    return;
                }

                org.json.JSONArray items = root.getJSONArray("items");
                List<NewsArticle> articles = new ArrayList<>();
                for (int i = 0; i < Math.min(items.length(), 4); i++) {
                    org.json.JSONObject item = items.getJSONObject(i);
                    String title = item.optString("title");
                    // Google News titles often have " - Source" at the end, let's clean it
                    if (title.contains(" - ")) title = title.substring(0, title.lastIndexOf(" - "));
                    
                    articles.add(new NewsArticle(
                        title,
                        item.optString("description").replaceAll("<[^>]*>", "").trim(),
                        item.optString("author", "National News"),
                        item.optString("link")
                    ));
                }
                
                cb.onSuccess(articles);
            } catch (Exception e) {
                cb.onError("Connection Error: Check internet connection.");
                e.printStackTrace();
            }
        });
    }

    public void shutdown() { executor.shutdownNow(); }
}
