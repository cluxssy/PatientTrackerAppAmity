package com.example.patienttracker.interfaces;

import com.example.patienttracker.models.User;

public interface ApprovalActionListener {
    void onApprove(User doctor);
    void onReject(User doctor);
}