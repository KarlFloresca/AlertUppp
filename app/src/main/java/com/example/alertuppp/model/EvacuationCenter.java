package com.example.alertuppp.model;

public class EvacuationCenter {
    private String id;
    private String name;
    private String address;
    private String municipality;
    private double latitude;
    private double longitude;
    private int maxCapacity;
    private int currentOccupancy;
    private String status; // "available" | "full" | "closed"
    private boolean hasWater;
    private boolean hasFood;
    private boolean hasMedical;

    public EvacuationCenter() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }

    public int getCurrentOccupancy() { return currentOccupancy; }
    public void setCurrentOccupancy(int currentOccupancy) { this.currentOccupancy = currentOccupancy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isHasWater() { return hasWater; }
    public void setHasWater(boolean hasWater) { this.hasWater = hasWater; }

    public boolean isHasFood() { return hasFood; }
    public void setHasFood(boolean hasFood) { this.hasFood = hasFood; }

    public boolean isHasMedical() { return hasMedical; }
    public void setHasMedical(boolean hasMedical) { this.hasMedical = hasMedical; }

    public boolean isFull() { return "full".equals(status) || currentOccupancy >= maxCapacity; }

    public int getAvailableSlots() { return Math.max(0, maxCapacity - currentOccupancy); }

    public float getOccupancyPercent() {
        if (maxCapacity == 0) return 0f;
        return (float) currentOccupancy / maxCapacity;
    }

    public String getCapacityLabel() { return currentOccupancy + "/" + maxCapacity; }
}
