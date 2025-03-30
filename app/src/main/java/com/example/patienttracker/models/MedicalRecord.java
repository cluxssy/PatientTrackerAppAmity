package com.example.patienttracker.models;

import com.google.firebase.database.Exclude;

import java.io.Serializable;

/**
 * Model class representing a medical record.
 * Stores information about a patient's medical condition, diagnosis, prescription, etc.
 */
public class MedicalRecord implements Serializable {
    private String id;
    private String patientId;
    private String doctorId;
    private String patientName;
    private String doctorName;
    private long date;
    private String diagnosis;
    private String prescription;
    private String notes;

    // Empty constructor required for Firebase
    public MedicalRecord() {
    }

    /**
     * Constructor with required fields
     */
    public MedicalRecord(String patientId, String doctorId, String patientName, String doctorName,
                         long date, String diagnosis, String prescription) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.date = date;
        this.diagnosis = diagnosis;
        this.prescription = prescription;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getPrescription() {
        return prescription;
    }

    public void setPrescription(String prescription) {
        this.prescription = prescription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}