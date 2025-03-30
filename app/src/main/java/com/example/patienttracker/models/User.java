package com.example.patienttracker.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Map;

public class User implements Serializable {

    // Role constants
    public static final int ROLE_PATIENT = 0;
    public static final int ROLE_DOCTOR = 1;
    public static final int ROLE_ADMIN = 2;

    // Status constants
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_PENDING = 1;
    public static final int STATUS_REJECTED = 2;

    // Add serialVersionUID for serialization
    private static final long serialVersionUID = 1L;

    @DocumentId
    private String uid;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String dateOfBirth;
    private String gender;
    private String photoUrl;
    private int role;
    private int status;
    private String specialization; // For doctors
    private String department; // For doctors
    private int yearsOfExperience; // For doctors
    private Map<String, Object> medicalHistory; // For patients

    // Mark Timestamp fields as transient to exclude them from serialization
    @ServerTimestamp
    private transient Timestamp registeredAt;
    private transient Timestamp lastUpdated;

    // Add string representations of timestamps that can be serialized
    private String registeredAtString;
    private String lastUpdatedString;

    // Required empty constructor for Firestore
    public User() {
    }

    // Constructor for registration
    public User(String uid, String email, String fullName, int role) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = role == ROLE_DOCTOR ? STATUS_PENDING : STATUS_ACTIVE;
    }

    // Getters and setters
    public String getUid() {
        return uid;
    }

    // Alias for getUid() to maintain compatibility with code using getId()
    public String getId() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    // Alias for getFullName() to handle code that uses getName()
    public String getName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    // Alias for setFullName() to maintain compatibility with code using setName()
    public void setName(String name) {
        this.fullName = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(int yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public Map<String, Object> getMedicalHistory() {
        return medicalHistory;
    }

    public void setMedicalHistory(Map<String, Object> medicalHistory) {
        this.medicalHistory = medicalHistory;
    }

    public Timestamp getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Timestamp registeredAt) {
        this.registeredAt = registeredAt;
        if (registeredAt != null) {
            this.registeredAtString = registeredAt.toDate().toString();
        }
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
        if (lastUpdated != null) {
            this.lastUpdatedString = lastUpdated.toDate().toString();
        }
    }

    public String getRegisteredAtString() {
        return registeredAtString;
    }

    public void setRegisteredAtString(String registeredAtString) {
        this.registeredAtString = registeredAtString;
    }

    public String getLastUpdatedString() {
        return lastUpdatedString;
    }

    public void setLastUpdatedString(String lastUpdatedString) {
        this.lastUpdatedString = lastUpdatedString;
    }

    // Custom serialization to handle Timestamp fields
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        // Convert timestamps to strings before serialization
        if (registeredAt != null && registeredAtString == null) {
            registeredAtString = registeredAt.toDate().toString();
        }
        if (lastUpdated != null && lastUpdatedString == null) {
            lastUpdatedString = lastUpdated.toDate().toString();
        }

        // Use default serialization
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        // Use default deserialization
        in.defaultReadObject();

        // The transient Timestamp fields will remain null after deserialization
        // They will be populated from Firestore when needed
    }

    // Helper methods
    public boolean isPatient() {
        return role == ROLE_PATIENT;
    }

    public boolean isDoctor() {
        return role == ROLE_DOCTOR;
    }

    public boolean isAdmin() {
        return role == ROLE_ADMIN;
    }

    public boolean isActive() {
        return status == STATUS_ACTIVE;
    }

    public boolean isPending() {
        return status == STATUS_PENDING;
    }

    public boolean isRejected() {
        return status == STATUS_REJECTED;
    }

    public boolean isApproved() {
        return status == STATUS_ACTIVE;
    }

    public String getRoleString() {
        switch (role) {
            case ROLE_PATIENT:
                return "Patient";
            case ROLE_DOCTOR:
                return "Doctor";
            case ROLE_ADMIN:
                return "Admin";
            default:
                return "Unknown";
        }
    }

    public String getStatusString() {
        switch (status) {
            case STATUS_ACTIVE:
                return "Active";
            case STATUS_PENDING:
                return "Pending Approval";
            case STATUS_REJECTED:
                return "Rejected";
            default:
                return "Unknown";
        }
    }
}