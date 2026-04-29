package com.example.alertuppp.model;

public class HouseholdMember {

    public enum Relation {
        HEAD, SPOUSE, CHILD, PARENT, SIBLING, RELATIVE, OTHER
    }

    private String id;
    private String fullName;
    private int age;
    private String sex; // "Male" / "Female"
    private Relation relation;
    private boolean isPwd;       // Person with Disability
    private boolean isSenior;    // 60+
    private boolean isPregnant;
    private String notes;

    public HouseholdMember() {}

    public HouseholdMember(String fullName, int age, String sex, Relation relation) {
        this.fullName = fullName;
        this.age = age;
        this.sex = sex;
        this.relation = relation;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public int getAge() { return age; }
    public void setAge(int age) {
        this.age = age;
        this.isSenior = age >= 60;
    }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public Relation getRelation() { return relation; }
    public void setRelation(Relation relation) { this.relation = relation; }

    public boolean isPwd() { return isPwd; }
    public void setPwd(boolean pwd) { isPwd = pwd; }

    public boolean isSenior() { return isSenior; }

    public boolean isPregnant() { return isPregnant; }
    public void setPregnant(boolean pregnant) { isPregnant = pregnant; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getRelationLabel() {
        if (relation == null) return "Member";
        switch (relation) {
            case HEAD:     return "Household Head";
            case SPOUSE:   return "Spouse";
            case CHILD:    return "Child";
            case PARENT:   return "Parent";
            case SIBLING:  return "Sibling";
            case RELATIVE: return "Relative";
            default:       return "Other";
        }
    }

    /** Returns a short tag string for special needs, e.g. "PWD · Senior" */
    public String getSpecialNeedsTags() {
        StringBuilder sb = new StringBuilder();
        if (isPwd)      sb.append("PWD");
        if (isSenior)   { if (sb.length() > 0) sb.append(" · "); sb.append("Senior"); }
        if (isPregnant) { if (sb.length() > 0) sb.append(" · "); sb.append("Pregnant"); }
        return sb.toString();
    }
}
