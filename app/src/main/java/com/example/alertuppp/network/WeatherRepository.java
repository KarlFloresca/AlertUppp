package com.example.alertuppp.network;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherRepository {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class WeatherData {
        public String temp;
        public String description;
        public String city;

        public WeatherData(String temp, String description, String city) {
            this.temp = temp;
            this.description = description;
            this.city = city;
        }
    }

    public void fetchWeather(String city, Callback<WeatherData> cb) {
        executor.execute(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1500);
                
                // Mock data for Daet, Camarines Norte
                // In a real app, you would use:
                // String json = client.get("https://api.openweathermap.org/data/2.5/weather?q=" + city + "&appid=YOUR_KEY");
                
                WeatherData mock = new WeatherData("29°C", "Sunny Intervals", "Daet");
                cb.onSuccess(mock);
            } catch (InterruptedException e) {
                cb.onError(e.getMessage());
            }
        });
    }

    public void shutdown() { executor.shutdownNow(); }
}
