package com.example.patienttracker.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;

public class Notification {

    // Constants for recipient types
    public static final int TARGET_ALL_USERS = 0;
    public static final int TARGET_ALL_PATIENTS = 1;
    public static final int TARGET_ALL_DOCTORS = 2;
    public static final int TARGET_SPECIFIC_USERS = 3;

    @DocumentId
    private String id;
    private String title;
    private String message;
    private String senderUid;
    private String senderName;

    // Target can be a role (all users, all patients, all doctors) or specific users
    private int targetType;
    private List<String> recipientUids; // Used when targetType = TARGET_SPECIFIC_USERS

    @ServerTimestamp
    private Timestamp createdAt;

    private boolean isRead; // For recipient usage

    // Required empty constructor for Firestore
    public Notification() {
    }

    // Constructor for sending to a role group (all users, all patients, all doctors)
    public Notification(String title, String message, String senderUid, String senderName, int targetType) {
        this.title = title;
        this.message = message;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.targetType = targetType;
        this.isRead = false;
    }

    // Constructor for sending to specific users
    public Notification(String title, String message, String senderUid, String senderName, List<String> recipientUids) {
        this.title = title;
        this.message = message;
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.targetType = TARGET_SPECIFIC_USERS;
        this.recipientUids = recipientUids;
        this.isRead = false;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public void setSenderUid(String senderUid) {
        this.senderUid = senderUid;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public int getTargetType() {
        return targetType;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public List<String> getRecipientUids() {
        return recipientUids;
    }

    public void setRecipientUids(List<String> recipientUids) {
        this.recipientUids = recipientUids;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Date getCreatedAtDate() {
        return createdAt != null ? createdAt.toDate() : null;
    }

    // Returns creation timestamp as a long value for use in adapters
    public long getTimestamp() {
        return createdAt != null ? createdAt.toDate().getTime() : System.currentTimeMillis();
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}