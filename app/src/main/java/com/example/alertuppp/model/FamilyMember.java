package com.example.alertuppp.model;

/**
 * Represents a family_members row — one person in a family registration.
 */
public class FamilyMember {
    private String id;
    private String registrationId;
    private String fullName;
    private int age;
    private String notes;

    public FamilyMember() {}
    public FamilyMember(String fullName, int age) { this.fullName = fullName; this.age = age; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRegistrationId() { return registrationId; }
    public void setRegistrationId(String registrationId) { this.registrationId = registrationId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
