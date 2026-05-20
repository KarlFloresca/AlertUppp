package com.example.alertuppp.network;

import android.content.Context;
import com.example.alertuppp.model.Announcement;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnnouncementRepository {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final SupabaseClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AnnouncementRepository(Context ctx) {
        this.client = new SupabaseClient(ctx);
    }

    public void loadRecent(Callback<List<Announcement>> cb) {
        executor.execute(() -> {
            try {
                // Fetch top 5 announcements ordered by newest
                String json = client.get("announcements", "select=*&order=created_at.desc&limit=5");
                cb.onSuccess(parseList(json));
            } catch (IOException | JSONException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    private List<Announcement> parseList(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<Announcement> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            Announcement a = new Announcement();
            a.setId(obj.optString("id"));
            a.setTitle(obj.optString("title"));
            a.setBody(obj.optString("body"));
            a.setPostedBy(obj.optString("posted_by"));
            a.setCreatedAt(obj.optString("created_at"));
            list.add(a);
        }
        return list;
    }
}
