package com.example.patienttracker.interfaces;

import com.example.patienttracker.models.User;

public interface UserActionListener {
    void onActivate(User user);
    void onDeactivate(User user);
}