package com.example.alertuppp.model;

public class IncidentReport {
    private String id;
    private String submittedBy;
    private String reportType; // flood | road | damage | unsafe | missing | rescue | medical | supply
    private String title;
    private String description;
    private String floodLevel;
    private double latitude;
    private double longitude;
    private String landmark;
    private String photoUrl;
    private String status; // pending | verified | ongoing | resolved
    private String assignedTeam;
    private String createdAt;
    private boolean isDuplicate;
    private String parentReportId;
    private int duplicateCount = 0;

    public IncidentReport() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFloodLevel() { return floodLevel; }
    public void setFloodLevel(String floodLevel) { this.floodLevel = floodLevel; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(String assignedTeam) { this.assignedTeam = assignedTeam; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isDuplicate() { return isDuplicate; }
    public void setDuplicate(boolean duplicate) { isDuplicate = duplicate; }

    public String getParentReportId() { return parentReportId; }
    public void setParentReportId(String parentReportId) { this.parentReportId = parentReportId; }

    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int count) { this.duplicateCount = count; }

    public String getTypeEmoji() {
        if (reportType == null) return "📋";
        switch (reportType) {
            case "flood":   return "🌊";
            case "road":    return "🚧";
            case "damage":  return "🏚️";
            case "unsafe":  return "⚠️";
            case "missing": return "🔍";
            case "rescue":  return "🆘";
            case "medical": return "🏥";
            case "supply":  return "📦";
            default:        return "📋";
        }
    }

    public String getTypeLabel() {
        if (reportType == null) return "Report";
        switch (reportType) {
            case "flood":   return "Flood";
            case "road":    return "Blocked Road";
            case "damage":  return "Infrastructure Damage";
            case "unsafe":  return "Unsafe Area";
            case "missing": return "Missing Person";
            case "rescue":  return "Rescue Needed";
            case "medical": return "Medical Emergency";
            case "supply":  return "Supply Shortage";
            default:        return reportType;
        }
    }
}
