package com.example.alertuppp.model;

public class HouseholdProfile {

    private String id;
    private String headResidentId;
    private String residentId;
    private String householdName;
    private String address;
    private String barangay;
    private String municipality;
    private String houseType;        // Concrete / Wood / Mixed / Makeshift
    private boolean nearFloodZone;
    private boolean nearLandslideZone;
    private String createdAt;
    private String updatedAt;

    public HouseholdProfile() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHeadResidentId() { return headResidentId; }
    public void setHeadResidentId(String headResidentId) { this.headResidentId = headResidentId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getHouseholdName() { return householdName; }
    public void setHouseholdName(String householdName) { this.householdName = householdName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getBarangay() { return barangay; }
    public void setBarangay(String barangay) { this.barangay = barangay; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public String getHouseType() { return houseType; }
    public void setHouseType(String houseType) { this.houseType = houseType; }

    public boolean isNearFloodZone() { return nearFloodZone; }
    public void setNearFloodZone(boolean nearFloodZone) { this.nearFloodZone = nearFloodZone; }

    public boolean isNearLandslideZone() { return nearLandslideZone; }
    public void setNearLandslideZone(boolean nearLandslideZone) { this.nearLandslideZone = nearLandslideZone; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
