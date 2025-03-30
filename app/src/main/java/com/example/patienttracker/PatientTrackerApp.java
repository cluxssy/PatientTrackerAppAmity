package com.example.patienttracker;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Main Application class for the Patient Tracker App.
 * This class initializes global application settings.
 */
public class PatientTrackerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Explicitly disable night mode/dark theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}