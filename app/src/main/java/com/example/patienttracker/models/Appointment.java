package com.example.patienttracker.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

/**
 * Model class for an appointment between a patient and a doctor.
 */
public class Appointment {
    @DocumentId
    private String id;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private Timestamp dateTime;
    // Legacy fields for backward compatibility
    private Timestamp date;
    private String time;
    private String reason;
    private Timestamp createdAt;
    private String appointmentType;
    private String notes;
    private String status; // SCHEDULED, CANCELLED, COMPLETED

    // Empty constructor required for Firestore
    public Appointment() {
    }

    public Appointment(String patientId, String patientName, String doctorId, String doctorName,
                       Timestamp dateTime, String appointmentType, String notes) {
        this.patientId = patientId;
        this.patientName = patientName;
        this.doctorId = doctorId;
        this.doctorName = doctorName;
        this.dateTime = dateTime;
        this.date = dateTime; // Set legacy field
        this.appointmentType = appointmentType;
        this.notes = notes;
        this.reason = notes; // Map notes to reason for backward compatibility
        this.status = "SCHEDULED";
        this.createdAt = Timestamp.now();

        // Format time string
        if (dateTime != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
            this.time = sdf.format(dateTime.toDate());
        }
    }

    // Getters and setters
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

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public Timestamp getDateTime() {
        return dateTime;
    }

    public void setDateTime(Timestamp dateTime) {
        this.dateTime = dateTime;
        // Update legacy fields
        this.date = dateTime;
        if (dateTime != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
            this.time = sdf.format(dateTime.toDate());
        }
    }

    public Timestamp getDate() {
        // Use dateTime if date is null (for newer records)
        return date != null ? date : dateTime;
    }

    public void setDate(Timestamp date) {
        this.date = date;
        // If dateTime is null but date isn't, update dateTime for consistency
        if (dateTime == null && date != null) {
            this.dateTime = date;
        }
    }

    public String getTime() {
        // Return the stored time value if it exists
        if (time != null) {
            return time;
        }

        // Otherwise generate from dateTime if available
        if (dateTime != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
            return sdf.format(dateTime.toDate());
        }

        return null;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getReason() {
        // Use notes if reason is null (for newer records)
        return reason != null ? reason : notes;
    }

    public void setReason(String reason) {
        this.reason = reason;
        // If notes is null but reason isn't, update notes for consistency
        if (notes == null && reason != null) {
            this.notes = reason;
        }
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(String appointmentType) {
        this.appointmentType = appointmentType;
    }

    public String getNotes() {
        // Use reason if notes is null (for older records)
        return notes != null ? notes : reason;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        // If reason is null but notes isn't, update reason for consistency
        if (reason == null && notes != null) {
            this.reason = notes;
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Helper method to convert a legacy appointment with separate date and time
     * fields to the newer format with a single dateTime field.
     * This should be called after Firestore deserialization if needed.
     */
    @Exclude
    public void normalizeDateTime() {
        // If we have dateTime already, no need to normalize
        if (dateTime != null) {
            return;
        }

        // If we have a date field but no dateTime, use it
        if (date != null) {
            dateTime = date;
        }
    }
}