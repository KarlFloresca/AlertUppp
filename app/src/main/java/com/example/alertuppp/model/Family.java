package com.example.alertuppp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a family_registration row — one family unit registered
 * at a specific evacuation center under a household.
 */
public class Family {
    private String id;
    private String householdId;
    private String residentId;
    private String familyName;   // user-given name, e.g. "Dela Cruz Family"
    private String centerId;     // nullable — assigned when checking in
    private String centerName;
    private String registeredAt;
    private List<FamilyMember> members;

    public Family() { members = new ArrayList<>(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseholdId() { return householdId; }
    public void setHouseholdId(String householdId) { this.householdId = householdId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getCenterId() { return centerId; }
    public void setCenterId(String centerId) { this.centerId = centerId; }

    public String getCenterName() { return centerName; }
    public void setCenterName(String centerName) { this.centerName = centerName; }

    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }

    public List<FamilyMember> getMembers() { return members; }
    public void setMembers(List<FamilyMember> members) { this.members = members != null ? members : new ArrayList<>(); }

    public int getMemberCount() { return members != null ? members.size() : 0; }

    public void addMember(FamilyMember m) { if (members == null) members = new ArrayList<>(); members.add(m); }
    public void removeMember(int i) { if (members != null && i >= 0 && i < members.size()) members.remove(i); }
}
