package com.example.alertuppp.model;

public class Alert {
    private String id;
    private String title;
    private String body;
    private String level; // "danger" | "warning" | "info"
    private String area;
    private boolean isActive;
    private String issuedAt;
    private String expiresAt;

    public Alert() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getIssuedAt() { return issuedAt; }
    public void setIssuedAt(String issuedAt) { this.issuedAt = issuedAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getLevelEmoji() {
        if (level == null) return "📢";
        switch (level) {
            case "danger":  return "⚠️";
            case "warning": return "🌊";
            default:        return "📢";
        }
    }

    public String getLevelLabel() {
        if (level == null) return "INFO";
        return level.toUpperCase();
    }
}
